package com.axel.onixlauncher

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.carlink.core.connection.toLinkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Prueba completa (v0.41.0): un solo botón en Diagnóstico revisa todas las funciones del launcher
 * (red y servicios de internet, GPS, clima, luces, OBD2, radio, música y Spotify, navegación y voz,
 * permisos, almacenamiento, interfaz) y arma un informe de texto que se guarda como .txt en
 * Descargas/OnixLauncher y se copia al portapapeles. Reemplaza a "Probar radio", "Probar OBD2" y
 * "Copiar informe".
 *
 * El informe no lleva tokens, claves, el Client ID de Spotify ni coordenadas: solo si existen.
 */

/** Resultado de una revisión: OK, FALLA o N/D (no se pudo probar, por ejemplo sin lector OBD2). */
enum class CheckResult(val label: String) { OK("OK"), FALLA("FALLA"), ND("N/D") }

/** Una línea del informe con su resultado. */
class Check(val area: String, val name: String, val result: CheckResult, val detail: String)

object FullReport {
    val running = mutableStateOf(false)
    val step = mutableStateOf<String?>(null)
    /** Dónde quedó el último informe guardado (para mostrarlo en pantalla). */
    val lastSaved = mutableStateOf<String?>(null)

    private val scope by lazy { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main) }

    /**
     * Arranca la prueba completa en segundo plano (sigue aunque se cierre Diagnóstico); al terminar
     * guarda el .txt, lo copia al portapapeles y avisa.
     */
    /**
     * AUD-103 (v0.79.5): marca la prueba como en curso antes de lanzarla; si ya corre, no arranca
     * otra (dos toques seguidos pasaban los dos: [run] la marcaba recién después del launch).
     */
    fun tryBegin(): Boolean {
        if (running.value) return false
        running.value = true
        return true
    }

    fun start(context: Context, screen: List<Pair<String, String>>, media: MediaResult, weather: Weather?) {
        if (!tryBegin()) return
        val app = context.applicationContext
        scope.launch {
            val text = try {
                run(app, screen, media, weather)
            } catch (e: Exception) {
                AppLog.warn("Prueba completa: se cortó", e)
                "=== Prueba completa · Onix Launcher ${appVersionLabel()} ===\nSe cortó: ${e.javaClass.simpleName} ${e.message ?: ""}\n\n" +
                    AppLog.recent().joinToString("\n")
            }
            val where = withContext(Dispatchers.IO) { save(app, text) }
            lastSaved.value = where
            // v0.81.23: además va a la PC del autor por el canal de informes (si no hay red, al volver)
            ReportUpload.send(app, fileName(Date()), text)
            try {
                val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Informe Onix Launcher", text))
            } catch (e: Exception) {
                AppLog.warn("Prueba completa: no se pudo copiar", e)
            }
            android.widget.Toast.makeText(
                app,
                if (where != null) "Informe guardado en $where y copiado" else "Informe copiado (no se pudo guardar el archivo)",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Resumen de una lista de revisiones: "12 OK · 1 FALLA · 2 N/D" (función pura, para los tests). */
    fun summary(checks: List<Check>): String {
        val ok = checks.count { it.result == CheckResult.OK }
        val bad = checks.count { it.result == CheckResult.FALLA }
        val nd = checks.count { it.result == CheckResult.ND }
        return "$ok OK · $bad FALLA · $nd N/D"
    }

    /** Resultado de un pedido HTTP: 2xx es OK; 401/403 también (el servicio responde, falta sesión). */
    fun httpResult(code: Int?, authOk: Boolean = false): CheckResult = when {
        code == null -> CheckResult.FALLA
        code in 200..299 -> CheckResult.OK
        authOk && (code == 401 || code == 403) -> CheckResult.OK
        else -> CheckResult.FALLA
    }

    /** Desde qué API existe un permiso nuevo; antes de esa versión no hace falta (función pura). */
    fun permissionSince(p: String): Int? = when (p) {
        "android.permission.FOREGROUND_SERVICE" -> 28
        "android.permission.BLUETOOTH_CONNECT" -> 31
        "android.permission.POST_NOTIFICATIONS" -> 33
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" -> 34
        else -> null
    }

    /** Si una línea de la prueba de radio muestra un enlace que entrega audio (HTTP 2xx con tipo de audio o lista HLS). */
    fun audioOk(line: String): Boolean =
        Regex("HTTP 2\\d\\d · (audio/|application/(ogg|vnd\\.apple\\.mpegurl|x-mpegurl))", RegexOption.IGNORE_CASE).containsMatchIn(line)

    /** Voces que usa la app y no están en el APK (v0.62.0; pura, para los tests). */
    fun missingClips(files: List<String>, keys: List<String>): List<String> {
        val have = files.filter { it.endsWith(".ogg") }.map { it.removeSuffix(".ogg") }.toSet()
        return keys.filter { it !in have }
    }

    /** Teclas distintas que llegaron a la app, en orden (v0.62.0; pura, para los tests). */
    fun keysSeen(lines: List<String>): List<String> =
        lines.mapNotNull { l ->
            when {
                "TECLA · " in l -> l.substringAfter("TECLA · ")
                "ACTION_KEY_VALUE" in l -> "aviso de la radio: " + l.substringAfter("ACTION_KEY_VALUE · ", l)
                else -> null
            }
        }.distinct()

    /** Nombre del archivo: onix_informe_2026-10-28_10-50.txt. */
    fun fileName(at: Date): String = "onix_informe_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(at) + ".txt"

    /**
     * Corre todas las revisiones y devuelve el informe. [screen] trae lo que ya muestra la pantalla
     * de Diagnóstico (equipo y sensores). Tarda hasta ~1,5 min (la prueba de la radio es la más larga).
     */
    suspend fun run(context: Context, screen: List<Pair<String, String>>, media: MediaResult, weather: Weather?): String {
        running.value = true
        NwdSignals.startCapture(context)
        val started = SystemClock.elapsedRealtime()
        val checks = mutableListOf<Check>()
        val sections = mutableListOf<Pair<String, List<String>>>()
        fun add(area: String, name: String, r: CheckResult, detail: String) {
            checks += Check(area, name, r, detail)
        }
        try {
            // 1. Red
            step.value = "Red…"
            val net = withContext(Dispatchers.IO) { networkInfo(context) }
            add("Red", "Conexión", if (net.first) CheckResult.OK else CheckResult.FALLA, net.second)
            add("Red", "Conexiones seguras (TLS)", CheckResult.OK, Tls.engine)

            // 2. Servicios de internet que usa el launcher
            val loc = lastLocation(context)
            val lat = loc?.latitude ?: DEFAULT_LAT
            val lon = loc?.longitude ?: DEFAULT_LON
            val services = listOf(
                Triple("Clima (Open-Meteo)", weatherUrl(DEFAULT_LAT, DEFAULT_LON), false),
                Triple("Radios (Radio Browser)", "https://de1.api.radio-browser.info/json/stats", false),
                Triple("Mapa (mosaicos OpenStreetMap)", "https://tile.openstreetmap.org/3/2/4.png", false),
                // v0.75.0: TomTom (la clave no aparece en el informe: solo el código HTTP)
                Triple("Búsqueda (TomTom)", NavApi.tomTomSearchUrl("9 de Julio 550, Tafí Viejo", DEFAULT_LAT, DEFAULT_LON), false),
                Triple("Rutas (TomTom)", NavApi.tomTomRouteUrl(DEFAULT_LAT, DEFAULT_LON, DEFAULT_LAT + 0.01, DEFAULT_LON + 0.01), false),
                Triple("Spotify (API)", "https://api.spotify.com/v1/me", true)
            )
            for ((name, url, authOk) in services) {
                step.value = "Internet: $name…"
                val t0 = SystemClock.elapsedRealtime()
                val res = withContext(Dispatchers.IO) { Net.request("GET", url, timeoutMs = 8000) }
                val ms = SystemClock.elapsedRealtime() - t0
                add("Internet", name, httpResult(res?.first, authOk), (res?.first?.let { "HTTP $it" } ?: "sin respuesta") + " · $ms ms")
            }

            // 2a. Rutas por TLS 1.3 directo (v0.71.0, HU-013): el respaldo que usa la navegación
            for ((name, url) in listOf(
                "Rutas (TomTom, TLS 1.3 directo)" to NavApi.tomTomRouteUrl(DEFAULT_LAT, DEFAULT_LON, DEFAULT_LAT + 0.01, DEFAULT_LON + 0.01)
            )) {
                step.value = "Internet: $name…"
                val t0 = SystemClock.elapsedRealtime()
                val res = withContext(Dispatchers.IO) { DirectHttps.get(url, 8000) }
                val ms = SystemClock.elapsedRealtime() - t0
                val detail = when {
                    Tls.factory == null -> "no hace falta (sin Conscrypt)"
                    res == null -> "sin respuesta"
                    else -> "HTTP ${res.first}"
                }
                add("Internet", name, if (Tls.factory == null) CheckResult.ND else httpResult(res?.first, false), "$detail · $ms ms")
            }

            // 2a'. Destinos desde el celular (v0.76.0): estado del canal de ntfy (sin mostrar su nombre)
            add("Internet", "Destinos del celular (ntfy)",
                when {
                    PhoneShare.status.value == "conectado" -> CheckResult.OK
                    PhoneShare.topic.isBlank() -> CheckResult.ND
                    else -> CheckResult.FALLA
                }, PhoneShare.status.value)

            // 2b. Conexión segura con los servidores de rutas, paso a paso (HU-013, v0.46.0)
            step.value = "Conexión segura de las rutas…"
            val tls = withContext(Dispatchers.IO) {
                listOf("api.tomtom.com").flatMap { tlsProbe(it) }
            }
            sections += "Conexión segura con los servidores de rutas (cada dirección IP)" to tls

            // 3. GPS
            step.value = "GPS…"
            val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsOn = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
            add("GPS", "Permiso de ubicación", if (hasLoc) CheckResult.OK else CheckResult.FALLA, if (hasLoc) "concedido" else "sin permiso")
            add("GPS", "GPS encendido", if (gpsOn) CheckResult.OK else CheckResult.FALLA, if (gpsOn) "sí" else "no")
            add(
                "GPS", "Última ubicación",
                if (loc != null) CheckResult.OK else CheckResult.ND,
                loc?.let { "hace ${(System.currentTimeMillis() - it.time) / 1000} s · precisión ${it.accuracy.toInt()} m · ${it.provider}" }
                    ?: "sin ubicación todavía (el clima y el mapa usan $DEFAULT_PLACE_NAME)"
            )
            // v0.79.7: cada cuánto llegan las posiciones y con cuánto retraso (para saber si la lentitud del mapa es de la radio)
            val fixes = GpsFix.report(SystemClock.elapsedRealtime())
            add(
                "GPS", "Posiciones en vivo (frecuencia)",
                when {
                    fixes == null -> CheckResult.ND
                    fixes.ageMs > GpsFix.FRESH_MS || (fixes.avgIntervalMs ?: 0L) > 2_500L -> CheckResult.FALLA
                    else -> CheckResult.OK
                },
                fixes?.text() ?: "no llegó ninguna desde que se abrió la app (sin señal, sin permiso o parado bajo techo)"
            )

            // 4. Clima
            add(
                "Clima", "Datos del clima",
                if (weather != null) CheckResult.OK else CheckResult.FALLA,
                weather?.let { "${it.tempC}° ${it.shortLabel} · lluvia=${it.raining} · ${it.days.size} días" } ?: "sin datos"
            )
            step.value = "Lugar…"
            val place = withContext(Dispatchers.IO) { try { placeName(context, loc) } catch (e: Exception) { null } }
            add("Clima", "Nombre del lugar (Geocoder)", if (place != null) CheckResult.OK else CheckResult.ND, place ?: "el equipo no lo da")

            // 5. Luces
            val lightsMode = LightsPrefs.load(context)
            val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            add("Luces", "Brillo de la pantalla", CheckResult.OK,
                "launcher pide " + (if (ScreenBrightness.forceMax.value) "brillo máximo" else "el brillo del sistema") +
                    " · sistema=" + (try { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { -1 }))
            add("Luces", "Modo", CheckResult.OK, "${lightsMode.label} · modo noche del sistema=${if (night) "sí" else "no"}")
            add(
                "Luces", "Señal de luces de la radio",
                if (RadioIll.present.value) CheckResult.OK else CheckResult.FALLA,
                if (!RadioIll.present.value) "la radio no la informa"
                else (if (RadioIll.on.value) "luces prendidas" else "luces apagadas") + " · por " + RadioIll.via.value
            )
            add(
                "Luces", "Avisos de la radio (NWD)",
                if (NwdSignals.recent().isNotEmpty()) CheckResult.OK else CheckResult.ND,
                "${NwdSignals.recent().size} desde que se abrió la app"
            )

            // 6. OBD2: si está conectado, se le piden las respuestas crudas
            step.value = "OBD2…"
            if (ObdState.status.value == "conectado") {
                ObdState.probe.value = emptyList()
                ObdState.probeRequested = true
                var waited = 0
                while (ObdState.probe.value.isEmpty() && waited < 20_000) { delay(250); waited += 250 }
            }
            val d = ObdState.data.value
            val ecu = d != null && (d.coolantC != null || d.rpm != null || d.speedKmh != null || d.fuelPct != null)
            // v0.81.24: quién está emparejado con la radio (el lector atiende a un solo equipo a la vez)
            val paired = withContext(Dispatchers.IO) { BtDevices.read(context) }
            add(
                "OBD2", "Bluetooth emparejado con la radio",
                if (paired == null) CheckResult.ND else CheckResult.OK,
                paired?.let { BtDevices.summary(it) } ?: "sin Bluetooth o apagado"
            )
            add(
                "OBD2", "Conexión con el lector",
                when {
                    ObdState.status.value == "conectado" -> CheckResult.OK
                    ObdState.device.value == null -> CheckResult.ND
                    else -> CheckResult.FALLA
                },
                ObdState.status.value + (ObdState.device.value?.let { " · $it" } ?: "")
            )
            add(
                "OBD2", "Datos del auto",
                if (ecu) CheckResult.OK else if (d != null) CheckResult.FALLA else CheckResult.ND,
                d?.let {
                    listOf("motor ${it.coolantC ?: "--"} °C", "${it.rpm ?: "--"} rpm", "${it.speedKmh ?: "--"} km/h",
                        "nafta ${it.fuelPct ?: "--"} %", "${it.volts ?: "--"} V").joinToString(" · ")
                } ?: "sin datos"
            )
            val codes = ObdState.codes.value
            add(
                "OBD2", "Códigos de falla",
                when {
                    codes == null -> CheckResult.ND
                    codes.total == 0 && codes.mil != true -> CheckResult.OK
                    else -> CheckResult.FALLA
                },
                codes?.let { c ->
                    "luz del motor " + (when (c.mil) { true -> "prendida"; false -> "apagada"; null -> "sin dato" }) +
                        " · guardados: " + c.stored.joinToString().ifEmpty { "ninguno" } +
                        " · pendientes: " + c.pending.joinToString().ifEmpty { "ninguno" }
                } ?: "sin leer"
            )
            add(
                "OBD2", "Consumo estimado",
                if (d?.kmPerLiter != null) CheckResult.OK else CheckResult.ND,
                d?.kmPerLiter?.let { String.format(java.util.Locale.US, "%.1f km/l", it) } ?: "sin dato (auto parado o sin MAP/MAF)"
            )
            // v0.78.1: kilometraje total por OBD2 (PID 01A6), si el auto lo informa
            add(
                "OBD2", "Kilometraje del auto (01A6)",
                if (ObdState.odometerKm.value != null) CheckResult.OK else CheckResult.ND,
                ObdState.odometerKm.value?.let { "$it km (Mi auto se ajusta solo)" }
                    ?: if (d == null) "sin conexión con el lector" else "el auto no lo informa: se carga a mano una vez en Mi auto"
            )
            add(
                "OBD2", "Alertas del motor",
                if (ObdState.alert.value == null) CheckResult.OK else CheckResult.FALLA,
                ObdState.alert.value?.let { "${it.title}: ${it.advice}" } ?: "ninguna"
            )
            // AUD-065 (v0.67.1): la salud por sistema (v0.64.0) también va en el informe
            val health = CarHealth.of(codes, ObdState.alert.value)
            add(
                "OBD2", "Salud por sistema",
                when {
                    health.any { it.second == HealthLevel.FAULT } -> CheckResult.FALLA
                    health.all { it.second == HealthLevel.UNKNOWN } -> CheckResult.ND
                    else -> CheckResult.OK
                },
                CarHealth.summary(health)
            )
            sections += "Respuestas crudas del OBD2" to ObdState.probe.value

            // 6b. Radio FM por la antena (v0.51.0)
            val tuned = if (NwdTuner.available.value) NwdTuner.refresh() else null
            add(
                "Radio", "Sintonizador FM de la radio (antena)",
                when {
                    !NwdTuner.available.value -> CheckResult.ND
                    tuned != null -> CheckResult.OK
                    else -> CheckResult.FALLA
                },
                if (!NwdTuner.available.value) "sin servicio com.nwd.radio.service (${NwdTuner.lastResult.value})"
                else (tuned?.let { "banda ${it.band} · ${TunerMath.label(it.mhz)} MHz · RDS \"${it.rds}\"" } ?: "no contesta") +
                    " · modo " + (if (NwdTuner.antenna.value) "antena" else "internet") + " · " + NwdTuner.lastResult.value
            )

            // 7. Radio: estado, catálogo y prueba de la emisora actual (o La 100)
            step.value = "Radio: probando la señal…"
            val target = RadioState.station.value ?: TucumanFm.stations.first { it.name == "La 100" }
            RadioProbe.start(target)
            var w = 0
            while (RadioProbe.running.value && w < 90_000) { delay(500); w += 500 }
            val probe = RadioProbe.lines.value
            val probeOk = probe.any { audioOk(it) }
            add("Radio", "Prueba de ${target.name}", if (probeOk) CheckResult.OK else CheckResult.FALLA, "${probe.size} líneas (detalle abajo)")
            add(
                "Radio", "Estado",
                if (RadioState.playing.value) CheckResult.OK else CheckResult.ND,
                "${RadioState.current.value ?: "apagada"} · sonando=${RadioState.playing.value} · conectando=${RadioState.buffering.value} · " +
                    "pausa=${RadioState.pauseReason.value ?: "-"} · modo=${if (RadioState.national.value) "NACIONAL" else "LOCAL"}"
            )
            add(
                "Radio", "Catálogo",
                if (RadioCatalog.working.value.isNotEmpty()) CheckResult.OK else CheckResult.ND,
                "${RadioCatalog.stations.value.size} radios · ${RadioCatalog.working.value.size} funcionan · ${RadioCatalog.broken.value.size} sin señal"
            )
            sections += "Prueba de radio" to probe
            sections += "Revisión de radios" to RadioCatalog.checkLog.value

            // 8. Música y Spotify
            step.value = "Música y Spotify…"
            add("Música", "Acceso a las sesiones de música", if (media.hasAccess) CheckResult.OK else CheckResult.FALLA,
                if (media.hasAccess) "concedido" else "falta \"Dar acceso\"")
            add("Música", "Sonando ahora", CheckResult.OK, media.info?.let { "${it.pkg} · sonando=${it.playing}" } ?: "nada")
            val spotifyInstalled = try { context.packageManager.getPackageInfo(SPOTIFY, 0); true } catch (e: Exception) { false }
            add("Spotify", "App instalada", if (spotifyInstalled) CheckResult.OK else CheckResult.FALLA, if (spotifyInstalled) "sí" else "no")
            val hasClient = SpotifyAuth.clientId(context) != null
            val connected = SpotifyAuth.isConnected(context)
            add("Spotify", "Cuenta conectada", if (connected) CheckResult.OK else CheckResult.FALLA,
                "Client ID cargado=${if (hasClient) (if (SpotifyAuth.builtInClientId() != null) "sí (viene en la app)" else "sí (pegado a mano)") else "no"} · sesión=${if (connected) "sí" else "no"}")
            if (connected) {
                val lists = withContext(Dispatchers.IO) { try { SpotifyApi.playlists(context) } catch (e: Exception) { emptyList() } }
                add("Spotify", "Leer tus listas", if (lists.isNotEmpty()) CheckResult.OK else CheckResult.FALLA, "${lists.size} listas")
            }
            val audio = context.getSharedPreferences("audio", Context.MODE_PRIVATE)
            add("Música", "Retomar al encender", CheckResult.OK,
                "launcher como inicio=${LastAudio.isHomeLauncher(context)} · última fuente=${audio.getString("fuente", null) ?: "ninguna (pausada o nada)"}")

            // 9. Navegación
            step.value = "Navegación y voz…"
            Voice.init(context)
            var v = 0
            while (Voice.state.value == "iniciando" && v < 5000) { delay(250); v += 250 }
            add(
                "Navegación", "Voz (TextToSpeech)",
                if (Voice.state.value.startsWith("lista")) CheckResult.OK else CheckResult.FALLA,
                Voice.state.value + if (Voice.state.value == "sin voz en el equipo") " · instalar \"Servicios de voz de Google\" (Play Store) con español" else ""
            )
            // v0.61.0: frases grabadas para cuando no hay TextToSpeech
            val clipFiles = try { context.assets.list("voz")?.toList().orEmpty() } catch (e: Exception) { emptyList() }
            val missing = missingClips(clipFiles, VoiceClips.ALL)
            add("Navegación", "Voces grabadas", if (missing.isEmpty()) CheckResult.OK else CheckResult.FALLA,
                if (missing.isEmpty()) "${VoiceClips.ALL.size} frases (se usan si no hay TextToSpeech)" else "faltan: " + missing.joinToString())
            SavedPlaces.load(context)
            add("Navegación", "Lugares guardados", CheckResult.OK,
                "casa=${if (SavedPlaces.home.value != null) "sí" else "no"} · trabajo=${if (SavedPlaces.work.value != null) "sí" else "no"} · " +
                    "favoritos=${SavedPlaces.favorites.value.size} · recientes=${SavedPlaces.recent.value.size}")
            val tiles = withContext(Dispatchers.IO) { dirSize(File(context.filesDir, "osmdroid/tiles")) }
            add("Navegación", "Mapa sin conexión (caché)", if (tiles > 0) CheckResult.OK else CheckResult.ND, "${tiles / (1024 * 1024)} MB guardados")

            // 9b. Android Auto / CarPlay (v0.62.0)
            step.value = "Android Auto y CarPlay…"
            val link = try {
                com.carlink.core.connection.Connection.get(context).state.value.toLinkState()
            } catch (e: Exception) { null }
            add("Auto/CarPlay", "Motor", if (link == null) CheckResult.FALLA else if (link.stage == com.carlink.core.core.LinkStage.FAILED) CheckResult.FALLA else CheckResult.OK,
                link?.let { EngineTrace.session(it) } ?: "el motor no respondió")
            val needed = try { com.carlink.core.connection.DevicePreflight.requiredPermissions() } catch (e: Exception) { emptyList() }
            val missingPerms = needed.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            add("Auto/CarPlay", "Permisos del motor", if (missingPerms.isEmpty()) CheckResult.OK else CheckResult.FALLA,
                if (missingPerms.isEmpty()) "todos concedidos" else "faltan: " + missingPerms.joinToString { it.substringAfterLast('.') })
            val vpnOk = withContext(Dispatchers.Main) { runCatching { android.net.VpnService.prepare(context) == null }.getOrNull() }
            add("Auto/CarPlay", "Autorización de VPN (CarPlay por cable)", when (vpnOk) { true -> CheckResult.OK; false -> CheckResult.ND; null -> CheckResult.FALLA },
                when (vpnOk) { true -> "autorizada"; false -> "todavía no (se pide al enchufar un iPhone)"; null -> "no se pudo revisar" })
            val other = Projection.find(Projection.installed(context))
            add("Auto/CarPlay", "Otra app de proyección", if (other == null) CheckResult.OK else CheckResult.FALLA, other ?: "ninguna")
            // v0.79.1: qué ve la radio en el USB, los pasos del motor y el registro de CarLink
            val usb = try {
                (context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager).deviceList.values.map {
                    EngineTrace.usbLine(it.vendorId, it.productId, it.productName, it.configurationCount)
                }
            } catch (e: Exception) { listOf("no se pudo leer (${e.javaClass.simpleName})") }
            add("Auto/CarPlay", "Equipos USB", if (usb.isNotEmpty()) CheckResult.OK else CheckResult.ND,
                if (usb.isEmpty()) "ninguno enchufado" else "${usb.size} (detalle abajo)")
            val carlinkLog = try {
                val f = SimpleDateFormat("HH:mm:ss", Locale.US)
                com.carlink.core.diagnostics.EventLog.entries.value.takeLast(30).map { "${f.format(Date(it.timeMillis))} ${it.tag}: ${it.message}" }
            } catch (e: Exception) { emptyList() }
            sections += "Motor de Android Auto y CarPlay (últimos pasos)" to
                (listOf("Equipos USB:") + usb.ifEmpty { listOf("ninguno") }.map { "    $it" } +
                    listOf("Pasos del motor:") + EngineTrace.recent().ifEmpty { listOf("ninguno desde que se abrió la app") }.map { "    $it" } +
                    listOf("Registro de CarLink:") + carlinkLog.ifEmpty { listOf("vacío") }.map { "    $it" })
            // El registro del motor de CarPlay (DiPlay): cada paso con su motivo, como el de la app DiPlay
            val diplayLog = withContext(Dispatchers.IO) {
                fun read(name: String) = runCatching { File(File(context.filesDir, "logs"), name).readLines().takeLast(120) }.getOrDefault(emptyList())
                EngineTrace.logTail(read("diplay.log"), read("previous.log"))
            }
            sections += "Registro del motor de CarPlay (diplay.log)" to diplayLog.ifEmpty { listOf("vacío: el motor de CarPlay no arrancó desde que se instaló") }

            // 9c. Memoria por proceso: la app y el motor de Android Auto (:aa), AUD-041
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val procs = try { am.runningAppProcesses.orEmpty().filter { it.processName.startsWith(context.packageName) } } catch (e: Exception) { emptyList() }
            val pss = try { am.getProcessMemoryInfo(procs.map { it.pid }.toIntArray()).map { it.totalPss / 1024 } } catch (e: Exception) { emptyList() }
            val memInfo = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            add("Equipo", "Memoria por proceso", if (procs.isNotEmpty()) CheckResult.OK else CheckResult.ND,
                procs.mapIndexed { i, p -> "${p.processName.removePrefix(context.packageName).ifEmpty { "principal" }}=${pss.getOrNull(i) ?: "?"} MB" }.joinToString(" · ") +
                    " · RAM libre ${memInfo.availMem / (1024 * 1024)} MB" + if (memInfo.lowMemory) " (poca memoria)" else "")
            add("Equipo", "Modo liviano", CheckResult.OK, LiteMode.label())

            // 9d. Mi auto
            CarCare.load(context)
            add("Mi auto", "Kilometraje", if (CarCare.carKm() != null) CheckResult.OK else CheckResult.ND,
                CarCare.carKm()?.let { "${Reminders.km(it)} km" } ?: "sin cargar (se ajusta en Mi auto)")
            val maintSet = CarCare.maintenance.value.count { Maintenance.urgency(it, CarCare.carKm(), Reminders.today()) != Urgency.UNSET }
            add("Mi auto", "Viajes y mantenimiento", CheckResult.OK,
                "${CarCare.trips.value.size} viajes · mantenimiento $maintSet de ${CarCare.maintenance.value.size} completos · " +
                    SpeedWarning.also { it.load(context) }.label(SpeedWarning.limit.value).lowercase() + " · " +
                    "viaje en curso ${String.format(Locale.US, "%.1f", CarCare.liveKm.value)} km")

            // 9e. Botones del volante (R-45): teclas que llegaron desde que se abrió la app
            val keys = keysSeen(NwdSignals.keys())
            add("Volante", "Teclas recibidas", if (keys.isNotEmpty()) CheckResult.OK else CheckResult.ND,
                if (keys.isEmpty()) "ninguna (apretá cada botón con la app abierta antes de la prueba)" else "${keys.size} distintas (detalle abajo)")
            sections += "Teclas del volante" to keys

            // 10. Permisos
            step.value = "Permisos…"
            val perms = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
            } catch (e: Exception) { emptyList() }
            sections += "Permisos" to perms.map { p ->
                val granted = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
                val since = permissionSince(p)
                "${p.removePrefix("android.permission.")}: " + when {
                    granted -> "concedido"
                    since != null && Build.VERSION.SDK_INT < since -> "no aplica (existe desde Android API $since)"
                    else -> "no concedido"
                }
            }

            // 11. Almacenamiento e interfaz
            val free = try { StatFs(context.filesDir.path).availableBytes / (1024 * 1024) } catch (e: Exception) { -1L }
            add("Equipo", "Espacio libre", if (free > 200) CheckResult.OK else CheckResult.FALLA, "$free MB")
            add("Interfaz", "Dock", CheckResult.OK, DockStore.slots.value.joinToString(", ") {
                when (it) {
                    is DockSlot.Own -> it.b.name
                    is DockSlot.App -> it.label
                    DockSlot.Empty -> "vacío"
                }
            })
            add("Interfaz", "Saludo", CheckResult.OK, "nombre=${GreetingName.load(context)}")
            // v0.65.0: sonido de la presentación y tema festivo de hoy
            IntroSound.load(context); Festive.load(context)
            add("Interfaz", "Presentación y fiestas", CheckResult.OK,
                IntroSound.label().lowercase() + " · " + Festive.label().lowercase() + " · hoy: " +
                    (Festive.current(java.util.Calendar.getInstance())?.label ?: "ninguna"))
        } finally {
            step.value = null
            running.value = false
            NwdSignals.stopCapture(context)
        }

        val props = withContext(Dispatchers.IO) { NwdSignals.readProps() }
        val secs = (SystemClock.elapsedRealtime() - started) / 1000
        return buildString {
            appendLine("=== Prueba completa · Onix Launcher ${appVersionLabel()} ===")
            appendLine("Fecha: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date()) + " · duró $secs s")
            appendLine("Resumen: ${summary(checks)}")
            appendLine()
            appendLine("--- Equipo y sensores (pantalla de Diagnóstico) ---")
            for ((k, value) in screen) appendLine("$k: $value")
            for (area in checks.map { it.area }.distinct()) {
                appendLine()
                appendLine("--- $area ---")
                for (c in checks.filter { it.area == area }) appendLine("[${c.result.label}] ${c.name}: ${c.detail}")
            }
            for ((title, lines) in sections) {
                appendLine()
                appendLine("--- $title ---")
                if (lines.isEmpty()) appendLine("(nada)") else lines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("--- Ajustes de luces y brillo del sistema ---")
            lightSettings(context).forEach { appendLine(it) }
            appendLine()
            appendLine("--- Propiedades de la radio (luces, modo noche, CAN) ---")
            props.ifEmpty { mapOf("(ninguna)" to "") }.toSortedMap().forEach { (k, v) -> appendLine("$k = $v") }
            appendLine()
            appendLine("--- Avisos de la radio y cambios de ajustes o propiedades ---")
            NwdSignals.recent().ifEmpty { listOf("ninguno desde que se abrió la app") }.forEach { appendLine(it) }
            appendLine()
            appendLine("--- Registro del encendido (guardado aparte, no se pierde con los errores de red) ---")
            AppLog.recentKey().ifEmpty { listOf("ninguno desde que se abrió la app") }.forEach { appendLine(it) }
            appendLine()
            appendLine("--- Últimos eventos ---")
            AppLog.recent().forEach { appendLine(it) }
        }
    }

    /**
     * Prueba la conexión segura con [host] en cada una de sus direcciones IP (hasta 3), con el TLS
     * que usa la app (Conscrypt en Android 7) y con el del sistema, para ver dónde se corta.
     */
    private fun tlsProbe(host: String): List<String> {
        val out = mutableListOf<String>()
        val addrs = try { java.net.InetAddress.getAllByName(host).toList() } catch (e: Exception) {
            return listOf("$host: no se resolvió el nombre (${e.javaClass.simpleName})")
        }
        out += "$host → " + addrs.joinToString(", ") { it.hostAddress ?: "?" }
        val engines = listOf(
            "app" to javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory(),
            "sistema" to try { javax.net.ssl.SSLContext.getDefault().socketFactory } catch (e: Exception) { null }
        )
        for (addr in addrs.take(3)) {
            val kind = if (addr is java.net.Inet6Address) "IPv6" else "IPv4"
            for ((name, factory) in engines) {
                if (factory == null) continue
                val t0 = SystemClock.elapsedRealtime()
                val res = try {
                    val plain = java.net.Socket()
                    plain.connect(java.net.InetSocketAddress(addr, 443), 6000)
                    plain.soTimeout = 6000
                    val ssl = factory.createSocket(plain, host, 443, true) as javax.net.ssl.SSLSocket
                    ssl.startHandshake()
                    val s = ssl.session
                    val r = "OK ${s.protocol} ${s.cipherSuite}"
                    ssl.close()
                    r
                } catch (e: Exception) {
                    "FALLA ${e.javaClass.simpleName}: ${e.message?.replace("\n", " ")?.take(120) ?: ""}"
                }
                out += "    $kind ${addr.hostAddress} · TLS $name: $res · ${SystemClock.elapsedRealtime() - t0} ms"
            }
        }
        return out
    }

    private fun networkInfo(context: Context): Pair<Boolean, String> = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        if (info != null && info.isConnected) true to "${info.typeName} conectado" else false to "sin conexión"
    } catch (e: Exception) {
        false to "no se pudo leer (${e.javaClass.simpleName})"
    }

    private fun dirSize(dir: File): Long = try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (e: Exception) {
        0L
    }

    /**
     * Guarda el informe como .txt. Android 10 o más: Descargas/OnixLauncher (MediaStore, sin permiso).
     * Android 7 a 9: Descargas/OnixLauncher si hay permiso de almacenamiento; si no, la carpeta de la
     * app (Android/data/com.axel.onixlauncher/files/informes). Devuelve dónde quedó, o null.
     */
    fun save(context: Context, text: String): String? {
        val name = fileName(Date())
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OnixLauncher")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Descargas/OnixLauncher/$name"
            } else {
                val canPublic = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
                @Suppress("DEPRECATION")
                val dir = if (canPublic) File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OnixLauncher")
                else File(context.getExternalFilesDir(null) ?: context.filesDir, "informes")
                dir.mkdirs()
                val f = File(dir, name)
                f.writeText(text)
                if (canPublic) "Descargas/OnixLauncher/$name" else f.absolutePath
            }
        } catch (e: Exception) {
            AppLog.warn("Prueba completa: no se pudo guardar el archivo", e)
            null
        }
    }
}

/**
 * Envío del informe a la PC del autor (v0.81.23). Usa un canal de ntfy aparte del de destinos
 * (el nombre del canal más `-informes`), así la radio no recibe su propio informe. En la PC lo baja
 * `tools/bajar_informe.ps1`. El archivo dura unas horas en el servidor. Si no hay internet queda
 * guardado y se envía cuando vuelve la red ([retry], desde `NetWatch.announce`).
 */
object ReportUpload {
    private const val SERVER = "https://ntfy.sh"
    private const val SUFFIX = "-informes"
    private const val PENDING = "informe_pendiente.txt"

    /** Estado del último envío, para mostrarlo junto a la Prueba completa. */
    val status = mutableStateOf<String?>(null)

    /** Dirección del canal de informes, o null si la app no tiene canal. Pura. */
    fun url(topic: String): String? =
        topic.trim().takeIf { it.isNotEmpty() }?.let { "$SERVER/" + java.net.URLEncoder.encode(it + SUFFIX, "UTF-8") }

    /** Texto del estado según el código HTTP del envío (null = sin respuesta). Pura. */
    fun statusText(code: Int?, hasTopic: Boolean): String = when {
        !hasTopic -> "Sin canal para enviarlo: mandalo en el chat."
        code == null -> "Sin internet: se envía a la PC cuando vuelva la red."
        code in 200..299 -> "Enviado a la PC."
        else -> "No se pudo enviar a la PC (HTTP $code)."
    }

    private fun prefs(c: Context) = c.getSharedPreferences("informes", Context.MODE_PRIVATE)

    fun send(app: Context, name: String, text: String) {
        if (url(PhoneShare.topic) == null) { status.value = statusText(null, hasTopic = false); return }
        status.value = "Enviando a la PC…"
        kotlin.concurrent.thread(isDaemon = true, name = "onix-informe") {
            try {
                // Se guarda antes de enviar: si falla o la app se cierra, sale cuando vuelve la red
                java.io.File(app.filesDir, PENDING).writeText(text)
                prefs(app).edit().putString("nombre", name).apply()
            } catch (e: Exception) {
                AppLog.warn("Informe: no se pudo guardar para enviar", e)
            }
            upload(app, name, text)
        }
    }

    /** La red volvió: si quedó un informe sin enviar, sale ahora. */
    fun retry(app: Context) {
        val file = java.io.File(app.filesDir, PENDING)
        if (!file.exists()) return
        val name = prefs(app).getString("nombre", null) ?: return
        kotlin.concurrent.thread(isDaemon = true, name = "onix-informe") {
            val text = try { file.readText() } catch (e: Exception) { return@thread }
            upload(app, name, text)
        }
    }

    private fun upload(app: Context, name: String, text: String) {
        val target = url(PhoneShare.topic) ?: return
        val res = Net.request(
            "PUT", target, text, contentType = "text/plain; charset=utf-8",
            headers = mapOf("Filename" to name, "Title" to "Informe Onix"), timeoutMs = 20_000
        )
        val code = res?.first
        if (code != null && code in 200..299) {
            java.io.File(app.filesDir, PENDING).delete()
            AppLog.info("Informe: enviado a la PC ($name)")
        } else {
            AppLog.warn("Informe: no se pudo enviar a la PC (" + (code?.let { "HTTP $it" } ?: "sin respuesta") + ")")
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post { status.value = statusText(code, hasTopic = true) }
    }
}

/**
 * Dispositivos Bluetooth emparejados con la radio, para el informe (v0.81.24). Sirve para ver si el
 * lector OBD2 compite con otro equipo. La dirección se acorta: solo se muestran los últimos 5 caracteres.
 */
object BtDevices {
    /** Última parte de la dirección, para distinguir equipos sin publicarla entera. Pura. */
    fun mask(address: String?): String = "…" + (address?.takeLast(5) ?: "?")

    /** Tipo según la clase mayor de Bluetooth (0x0200 teléfono, 0x0400 audio, 0x0100 computadora). Pura. */
    fun kind(major: Int?): String = when (major) {
        0x0200 -> "teléfono"
        0x0400 -> "audio"
        0x0100 -> "computadora"
        else -> "otro"
    }

    /** Una línea por equipo: nombre, tipo, si está conectado y el final de su dirección. Pura. */
    fun describe(name: String?, address: String?, connected: Boolean?, major: Int?): String {
        val state = when (connected) { true -> "conectado"; false -> "sin conexión"; null -> "estado desconocido" }
        return (name?.trim()?.takeIf { it.isNotEmpty() } ?: "sin nombre") + " (" + kind(major) + ", " + state + ", " + mask(address) + ")"
    }

    /** "ninguno" o "N: equipo; equipo". Pura. */
    fun summary(lines: List<String>): String = if (lines.isEmpty()) "ninguno" else lines.size.toString() + ": " + lines.joinToString("; ")

    /** Los equipos emparejados, o null si el equipo no tiene Bluetooth o está apagado. Bloquea poco. */
    @android.annotation.SuppressLint("MissingPermission")
    fun read(context: Context): List<String>? = try {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) null
        else adapter.bondedDevices.orEmpty().map { d ->
            // isConnected() existe en Android pero no es pública: si no está, el estado queda "desconocido"
            val connected = runCatching {
                android.bluetooth.BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as? Boolean
            }.getOrNull()
            describe(d.name, d.address, connected, d.bluetoothClass?.majorDeviceClass)
        }
    } catch (e: Exception) {
        AppLog.warn("Informe: no se pudo leer el Bluetooth emparejado", e)
        null
    }
}
