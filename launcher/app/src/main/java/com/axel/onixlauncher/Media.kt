package com.axel.onixlauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

class MediaInfo(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    /** Posición en el momento de la lectura ([readAt]). */
    val positionMs: Long,
    val durationMs: Long,
    val controller: MediaController?,
    /** `SystemClock.elapsedRealtime()` de la lectura, para calcular la posición sin volver a consultar. */
    val readAt: Long = SystemClock.elapsedRealtime()
) {
    /** Posición estimada ahora: la leída más el tiempo que pasó si está sonando. */
    fun positionAt(nowElapsed: Long): Long {
        val p = if (playing) positionMs + (nowElapsed - readAt) else positionMs
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }
}

class MediaResult(val hasAccess: Boolean, val info: MediaInfo?)

/**
 * Acción propia de la app que suena (por ejemplo, aleatorio o "me gusta" de Spotify), buscada
 * por palabras en su identificador o nombre. Null si la app no la ofrece.
 */
internal fun MediaController.customAction(vararg keys: String): android.media.session.PlaybackState.CustomAction? =
    playbackState?.customActions?.firstOrNull { a ->
        keys.any { k -> a.action.contains(k, ignoreCase = true) || a.name.toString().contains(k, ignoreCase = true) }
    }

/**
 * Da play a la sesión de música de otra app (por ejemplo Spotify) si está abierta. Devuelve
 * false si no hay sesión de esa app o no hay acceso a las notificaciones (entonces hay que abrirla).
 */
internal fun playOtherApp(context: Context, pkg: String): Boolean = try {
    leaveAntenna(context)
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val c = msm.getActiveSessions(ComponentName(context, MediaListenerService::class.java)).firstOrNull { it.packageName == pkg }
    c?.transportControls?.play()
    c != null
} catch (e: Exception) {
    false
}

internal fun playUriOtherApp(context: Context, pkg: String, uri: String): Boolean = try {
    leaveAntenna(context)
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val c = msm.getActiveSessions(ComponentName(context, MediaListenerService::class.java)).firstOrNull { it.packageName == pkg }
    c?.transportControls?.playFromUri(android.net.Uri.parse(uri), null)
    c?.transportControls?.play()
    c != null
} catch (e: Exception) {
    false
}

const val SPOTIFY = "com.spotify.music"

/**
 * Le da play a Spotify sin abrirlo (regla de oro: todo dentro del launcher). Si su sesión está
 * activa usa los controles; si Spotify estaba cerrado (por ejemplo, después de apagar la radio),
 * le manda la tecla PLAY: Spotify arranca en segundo plano y sigue con lo último que sonaba.
 */
internal fun spotifyPlay(context: Context) {
    leaveAntenna(context)
    playOtherApp(context, SPOTIFY)
    for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
        val i = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY)
            .putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
        try {
            context.sendBroadcast(i)
        } catch (e: Exception) {
            AppLog.warn("Spotify: no se pudo mandar PLAY", e)
        }
    }
}

/**
 * Última fuente de audio (v0.38.0): al encender la radio se retoma lo que sonaba, como un estéreo de
 * fábrica. Se guarda cuando algo empieza a sonar y se borra solo cuando el usuario pausa desde el
 * launcher (si el sistema corta el audio al apagar, se retoma). SharedPreferences `audio`.
 */
object LastAudio {
    private fun prefs(c: Context) = c.getSharedPreferences("audio", Context.MODE_PRIVATE)

    fun markSpotify(c: Context) {
        prefs(c).edit().putString("fuente", "spotify").apply()
    }

    fun markRadio(c: Context, station: String) {
        prefs(c).edit().putString("fuente", "radio").putString("emisora", station).apply()
    }

    /** v0.81.18: ajuste "Retomar la música al dar contacto" (prendido por defecto). */
    fun autoResumeEnabled(c: Context): Boolean = prefs(c).getBoolean("retomar", true)

    fun setAutoResume(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("retomar", enabled).apply()
        AppLog.info("Ajustes: retomar la música al dar contacto = $enabled")
    }

    /**
     * Si el launcher puede dar play por su cuenta: con el ajuste prendido y sin Android Auto ni
     * CarPlay (ahí el audio es del celular). Pura.
     */
    fun mayAutoPlay(enabled: Boolean, projection: Boolean): Boolean = enabled && !projection

