package com.axel.onixlauncher

import android.app.ActivityManager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin


/** Versión para mostrar: "0.17.0 · build 17". */
internal fun appVersionLabel(): String = "${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_NUMBER}"

/**
 * Diagnóstico en vivo: solo lecturas (equipo, entorno, auto, medios) y la Prueba completa.
 * v0.81.11: es una categoría de Ajustes (antes una pantalla aparte). Los ajustes que estaban
 * mezclados acá (sonido de inicio, avisos, brillo, luces, modo liviano, "Usar como inicio") pasaron
 * a sus categorías; con ellos la fila de la Prueba completa quedaba cortada fuera del panel.
 */
@Composable
internal fun DiagnosticsPanel(
    now: Calendar,
    ambient: Ambient,
    lightsOn: Boolean,
    media: MediaResult,
    speed: Int?,
    weather: Weather?,
    lightsMode: LightsMode = LightsMode.RADIO,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { RadioCatalog.loadSaved(context) }
    // Registro de cambios de ajustes y propiedades de la radio, solo mientras Diagnóstico está abierto
    androidx.compose.runtime.DisposableEffect(Unit) {
        NwdSignals.startCapture(context)
        onDispose { NwdSignals.stopCapture(context) }
    }
    // Permiso para guardar en Descargas (solo Android 6 a 9); con o sin permiso, la prueba sigue
    var pendingScreen by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val storageAsk = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { FullReport.start(context, pendingScreen, media, weather) }

    // Fluidez: cuadros por segundo medidos mientras el diagnóstico está abierto
    var fps by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (!RenderFlags.animate) return@LaunchedEffect
        var frames = 0
        var start = 0L
        while (true) {
            withFrameNanos { t ->
                if (start == 0L) start = t
                frames++
                if (t - start >= 1_000_000_000L) {
                    fps = (frames * 1e9 / (t - start)).roundToInt()
                    frames = 0
                    start = t
                }
            }
        }
    }

    // Memoria y pantalla
    val rt = Runtime.getRuntime()
    val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    val maxMb = rt.maxMemory() / (1024 * 1024)
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val dm = context.resources.displayMetrics
    val version = appVersionLabel()

    val brightness = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Exception) {
        -1 // algunos equipos no lo exponen; se muestra "no disponible" sin registrarlo como error
    }
    // Lint (LocalContextConfigurationRead): en Compose la configuración se lee de LocalConfiguration
    val nightUi = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // v0.81.13: el launcher que protege el firmware de la radio (se lee una vez al abrir)
    val radioHome = remember { HomeDefault.radioLauncher() }
    val sistema = listOf(
        "Fluidez" to (fps?.let { "$it cuadros/s" } ?: "midiendo…"),
        "Memoria de la app" to "$usedMb de $maxMb MB",
        "RAM del equipo" to "${(mi.totalMem - mi.availMem) / (1024 * 1024)} de ${mi.totalMem / (1024 * 1024)} MB",
        "Pantalla" to "${dm.widthPixels}×${dm.heightPixels} px · ${dm.densityDpi} dpi",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        // Versión de gráficos: decide qué mapa 3D (MapLibre) puede usar la radio
        "OpenGL ES" to (am.deviceConfigurationInfo?.glEsVersion ?: "desconocida"),
        "Procesador" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "?") + " · " + Runtime.getRuntime().availableProcessors() + " núcleos",
        "Equipo" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        "Versión de la app" to version,
        // v0.81.11: quién atiende el botón Inicio de la radio (entra en la Prueba completa)
        "Pantalla de inicio" to HomeDefault.describe(HomeDefault.read(context), HomeDefault.resolvedPackage(context) ?: "?"),
        "Inicio según la radio" to HomeDefault.describeRadio(radioHome, context.packageName)
    )
    val entorno: List<Pair<String, String>> = listOf(
        "Hora" to String.format(
            Locale.US, "%02d:%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
        ),
        "Ambiente" to ambient.name.lowercase(),
        "Clima" to (weather?.let { "${it.tempC}° ${it.shortLabel}" } ?: "sin datos"),
        "Último error" to (AppLog.lastError ?: "ninguno")
    )
    val auto: List<Pair<String, String>> = listOf(
        "Luces" to (if (lightsOn) "encendidas" else "apagadas") + " · modo ${lightsMode.label}",
        "Señal de luces de la radio" to when {
            !RadioIll.present.value -> "no informa"
            RadioIll.on.value -> "prendidas"
            else -> "apagadas"
        },
        "Modo noche (cable de luces)" to if (nightUi) "sí" else "no",
        "Brillo del sistema" to if (brightness >= 0) brightness.toString() else "no disponible",
        "Aviso de luces" to if (!RadioIll.present.value) "sin señal de luces de la radio (no avisa)"
            else "activo (avisa andando, desde ${LightsReminder.MIN_KMH} km/h, de día y de noche)",
        "GPS" to (speed?.let { "$it km/h" } ?: "sin señal"),
        "OBD2" to (ObdState.data.value?.let { d ->
            "conectado · " + listOfNotNull(d.coolantC?.let { "$it °C" }, d.rpm?.let { "$it rpm" }, d.speedKmh?.let { "$it km/h" },
                d.fuelPct?.let { "nafta $it %" }, d.volts?.let { "$it V" },
                d.kmPerLiter?.let { String.format(java.util.Locale.US, "%.1f km/l", it) }).joinToString(" · ")
        } ?: ObdState.status.value) + (ObdState.device.value?.let { " ($it)" } ?: ""),
        "Códigos de falla" to (ObdState.codes.value?.let { c ->
            (if (c.mil == true) "luz del motor prendida · " else "") +
                ((c.stored + c.pending).distinct().joinToString().ifEmpty { "ninguno" })
        } ?: "sin leer") + (ObdState.alert.value?.let { " · ALERTA: ${it.title}" } ?: ""),
        // v0.72.0: en vivo, para saber qué manda cada botón de la radio o del volante
        "Última tecla" to NwdSignals.lastKey.value
    )
    val medios: List<Pair<String, String>> = listOf(
        "Música" to (media.info?.pkg?.ifEmpty { null } ?: if (media.hasAccess) "nada" else "sin acceso"),
        "Radio FM (antena)" to if (!NwdTuner.available.value) "sin sintonizador NWD (${NwdTuner.lastResult.value})" else
            (if (NwdTuner.antenna.value) "modo antena" else "modo internet") +
                (NwdTuner.station.value?.let { " · ${TunerMath.label(it.mhz)}" + (if (it.rds.isNotBlank()) " ${it.rds}" else "") } ?: "") +
                " · ${NwdTuner.lastResult.value}",
        "Radio (página)" to (RadioDirectory.lastScrape ?: "sin leer"),
        "Revisión de radios" to (RadioCatalog.progress.value?.let { "en curso ${it.first} de ${it.second}" }
            ?: if (RadioCatalog.checkedAt.value == 0L) "sin hacer"
            else "${RadioCatalog.working.value.size} funcionan de ${RadioCatalog.working.value.size + RadioCatalog.broken.value.size}"),
        // v0.76.0: canal de ntfy para mandar destinos desde el celular
        "Destinos del celular" to PhoneShare.status.value
    )
    // Lo que se guarda en la Prueba completa: todas las lecturas de la pantalla
    val lecturas = sistema + entorno + auto + medios

    // Prueba completa (v0.41.0): un solo botón revisa todo, guarda el .txt y lo copia. Va arriba de
    // las lecturas para que nunca quede fuera del panel.
    val running = FullReport.running.value
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Prueba completa", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    running -> FullReport.step.value ?: "Probando…"
                    FullReport.lastSaved.value != null -> "Guardado en ${FullReport.lastSaved.value} y copiado. " + (ReportUpload.status.value ?: "")
                    else -> "Revisa todas las funciones (hasta 2 min), guarda un .txt en Descargas y lo envía a la PC."
                },
                color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        PillButton(
            if (running) "Probando…" else "Iniciar",
            if (running) Track else Accent, if (running) TextMain else OnAccent, Modifier.width(180.dp)
        ) {
            if (running) return@PillButton
            val needsStorage = Build.VERSION.SDK_INT in 23..28 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsStorage) { pendingScreen = lecturas; storageAsk.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            else FullReport.start(context, lecturas, media, weather)
        }
    }
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiagColumn("Equipo y rendimiento", sistema, Modifier.fillMaxWidth())
            DiagColumn("Entorno", entorno, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiagColumn("Auto", auto, Modifier.fillMaxWidth())
            DiagColumn("Medios y red", medios, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun DiagColumn(title: String, rows: List<Pair<String, String>>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        for ((k, v) in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = Muted, fontSize = 14.sp)
                Text(
                    v, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Ajustes del sistema que pueden indicar las luces del auto (cable ILL), el brillo o el modo
 * noche: cada fabricante de radio usa otros nombres, así que se listan todos los que coincidan,
 * para comparar el informe con las luces prendidas y apagadas.
 */
internal fun lightSettings(context: Context): List<String> {
    val pattern = Regex("ill|light|night|dim|lamp|backlight|bright|lux|headl|luz|day|arm_|sound|audio|mute|mcu_current_source", RegexOption.IGNORE_CASE)
    val out = mutableListOf<String>()
    for ((table, uri) in listOf(
        "system" to android.provider.Settings.System.CONTENT_URI,
        "global" to android.provider.Settings.Global.CONTENT_URI,
        "secure" to android.provider.Settings.Secure.CONTENT_URI
    )) {
        try {
            context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    if (pattern.containsMatchIn(name)) out += "$table/$name = ${c.getString(1)}"
                }
            }
        } catch (e: Exception) {
            out += "$table: no se pudo leer (${e.javaClass.simpleName})"
        }
    }
    val cfg = context.resources.configuration
    out += "uiMode = ${cfg.uiMode} (noche=${(cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES})"
    return out
}
