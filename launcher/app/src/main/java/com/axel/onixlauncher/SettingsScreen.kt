package com.axel.onixlauncher

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Ajustes (v0.80.0): pantalla propia del launcher, por categorías, con selección de tema
 * (oscuro, claro o automático por faros/hora), color de acento, sonido/avisos y registro de fallos.
 * Idea inspirada en StradaOS (GPL-3.0); implementación propia en Compose.
 */

/** Categorías del menú de Ajustes. */
internal enum class SettingsCategory(val label: String, val hint: String) {
    SCREEN("Apariencia", "Tema, color y brillo"),
    SOUND("Sonido y avisos", "Sonido de inicio y alertas"),
    CAR("Auto y conexiones", "Lector OBD2, luces y celular"),
    SYSTEM("Sistema", "Inicio, actualizaciones y fallos"),
    // v0.81.11: Diagnóstico es una categoría más (antes, una pantalla aparte)
    DIAG("Diagnóstico", "Lecturas en vivo y Prueba completa")
}

/**
 * Quién atiende el botón Inicio de la radio (v0.81.11). "Usar como inicio" prendía el alias y abría
 * los ajustes de inicio de Android, que algunas radios no traen: no pasaba nada y no había forma de
 * saber si Onix había quedado elegido. Ahora se consulta a Android y se muestra el resultado.
 */
internal object HomeDefault {
    enum class State { NOT_OFFERED, ONIX, NOT_CHOSEN, OTHER }

    /** [resolved]: paquete que Android abre con Inicio; "android" es su ventana para elegir. Pura. */
    fun state(resolved: String?, own: String, aliasEnabled: Boolean): State = when {
        !aliasEnabled -> State.NOT_OFFERED
        resolved == own -> State.ONIX
        resolved == null || resolved == "android" -> State.NOT_CHOSEN
        else -> State.OTHER
    }

    fun describe(state: State, resolved: String): String = when (state) {
        State.NOT_OFFERED -> "Onix no está ofrecido como pantalla de inicio."
        State.ONIX -> "Onix es la pantalla de inicio."
        State.NOT_CHOSEN -> "Falta elegir: tocá Elegir, después Onix Launcher y \"Siempre\"."
        State.OTHER -> "La radio abre $resolved con el botón Inicio."
    }

    /**
     * v0.81.13: en las radios NWD el firmware protege al launcher que figura en esta propiedad y puede
     * volver a poner el suyo aunque Android tenga otro elegido. Solo se lee: cambiarla pide ADB.
     */
    const val RADIO_PROP = "persist.nwd.launcher.default"

    /** Lo que dice la radio sobre su launcher protegido ([value]: la propiedad [RADIO_PROP]). Pura. */
    fun describeRadio(value: String?, own: String): String = when {
        value.isNullOrBlank() -> "La radio no informa un launcher protegido."
        value.contains(own) -> "La radio protege a Onix."
        else -> "La radio protege a $value."
    }