    /** El usuario pausó a propósito: no retomar al encender. */
    fun userPaused(c: Context) {
        prefs(c).edit().remove("fuente").apply()
    }

    /** Lo último que sonaba ("spotify", "radio:<emisora>") o null si el usuario lo pausó (v0.79.4). */
    fun lastSource(c: Context): String? = decide(prefs(c).getString("fuente", null), prefs(c).getString("emisora", null))

    /** Qué retomar: "spotify", "radio:<emisora>" o null (función de lectura, para los tests). */
    fun decide(source: String?, station: String?): String? = when {
        source == "spotify" -> "spotify"
        source == "radio" && !station.isNullOrBlank() -> "radio:$station"
        else -> null
    }

    /** Si el launcher es la pantalla de inicio del equipo (la radio del auto, no el celular). */
    fun isHomeLauncher(c: Context): Boolean = try {
        c.packageManager.getComponentEnabledSetting(android.content.ComponentName(c, "com.axel.onixlauncher.HomeAlias")) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (e: Exception) {
        false
    }

    /**
     * Retoma la última fuente si no suena nada. Solo cuando el launcher es la pantalla de inicio
     * (v0.38.1): en un celular no tiene que arrancar Spotify al desbloquear.
     */
    fun resume(c: Context) {
        if (!isHomeLauncher(c)) return
        if (RadioState.playing.value || RadioState.buffering.value) return
        // AUD-049: con Android Auto o CarPlay conectados, el audio es del celular
        if (!mayAutoPlay(autoResumeEnabled(c), Projection.sessionActive(c))) return
        try {
            resumeNow(c)
        } catch (e: Exception) {
            // Por ejemplo, Android 8+ no deja arrancar el servicio de radio desde segundo plano
            AppLog.warn("Al encender: no se pudo retomar el audio", e)
        }
    }

    /**
     * La red volvió después de un corte (v0.40.0): si lo último era la radio y no suena (se agotaron
     * los reintentos sin red), se vuelve a poner la misma emisora. Si el usuario la pausó, no.
     */
    fun resumeRadioAfterNetwork(c: Context) {
        if (RadioState.playing.value || RadioState.buffering.value) return
        if (!autoResumeEnabled(c)) return
        if (Projection.sessionActive(c)) {
            AppLog.info("Red: volvió con Android Auto / CarPlay activo; mando PLAY")
            mediaKey(c, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            return
        }
        val p = prefs(c)
        val what = decide(p.getString("fuente", null), p.getString("emisora", null)) ?: return
        if (what == "spotify") {
            val alreadyPlaying = runCatching { readMedia(c).info?.let { it.pkg == SPOTIFY && it.playing } == true }.getOrDefault(false)
            if (!alreadyPlaying) {
                AppLog.info("Red: retomo Spotify")
                spotifyPlay(c)
            }
            return
        }
        if (!what.startsWith("radio:")) return
        val name = what.removePrefix("radio:")
        val s = RadioCatalog.stations.value.firstOrNull { it.name == name } ?: return
        try {
            AppLog.info("Red: retomo la radio $name")
            Radio.play(c, s)
        } catch (e: Exception) {
            AppLog.warn("Red: no se pudo retomar la radio", e)
        }
    }

    private fun resumeNow(c: Context) {
        val p = prefs(c)
        when (val what = decide(p.getString("fuente", null), p.getString("emisora", null))) {
            null -> return
            "spotify" -> {
                AppLog.info("Al encender: retomo Spotify")
                spotifyPlay(c)
            }
            else -> {
                val name = what.removePrefix("radio:")
                val s = RadioCatalog.stations.value.firstOrNull { it.name == name } ?: return
                AppLog.info("Al encender: retomo la radio $name")
                Radio.play(c, s)
            }
        }
    }
}

internal val ShuffleKeys = arrayOf("shuffle", "aleatori")
internal val LikeKeys = arrayOf("like", "heart", "favorit", "collection", "library", "bibliotec", "me gusta", "guardar", "save")

/** Lee la sesión de música activa (Spotify u otro reproductor). Requiere acceso a notificaciones. */
fun readMedia(context: Context): MediaResult {
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val controllers = try {
        msm.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
    } catch (e: SecurityException) {
        return MediaResult(false, null)
    }
    val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: controllers.firstOrNull()
        ?: return MediaResult(true, null)
    return MediaResult(true, infoOf(c))
}

private fun infoOf(c: MediaController): MediaInfo {
    val md = c.metadata
    val st = c.playbackState
    val playing = st?.state == PlaybackState.STATE_PLAYING
    val now = SystemClock.elapsedRealtime()
    var pos = st?.position ?: 0L
    if (playing && st != null) {
        pos += ((now - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
    }
    val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    return MediaInfo(
        pkg = c.packageName ?: "",
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sin título",
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
        art = art,
        playing = playing,
        positionMs = pos.coerceAtLeast(0L),
        durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        controller = c,
        readAt = now
    )
}

/**
 * Escucha las sesiones de música en lugar de consultarlas cada segundo (AUD-005):
 * avisa cuando aparece o desaparece un reproductor, cambia la canción o se pausa.
 */
class MediaWatcher(private val context: Context, private val onChange: (MediaResult) -> Unit) {
    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var watched: List<MediaController> = emptyList()
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty())
        publish()
    }

    /** Empieza a escuchar. Sin acceso a notificaciones no puede: se reintenta con [retryIfNeeded]. */
    fun start() {
        if (!listening) {
            try {
                msm.addOnActiveSessionsChangedListener(sessions, component, handler)
                listening = true
                watch(msm.getActiveSessions(component))
            } catch (e: SecurityException) {
                listening = false
            }
        }
        publish()
    }

    /** Si todavía no hay acceso, lo vuelve a intentar (el usuario puede habilitarlo en cualquier momento). */
    fun retryIfNeeded() {
        if (!listening) start()
    }

    fun stop() {
        if (listening) msm.removeOnActiveSessionsChangedListener(sessions)
        listening = false
        watch(emptyList())
    }

    private fun watch(list: List<MediaController>) {
        watched.forEach { it.unregisterCallback(callback) }
        watched = list
        list.forEach { it.registerCallback(callback, handler) }
    }

    private fun publish() {
        onChange(
            try {
                readMedia(context)
            } catch (e: RuntimeException) {
                AppLog.warn("Música: no se pudo leer la sesión", e)
                MediaResult(listening, null)
            }
        )
    }
}

/** Música actual, actualizada por eventos. Reintenta el acceso cada 5 segundos mientras no lo tenga. */
@Composable
fun rememberMedia(): MediaResult {
    val context = LocalContext.current
    var media by remember { mutableStateOf(MediaResult(true, null)) }
    val watcher = remember { MediaWatcher(context.applicationContext) { media = it } }
    DisposableEffect(watcher) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    LaunchedEffect(media.hasAccess) {
        while (!media.hasAccess) {
            delay(5_000)
            watcher.retryIfNeeded()
        }
    }
    return media
}

/** Posición de la canción, recalculada localmente una vez por segundo solo mientras suena. */
@Composable
fun rememberPlaybackPosition(info: MediaInfo?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(info) {
        while (info?.playing == true && RenderFlags.animate) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return info?.positionAt(maxOf(now, info.readAt)) ?: 0L
}

/**
 * Retomar al encender: una vez al abrir la app y cada vez que se prende la pantalla (la radio
 * "despierta" al poner contacto sin reiniciar).
 */
object AutoResume {
    @Volatile var done = false
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                handler.postDelayed({ LastAudio.resume(app) }, 4000)
            }
        }
        app.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
        // v0.79.4: al poner contacto la radio a veces deja su fuente de audio fuera de Android y
        // Spotify "suena" sin salir por los parlantes. Se devuelve la fuente a Android, como cuando
        // el autor pasaba a la radio y volvía a Spotify, y se retoma lo último.
        val ignition = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                val state = ObdRecovery.mcuState(intent)
                AppLog.info("Contacto: la radio avisó estado=$state")
                if (state != 1) return
                // v0.79.16: cuánto tarda en volver la red al encender (el sonido de Spotify depende de ella)
                IgnitionNet.CHECKS_S.forEach { s -> handler.postDelayed({ IgnitionNet.log(app, s) }, s * 1_000L) }
                handler.postDelayed({ IgnitionAudio.restore(app) }, IgnitionAudio.DELAY_MS)
            }
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                app, ignition, android.content.IntentFilter(ObdRecovery.MCU_ACTION), androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
}

