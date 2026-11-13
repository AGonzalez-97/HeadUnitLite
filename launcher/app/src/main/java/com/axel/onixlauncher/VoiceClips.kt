package com.axel.onixlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/*
 * Voces grabadas (v0.61.0). La radio no tiene TextToSpeech ("Servicios de voz de Google"), así que
 * los avisos y la navegación solo hacían un pitido. Ahora hay frases grabadas con Piper (MIT) en
 * assets/voz/<clave>.ogg, generadas por .github/workflows/voces.yml desde tools/voces/frases.txt.
 * Si el equipo tiene TextToSpeech, se usa ese (dice también el nombre de la calle); si no, se
 * encadenan las frases grabadas ("En doscientos metros," + "girá a la derecha.").
 */

/** Qué frases grabadas dicen cada aviso o indicación (funciones puras, para los tests). */
object VoiceClips {
    /** Todas las claves que usa la app; tienen que existir en tools/voces/frases.txt. */
    val ALL = listOf(
        "luces_apagadas", "velocidad_alta", "motor_recalentado", "bateria_no_carga", "voltaje_alto", "bateria_baja",
        "recalculando", "llegaste",
        "en_50", "en_100", "en_150", "en_200", "en_250", "en_300",
        "girar_izq", "girar_der", "leve_izq", "leve_der", "cerrado_izq", "cerrado_der", "giro_u", "derecho",
        "salir", "rotonda", "rotonda_1", "rotonda_2", "rotonda_3", "rotonda_4", "rotonda_5",
        "bifurcacion_izq", "bifurcacion_der", "incorporate",
        // v0.77.0: nafta baja (OBD2)
        "nafta_baja",
        // v0.65.0: slogan de la presentación
        "inicio"
    )

    fun alert(a: EngineAlert): String = when (a) {
        EngineAlert.HOT -> "motor_recalentado"
        EngineAlert.NOT_CHARGING -> "bateria_no_carga"
        EngineAlert.OVERCHARGING -> "voltaje_alto"
        EngineAlert.LOW_BATTERY -> "bateria_baja"
    }

    /** "En … metros," redondeado a 50 m entre 50 y 300 (lo que avisa la navegación antes de la maniobra). */
    fun distance(m: Double): String {
        val r = (Math.round(m / 50.0) * 50).toInt().coerceIn(50, 300)
        return "en_$r"
    }

    /** La maniobra (nombres de OSRM; las de TomTom se traducen en NavApi.maneuver) como frase grabada (sin el nombre de la calle). */
    fun maneuver(type: String, modifier: String, exit: Int): String = when (type) {
        "arrive", "waypoint" -> "llegaste"
        "depart" -> "salir"
        "roundabout", "rotary", "roundabout turn" -> if (exit in 1..5) "rotonda_$exit" else "rotonda"
        "fork" -> if (modifier.contains("left")) "bifurcacion_izq" else "bifurcacion_der"
        "merge" -> "incorporate"
        else -> when (modifier) {
            "left" -> "girar_izq"
            "right" -> "girar_der"
            "slight left" -> "leve_izq"
            "slight right" -> "leve_der"
            "sharp left" -> "cerrado_izq"
            "sharp right" -> "cerrado_der"
            "uturn" -> "giro_u"
            else -> "derecho"
        }
    }

    /** Archivo de la misma frase grabada con ElevenLabs (v0.72.2; voz Comercial desde v0.72.3): ia_<clave>; el slogan y los avisos por voz, igual. */
    fun aiName(key: String): String =
        if (key.startsWith("inicio") || (key.startsWith("luces_") && key != "luces_apagadas")) key else "ia_$key"

    /** Lo que se dice para una maniobra: con [distanceM] el aviso previo ("En 200 metros, …"); sin él, el de "ahora". */
    fun step(type: String, modifier: String, exit: Int, distanceM: Double?): List<String> =
        listOfNotNull(distanceM?.let { distance(it) }, maneuver(type, modifier, exit))
}

/** Reproduce frases grabadas una detrás de otra, bajando la música mientras habla. */
object RecordedVoice {
    private var player: MediaPlayer? = null
    private val queue = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    /** Android 8+ no acepta un oyente nulo para el foco de audio. */
    private val focus = AudioManager.OnAudioFocusChangeListener { }

    /** Si están las voces en el APK (las genera la CI). */
    fun available(context: Context): Boolean = try {
        context.assets.list("voz")?.any { it.endsWith(".ogg") } == true
    } catch (e: Exception) {
        false
    }

    /** Dice [clips] en orden; corta lo que estaba diciendo. */
    fun play(context: Context, clips: List<String>) {
        if (clips.isEmpty()) return
        main.post {
            app = context.applicationContext
            stopNow()
            queue.addAll(clips)
            next()
        }
    }

    private fun next() {
        val c = app ?: return
        val key = queue.removeFirstOrNull()
        if (key == null) {
            stopNow()
            return
        }
        try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            // AUD-061 (v0.67.1): si una frase no carga, se libera el reproductor y el archivo
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                // v0.72.2: primero la versión del Locutor AR (ElevenLabs, ia_<clave>.ogg); si no está, la de Piper
                val fd = runCatching { c.assets.openFd("voz/${VoiceClips.aiName(key)}.ogg") }.getOrNull() ?: c.assets.openFd("voz/$key.ogg")
                fd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                mp.setOnCompletionListener { it.release(); player = null; next() }
                mp.setOnErrorListener { p, _, _ -> p.release(); player = null; next(); true }
                mp.prepare()
                mp.start()
                player = mp
            } catch (e: Exception) {
                mp.release()
                throw e
            }
        } catch (e: Exception) {
            AppLog.warn("Voz grabada: no se pudo decir $key", e)
            next()
        }
    }

    /** Corta la frase en curso y las que esperaban. */
    fun stop() { main.post { stopNow() } }

    private fun stopNow() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        queue.clear()
        val c = app ?: return
        @Suppress("DEPRECATION")
        runCatching { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(focus) }
    }
}