    fun radioLauncher(): String? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop", RADIO_PROP))
        proc.inputStream.bufferedReader().use { it.readText() }.trim().also { proc.waitFor() }
    } catch (e: Exception) {
        null
    }

    private fun alias(c: Context) = android.content.ComponentName(c, "com.axel.onixlauncher.HomeAlias")

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun aliasEnabled(c: Context): Boolean =
        c.packageManager.getComponentEnabledSetting(alias(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun resolvedPackage(c: Context): String? = runCatching {
        c.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    fun read(c: Context): State = state(resolvedPackage(c), c.packageName, aliasEnabled(c))

    fun setOffered(c: Context, offered: Boolean) {
        c.packageManager.setComponentEnabledSetting(
            alias(c),
            if (offered) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Pide a Android que pregunte con qué app abrir Inicio. Sin inicio elegido, el pedido de Inicio
     * muestra esa ventana en cualquier Android; los ajustes de inicio quedan como segunda opción
     * porque no todas las radios los traen.
     */
    fun choose(c: Context) {
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        val hasSettings = runCatching { settings.resolveActivity(c.packageManager) != null }.getOrDefault(false)
        if (read(c) == State.OTHER && hasSettings) startSafe(c, settings)
        else startSafe(c, homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Datos de una versión publicada en GitHub Releases (v0.81.0). */
internal data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val assetApiUrl: String,
    val downloadUrl: String,
)

/**
 * Actualizador integrado desde GitHub Releases (v0.81.0).
 *
 * El repositorio es privado: el usuario puede pegar una sola vez un token de lectura
 * (`Contents: Read-only`) en Ajustes → Sistema; se guarda únicamente en `SharedPreferences("actualizaciones")`
 * y nunca pasa a los logs. Antes de abrir el instalador de Android se verifica que el APK descargado
 * esté firmado con el mismo certificado que la app instalada.
 */
internal object Updates {
    private const val PREFS = "actualizaciones"
    private const val KEY_TOKEN = "github_token"
    private const val LATEST_URL = "https://api.github.com/repos/AGonzalez-97/onix-launcher/releases/latest"

    val status = mutableStateOf("Instalada: v${BuildConfig.VERSION_NAME}")
    val busy = mutableStateOf(false)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** El token compilado desde el secreto `UPDATER_READ_TOKEN` o el pegado a mano en el equipo. Pura. */
    fun pickToken(saved: String?, builtIn: String?): String? =
        saved?.trim()?.takeIf { isValidToken(it) } ?: builtIn?.trim()?.takeIf { isValidToken(it) }

    /**
     * Si [raw] tiene forma de token de GitHub: 8 a 255 letras, números, `_`, `-` o `.`, sin espacios ni
     * saltos de línea (v0.81.24: un informe pegado como token rompía el pedido y se veía como "sin conexión"). Pura.
     */
    fun isValidToken(raw: String): Boolean = Regex("[A-Za-z0-9_.\\-]{8,255}").matches(raw.trim())

    fun token(c: Context): String? = pickToken(prefs(c).getString(KEY_TOKEN, null), BuildConfig.UPDATER_READ_TOKEN)

    fun setToken(c: Context, raw: String) {
        prefs(c).edit().putString(KEY_TOKEN, raw.trim()).apply()
    }

    /** Lee la respuesta JSON de `/releases/latest` y extrae versión y enlace del APK. Pura, para los tests. */
    fun parseRelease(json: String): ReleaseInfo? {
        val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1).orEmpty()
        val ver = Regex("""Onix Launcher v(\d+\.\d+\.\d+)""").find(json)?.groupValues?.get(1)
            ?: Regex(""""(?:name|tag_name)"\s*:\s*"[^"]*?v?(\d+\.\d+\.\d+)""").find(json)?.groupValues?.get(1)
            ?: return null
        val apkObj = Regex("""\{[^{}]*"name"\s*:\s*"[^"]*\.apk"[^{}]*\}""", RegexOption.IGNORE_CASE).find(json)?.value
            ?: return null
        val apiUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(apkObj)?.groupValues?.get(1).orEmpty()
        val dlUrl = Regex(""""browser_download_url"\s*:\s*"([^"]+)"""").find(apkObj)?.groupValues?.get(1).orEmpty()
        return if (apiUrl.isBlank() && dlUrl.isBlank()) null
        else ReleaseInfo(ver, tag, apiUrl.ifBlank { dlUrl }, dlUrl.ifBlank { apiUrl })
    }

    /** Devuelve true si [candidate] es una versión mayor que [current] (por ejemplo `0.81.0` > `0.80.0`). Pura. */
    fun isNewer(current: String, candidate: String): Boolean {
        val a = current.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = candidate.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (bv != av) return bv > av
        }
        return false
    }

    /** Número de compilación de una etiqueta `build-178` (es el `versionCode` de ese APK); null si no lo trae. Pura. */
    fun buildNumber(tag: String): Int? = Regex("""build-(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Si [rel] es una actualización para lo instalado: una versión mayor o, con la misma versión,
     * una compilación mayor (v0.81.12: una versión que se vuelve a publicar corregida también llega). Pura.
     */
    fun hasUpdate(currentVersion: String, currentBuild: Int, rel: ReleaseInfo): Boolean {
        if (isNewer(currentVersion, rel.version)) return true
        if (isNewer(rel.version, currentVersion)) return false
        return (buildNumber(rel.tagName) ?: return false) > currentBuild
    }

    fun checkAndInstall(context: Context) {
        if (busy.value) return
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        busy.value = true
        status.value = "Buscando actualización en GitHub…"
        Thread({
            try {
                val tok = token(app)
                val headers = buildMap {
                    put("Accept", "application/vnd.github+json")
                    if (tok != null) put("Authorization", "Bearer $tok")
                }
                val r = Net.request("GET", LATEST_URL, headers = headers, timeoutMs = 12_000)
                if (r == null) {
                    main.post { status.value = "Sin conexión a internet"; busy.value = false }
                    return@Thread
                }
                if (r.first == 401 || r.first == 404) {
                    val msg = if (tok == null) "Pegá tu token de GitHub primero (repo privado)"
                    else "Token inválido o sin permiso de lectura (HTTP ${r.first})"
                    main.post { status.value = msg; busy.value = false }
                    return@Thread
                }
                if (r.first !in 200..299) {
                    main.post { status.value = "GitHub respondió HTTP ${r.first}"; busy.value = false }
                    return@Thread
                }
                val rel = parseRelease(r.second)
                if (rel == null) {
                    main.post { status.value = "La última versión no tiene el archivo APK listo"; busy.value = false }
                    return@Thread
                }
                if (!hasUpdate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, rel)) {
                    main.post { status.value = "Ya tenés la última versión (v${BuildConfig.VERSION_NAME}, compilación ${BuildConfig.VERSION_CODE})"; busy.value = false }
                    return@Thread
                }
                main.post { status.value = "Descargando v${rel.version}…" }
                val apkFile = File(File(app.cacheDir, "updates").apply { mkdirs() }, "onix-launcher.apk")
                if (!downloadApk(rel.assetApiUrl, tok, apkFile)) {
                    main.post { status.value = "No se pudo descargar el APK de v${rel.version}"; busy.value = false }
                    return@Thread
                }
                if (!verifySignature(app, apkFile)) {
                    apkFile.delete()
                    main.post { status.value = "El APK descargado no coincide con la firma de la app"; busy.value = false }
                    return@Thread
                }
                main.post {
                    busy.value = false
                    status.value = "v${rel.version} descargada; abriendo instalador…"
                    promptInstall(app, apkFile)
                }
            } catch (e: Exception) {
                AppLog.warn("Actualizaciones: fallo al buscar o descargar", e)
                main.post { status.value = "Error: ${e.javaClass.simpleName}"; busy.value = false }
            }
        }, "onix-updater").apply { isDaemon = true; start() }
    }

    private fun downloadApk(url: String, token: String?, dest: File): Boolean {
        var target = try { URL(url) } catch (_: Exception) { return false }
        repeat(5) {
            var conn: HttpURLConnection? = null
            try {
                conn = target.openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "OnixLauncher/${BuildConfig.VERSION_NAME}")
                if (target.host.equals("api.github.com", ignoreCase = true)) {
                    conn.setRequestProperty("Accept", "application/octet-stream")
                    if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return false
                    target = URL(target, loc)
                    return@repeat
                }
                if (code !in 200..299) return false
                conn.inputStream.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                return dest.length() > 100_000L
            } catch (_: Exception) {
                return false
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun verifySignature(context: Context, apkFile: File): Boolean = try {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val archiveSigs = archive?.signatures
        val installedSigs = installed?.signatures
        archive != null &&
            archive.packageName == context.packageName &&
            !archiveSigs.isNullOrEmpty() &&
            !installedSigs.isNullOrEmpty() &&
            archiveSigs[0] == installedSigs[0]
    } catch (_: Exception) {
        false
    }

    private fun promptInstall(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            status.value = "Habilitá \"Instalar apps desconocidas\" y volvé a tocar Instalar"
            startSafe(
                context,
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafe(context, intent)
    }
}

/** Estado global de la pantalla de Ajustes (se abre desde el dock, Apps y Diagnóstico). */
internal object SettingsNav {
    val open = mutableStateOf(false)
    val category = mutableStateOf(SettingsCategory.SCREEN)

    fun show(category: SettingsCategory = SettingsCategory.SCREEN) {
        this.category.value = category
        open.value = true
    }

    fun close() {
        open.value = false
    }

    /** Si se está viendo Diagnóstico (el reloj corre cada segundo solo en ese caso). */
    fun showsDiagnostics(): Boolean = open.value && category.value == SettingsCategory.DIAG
}

/**
 * Registro del último cierre inesperado (v0.80.0): guarda el error en SharedPreferences antes de
 * delegar al manejador de Android, para poder consultarlo desde Ajustes → Sistema sin conectar ADB.
 */
internal object CrashLog {
    private const val PREFS = "fallos"
    private const val KEY_LAST = "ultimo"
    val last = mutableStateOf<String?>(null)
    private var installed = false

    fun format(t: Throwable, timestamp: String): String {
        val frame = t.stackTrace.firstOrNull { it.className.startsWith("com.axel.onixlauncher") }
            ?: t.stackTrace.firstOrNull()
        val where = frame?.let { " en ${it.fileName ?: it.className}:${it.lineNumber}" } ?: ""
        return "$timestamp · ${t.javaClass.simpleName}: ${t.message ?: "sin detalle"}$where"
    }

    fun load(context: Context) {
        last.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)
    }

    fun clear(context: Context) {
        last.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply()
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                val text = format(throwable, ts)
                appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LAST, text).commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

/**
 * Pantalla de Ajustes sobre el lienzo de 1024x600. Se cierra con Atrás, con "Cerrar" o tocando
 * afuera del panel.
 */
@Composable
internal fun SettingsOverlay(
    lightsMode: LightsMode,
    onLightsMode: () -> Unit,
    onAbout: () -> Unit,
    onClose: () -> Unit,
    /** Lecturas en vivo para la categoría Diagnóstico. */
    diagnostics: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onClose)
    var category by remember { mutableStateOf(SettingsNav.category.value) }
    val currentGlass = ThemePrefs.glass.value
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = currentGlass.backdropAlpha)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        val glassBg = (if (ThemePrefs.isLight.value) Color(0xFFFFFFFF) else Color(0xFF0F172A)).copy(alpha = currentGlass.alpha)
        Row(
            Modifier.size(940.dp, 540.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                .background(glassBg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(18.dp)
        ) {
            Column(Modifier.width(230.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ajustes", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                for (c in SettingsCategory.entries) {
                    val selected = c == category
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Accent else Track)
                            .clickable { category = c; SettingsNav.category.value = c }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(c.label, color = if (selected) OnAccent else TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(c.hint, color = if (selected) OnAccent else Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                PillButton("Cerrar", Track, TextMain, Modifier.fillMaxWidth(), onClose)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (category) {
                    SettingsCategory.SCREEN -> ScreenSettings()
                    SettingsCategory.SOUND -> SoundSettings()
                    SettingsCategory.CAR -> CarSettings(lightsMode, onLightsMode)
                    SettingsCategory.SYSTEM -> SystemSettings(onAbout)
                    SettingsCategory.DIAG -> diagnostics()
                }
            }
        }
    }
}

@Composable
private fun ScreenSettings() {
    val context = LocalContext.current
    val currentMode = ThemePrefs.mode.value
    val currentAccent = ThemePrefs.accent.value

    val currentGlass = ThemePrefs.glass.value

    SettingRow("Tema", "Automático usa claro de día y pasa a oscuro al prender faros o anochecer.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in ThemeMode.entries) {
                val selected = m == currentMode
                PillButton(
                    m.label,
                    if (selected) Accent else Track,
                    if (selected) OnAccent else TextMain,
                    Modifier.width(116.dp)
                ) { ThemePrefs.setMode(context, m) }
            }
        }
    }

    SettingRow("Transparencia del menú", "Estilo vidriado translúcido (Glass) o cobertura sólida.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in GlassStyle.entries) {
                val selected = g == currentGlass
                PillButton(
                    g.label,
                    if (selected) Accent else Track,
                    if (selected) OnAccent else TextMain,
                    Modifier.width(116.dp)
                ) { ThemePrefs.setGlass(context, g) }
            }
        }
    }

    SettingRow("Color de acento", "Cambia el color principal de botones, indicadores y bordes.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (a in AccentPalette.entries) {
                val selected = a == currentAccent
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(a.color)
                        .border(if (selected) 3.dp else 1.dp, if (selected) TextMain else Color(0x55FFFFFF), CircleShape)
                        .clickable { ThemePrefs.setAccent(context, a) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Text("✓", color = OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    SettingRow("Brillo","Máximo mantiene el launcher al 100 %; Radio respeta el brillo del equipo.") {
        PillButton(
            if (ScreenBrightness.forceMax.value) "Máximo" else "Según la radio",
            Track, TextMain, Modifier.width(200.dp)
        ) { ScreenBrightness.toggle(context) }
    }
    SettingRow("Modo liviano", "Menos animaciones para mayor fluidez.") {
        PillButton(LiteMode.label(), Track, TextMain, Modifier.width(200.dp)) { LiteMode.cycle(context) }
    }
}

/** Una opción para prender o apagar, o para elegir entre varias (sonido de inicio, avisos). */
@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) Accent else Track)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) OnAccent else TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Sonido de inicio (v0.69.0) y avisos (v0.78.0). v0.81.11: con las mismas filas que el resto de
 * Ajustes (antes eran los paneles chicos de Diagnóstico).
 */
@Composable
private fun SoundSettings() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { IntroSound.load(context); AlertPrefs.load(context); SpeedWarning.load(context) }

    SettingRow("Sonido de inicio", "Qué suena al abrir el launcher: voz y sonido, solo sonido o nada.") {
        PillButton(IntroSound.mode.value.label.replaceFirstChar { it.uppercase() }, Track, TextMain, Modifier.width(200.dp)) {
            IntroSound.cycle(context)
        }
    }
    if (IntroSound.mode.value == IntroSound.Mode.VOICE && IntroSound.voices.value.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Voz del saludo (tocá una para escucharla y elegirla)", color = Muted, fontSize = 12.sp)
            for (row in IntroSound.voices.value.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in row) {
                        Chip("▶ " + (IntroVoices.labels[id] ?: id), id == IntroSound.voice.value, Modifier.weight(1f)) {
                            IntroSound.preview(context, id)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    // v0.81.18: apagado, el launcher no le da play a nada por su cuenta
    var autoResume by remember { mutableStateOf(LastAudio.autoResumeEnabled(context)) }
    SettingRow("Retomar la música al dar contacto", "Sigue con lo último que sonaba. Apagado, queda en pausa hasta que toques play.") {
        PillButton(if (autoResume) "Sí" else "No", Track, TextMain, Modifier.width(200.dp)) {
            autoResume = !autoResume
            LastAudio.setAutoResume(context, autoResume)
        }
    }
    // v0.81.18: antes era un renglón al pie de la biblioteca de Spotify (Navegación → Música)
    LaunchedEffect(Unit) { SpotifyAuth.refreshStatus(context) }
    if (SpotifyAuth.status.value == "conectado") {
        SettingRow("Cuenta de Spotify", "Conectada. Las listas, recientes y el buscador usan esta cuenta.") {
            PillButton("Desconectar", Track, TextMain, Modifier.width(200.dp)) { SpotifyAuth.logout(context) }
        }
    }
    SettingRow("Sonido de los avisos", "Todo, solo los avisos de seguridad o silencio.") {
        PillButton(AlertPrefs.mode.value.label, Track, TextMain, Modifier.width(200.dp)) {
            AlertPrefs.cycleMode(context)
            Voice.stopTalking()
        }
    }
    SettingRow("Qué avisa", "Velocidad: tocá para elegir el máximo.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // v0.79.0: no → 60 → 80 → … → 130 ("no" = apagado)
            val max = SpeedWarning.limit.value
            Chip(if (max != null) "✓ Vel. $max" else "✕ Velocidad", max != null, Modifier.width(104.dp)) { SpeedWarning.cycle(context) }
            for ((label, state) in listOf("Nafta" to AlertPrefs.fuel, "Luces" to AlertPrefs.lights, "Motor" to AlertPrefs.engine)) {
                val on = state.value
                Chip((if (on) "✓ " else "✕ ") + label, on, Modifier.width(86.dp)) { state.value = !on; AlertPrefs.save(context) }
            }
        }
    }
}

/** Auto y conexiones (v0.81.11): lector OBD2, luces del tablero y destinos desde el celular. */
@Composable
private fun CarSettings(lightsMode: LightsMode, onLightsMode: () -> Unit) {
    SettingRow(
        "Lector OBD2",
        ObdState.status.value.replaceFirstChar { it.uppercase() } + (ObdState.device.value?.let { " · $it" } ?: "")
    ) {
        // Mismo aviso que el contacto: empieza de cero y prueba ya, sin esperar el próximo reintento
        PillButton("Reintentar", Track, TextMain, Modifier.width(200.dp)) {
            ObdRecovery.kick.value = ObdRecovery.kick.value + 1
        }
    }
    SettingRow("Luces del tablero", "Normalmente siguen a la perilla de luces del auto (cable ILL).") {
        PillButton(lightsMode.label, Track, TextMain, Modifier.width(200.dp), onLightsMode)
    }
    SettingRow("Destinos desde el celular", PhoneShare.status.value.replaceFirstChar { it.uppercase() }) { }
}

@Composable
private fun SystemSettings(onAbout: () -> Unit) {
    val context = LocalContext.current
    val crash = CrashLog.last.value
    var home by remember { mutableStateOf(HomeDefault.read(context)) }
    var homePkg by remember { mutableStateOf(HomeDefault.resolvedPackage(context) ?: "?") }
    // Se vuelve a consultar cada segundo: al volver de la ventana de Android para elegir el inicio
    // la fila tiene que mostrar lo que quedó elegido
    LaunchedEffect(Unit) {
        while (true) {
            home = HomeDefault.read(context)
            homePkg = HomeDefault.resolvedPackage(context) ?: "?"
            kotlinx.coroutines.delay(1_000)
        }
    }

    SettingRow("Pantalla de inicio", HomeDefault.describe(home, homePkg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (home != HomeDefault.State.NOT_OFFERED) {
                PillButton("Quitar", Track, TextMain, Modifier.width(110.dp)) {
                    HomeDefault.setOffered(context, false)
                    home = HomeDefault.read(context)
                }
            }
            if (home != HomeDefault.State.ONIX) {
                PillButton(if (home == HomeDefault.State.NOT_OFFERED) "Usar como inicio" else "Elegir", Accent, OnAccent, Modifier.width(170.dp)) {
                    HomeDefault.setOffered(context, true)
                    HomeDefault.choose(context)
                    home = HomeDefault.read(context)
                    homePkg = HomeDefault.resolvedPackage(context) ?: "?"
                }
            }
        }
    }
    var hasToken by remember { mutableStateOf(Updates.token(context) != null) }
    val updateStatus = Updates.status.value
    val busy = Updates.busy.value

    SettingRow(
        "Actualizaciones",
        "$updateStatus · " + if (hasToken) "Token guardado en el equipo." else "Copiá tu token de lectura de GitHub y tocá Pegar token."
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (hasToken) "Cambiar token" else "Pegar token", Track, TextMain, Modifier.width(140.dp)) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                if (Updates.isValidToken(clip)) {
                    Updates.setToken(context, clip)
                    hasToken = true
                    Updates.status.value = "Token guardado; ya podés buscar actualizaciones"
                } else {
                    Updates.status.value = "Lo copiado no parece un token de GitHub: copiá solo el token"
                }
            }
            PillButton(if (busy) "Buscando…" else "Buscar e instalar", Accent, OnAccent, Modifier.width(170.dp)) {
                Updates.checkAndInstall(context)
            }
        }
    }
    SettingRow("Acerca de", "Versión actual, créditos y licencias.") {
        PillButton("Abrir", Track, TextMain, Modifier.width(180.dp), onAbout)
    }
    SettingRow(
        "Registro de fallos",
        crash ?: "Sin cierres inesperados registrados."
    ) {
        if (crash != null) {
            PillButton("Limpiar", Track, TextMain, Modifier.width(180.dp)) { CrashLog.clear(context) }
        } else {
            Text("OK", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Una fila de ajuste: título y explicación a la izquierda, el control a la derecha. */
@Composable
private fun SettingRow(title: String, detail: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        control()
    }
}