/**
 * Estado de la red después de poner contacto (v0.79.16). El informe del 07/11 mostró la radio sin
 * internet durante más de una hora (UnknownHostException en todo): Spotify no puede dar sonido ni
 * listas sin red, y eso se confundía con una falla del audio.
 */
object IgnitionNet {
    /** Segundos después del contacto en que se anota la red (0 = apenas llega el aviso). */
    val CHECKS_S = listOf(0, 10, 30, 60)

    /** Texto de "Últimos eventos". [typeName] es el tipo de red (WIFI, MOBILE…) o null sin conexión. Pura. */
    fun describe(afterS: Int, connected: Boolean, typeName: String?): String =
        "Red al encender (+$afterS s): " + if (connected) "${typeName ?: "red"} conectada" else "sin conexión"

    fun log(c: Context, afterS: Int) {
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            AppLog.info(describe(afterS, info != null && info.isConnected, info?.typeName))
        } catch (e: Exception) {
            AppLog.info("Red al encender (+$afterS s): no se pudo leer (${e.javaClass.simpleName})")
        }
    }
}

/** Fuente de audio de la radio al poner contacto (v0.79.4). */
object IgnitionAudio {
    const val DELAY_MS = 3_000L

    /**
     * Si lo último que sonaba era de Android (Spotify o radio por internet) y la radio no quedó en
     * el FM por antena, se le pide la fuente Android (aunque ya figure: el pedido destraba el audio). Pura.
     */
    fun shouldRestore(lastAudio: String?, currentSource: Int): Boolean =
        lastAudio != null && !TunerMath.isRadioSource(currentSource)

    /**
     * Devuelve true si devolvió el audio a Android (y retomó lo último). v0.79.9: también se usa al
     * arrancar la app de cero: si el contacto se puso antes de que la app existiera, el aviso de la
     * radio ya pasó y Spotify "sonaba" con la fuente de la radio afuera de Android (mudo).
     */
    /** Si Spotify era lo último y ya figura sonando, hay que darle pausa y play (v0.79.11). Pura. */
    fun shouldKickSpotify(lastAudio: String?, spotifyPlaying: Boolean): Boolean =
        lastAudio == "spotify"

    const val KICK_DELAY_MS = 5_000L
    const val FOREGROUND_MS = 4_000L

    /**
     * v0.79.13: la radio solo deja salir el audio del proceso que ella llama "válido"
     * (`sys.nwd.valid.play.pid`). Con Spotify mudo el pid válido era otro y el que sonaba era el de
     * Spotify (informes 08:56 y 10:37); al abrir Spotify pasó a ser el mismo y sonó. Pura: true si
     * suena algo y ninguno de los pids que suenan es el válido.
     */
    fun needsForeground(valid: String?, playing: String?, sessionPlaying: Boolean = true): Boolean {
        if (!sessionPlaying) return true
        val playingPids = playing.orEmpty().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (playingPids.isEmpty()) return false
        val v = valid.orEmpty().trim()
        return v.isEmpty() || v !in playingPids
    }

    const val NAVI_SOUND_FILE = "/nwdappconfig/app/NaviSound.ini"

    /**
     * v0.81.16: la radio no cierra al despertar a las apps listadas en `NaviSound.ini` (regla leída del
     * firmware, `NwdAudioService`). Con Spotify en esa lista sigue sonando con el mismo proceso y no
     * hace falta pasarlo al frente. Pura: true si [pkg] figura como primer campo de una línea sin `#`.
     */
    fun radioProtects(naviSound: String?, pkg: String): Boolean =
        naviSound.orEmpty().lineSequence().any {
            val line = it.trim()
            !line.startsWith("#") && line.split(Regex("\\s+")).firstOrNull() == pkg
        }

    private fun readNaviSound(): String? = try {
        java.io.File(NAVI_SOUND_FILE).readText()
    } catch (e: Exception) {
        null
    }

    /** v0.79.14: ajustes de la radio que podrían cortar el audio de Android (para el registro). */
    private fun armState(c: Context): String = listOf("arm_sound", "key_arm_fm_audio_mode", "mcu_current_source").joinToString(", ") {
        "$it=" + (try { android.provider.Settings.System.getString(c.contentResolver, it) } catch (e: Exception) { null } ?: "?")
    }

    private fun prop(name: String): String? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop", name))
        proc.inputStream.bufferedReader().use { it.readText() }.trim().also { proc.waitFor() }
    } catch (e: Exception) {
        null
    }

    /**
     * v0.79.13: si Spotify era lo último, suena (según su sesión) y la radio no lo tiene como proceso
     * válido, Spotify pasa un instante al frente y se vuelve a Onix: es lo que el autor hacía a mano
     * (abrir Spotify) y lo que lo hacía sonar. Sale a otra app solo en este caso (README, "Salidas a otras apps").
     * El "pausa y play" de la v0.79.11 no alcanzó (informe 12:09).
     */
    fun kickSpotify(c: Context) {
        try {
            val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val s = msm.getActiveSessions(ComponentName(c, MediaListenerService::class.java)).firstOrNull { it.packageName == SPOTIFY }
            val playing = s?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            if (!shouldKickSpotify(LastAudio.lastSource(c), playing)) return
            if (!LastAudio.mayAutoPlay(LastAudio.autoResumeEnabled(c), Projection.sessionActive(c))) return
            val valid = prop("sys.nwd.valid.play.pid")
            val pids = prop("sys.nwd.playing.pids")
            AppLog.info("Al encender: audio válido=$valid, sonando=$pids, sesion=$playing, ${armState(c)}")
            if (radioProtects(readNaviSound(), SPOTIFY)) {
                AppLog.info("Al encender: la radio protege a Spotify (NaviSound.ini); no lo paso al frente")
                if (!playing) spotifyPlay(c)
                return
            }
            if (!needsForeground(valid, pids, playing)) return
            val launch = c.packageManager.getLaunchIntentForPackage(SPOTIFY) ?: return
            AppLog.info("Al encender: la radio no tiene a Spotify como audio válido; lo paso un instante al frente")
            c.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ spotifyPlay(c) }, 1_500L)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ spotifyPlay(c) }, 3_200L)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                try {
                    c.startActivity(
                        android.content.Intent(c, MainActivity::class.java).addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    )
                } catch (e: Exception) {
                    AppLog.warn("Al encender: no se pudo volver a Onix", e)
                }
            }, FOREGROUND_MS)
            handler.postDelayed({
                AppLog.info("Al encender: después de pasar Spotify al frente, audio válido=${prop("sys.nwd.valid.play.pid")}, sonando=${prop("sys.nwd.playing.pids")}, ${armState(c)}")
            }, FOREGROUND_MS + 1_500L)
        } catch (e: Exception) {
            AppLog.warn("Al encender: no se pudo reiniciar Spotify", e)
        }
    }

    fun restore(c: Context): Boolean {
        try {
            if (!LastAudio.isHomeLauncher(c)) return false
            val source = android.provider.Settings.System.getInt(c.contentResolver, "mcu_current_source", 0)
            val last = LastAudio.lastSource(c)
            if (!shouldRestore(last, source)) {
                AppLog.info("Al encender: fuente de la radio $source; nada que devolver")
                return false
            }
            AppLog.info("Al encender: fuente de la radio $source; devuelvo el audio a Android ($last)")
            NwdTuner.bounceToAndroid(c)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({ LastAudio.resume(c) }, 2_500L)
            // Dar tiempo a que Spotify arranque; si figura sonando, se lo reinicia
            handler.postDelayed({ kickSpotify(c) }, KICK_DELAY_MS)
            return true
        } catch (e: Exception) {
            AppLog.warn("Al encender: no se pudo devolver el audio a Android", e)
            return false
        }
    }
}

/** Si suena el FM por la antena, lo corta y devuelve el audio a Android antes de dar play a otra app (v0.51.0). */
internal fun leaveAntenna(context: Context) {
    if (antennaActive()) Radio.stop(context)
}

/**
 * v0.64.0 (idea de LiteDash 1.4.55): algunas apps de música por Bluetooth de la radio no publican
 * su sesión, así que no hay controles. Entonces se manda la tecla de música, como los botones de
 * un volante, a la app que esté sonando.
 */
internal fun mediaKey(context: Context, keyCode: Int) {
    try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    } catch (e: Exception) {
        AppLog.warn("Música: no se pudo mandar la tecla $keyCode", e)
    }
}

