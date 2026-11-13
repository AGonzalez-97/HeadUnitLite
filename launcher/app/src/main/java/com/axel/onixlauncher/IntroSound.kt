package com.axel.onixlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 * Sonido de inicio (v0.65.0): con la presentación suena un sonido propio y, si se elige, la voz
 * grabada dice "Find new roads". El sonido se compone en el código (v0.81.15: un acorde que entra
 * de a poco y dos notas claras): no se copia el de ningún auto, que es de su marca. Se elige en la
 * ventana del saludo: voz y sonido / solo sonido (por defecto desde v0.81.15) / apagado.
 *
 * v0.69.0: la voz del slogan se elige entre varias (locutor de radio, épico, británico…), que la
 * CI genera con Piper y un efecto de locutor (tools/voces/inicio.txt). Se eligen en Diagnóstico,
 * escuchándolas, junto con voz y sonido / solo sonido / apagado.
 */

/** Voces para elegir del slogan (pura, para los tests). */
object IntroVoices {
    /** id → nombre para mostrar, en el orden de la pantalla. "original" es la voz de v0.65.0. */
    val labels: Map<String, String> = linkedMapOf(
        // v0.70.0: con emoción, generadas con ElevenLabs (solo si la CI tuvo la clave).
        // v0.72.0: todas dicen solo "Find new roads"; "comercial" = locutor de publicidad grave
        "comercial" to "Comercial",
        "ar_locutor" to "Locutor AR",
        "ar_locutora" to "Locutora AR",
        "asistente" to "Asistente IA",
        // v0.69.0: Piper con efecto de locutor
        "locutor" to "Locutor",
        "epico" to "Épico",
        "locutor2" to "Locutor 2",
        "britanico" to "Británico",
        "grave" to "Grave",
        "daniela" to "Daniela",
        "original" to "Original"
    )

    /** Nombre del archivo de la frase (sin .ogg) en assets/voz. */
    fun clip(id: String): String = if (id == "original") "inicio" else "inicio_$id"

    /** Las voces que trae el APK, según los archivos de assets/voz. */
    fun available(files: List<String>): List<String> = labels.keys.filter { "${clip(it)}.ogg" in files }

    /** Preferencia donde se guarda la voz elegida (v0.72.3: "voz3"; las de antes se ignoran). */
    const val PREF = "voz3"

    /**
     * La elegida si sigue estando; si no, la Comercial (v0.72.3, elegida por el autor para todo:
     * slogan, aviso de luces e indicaciones) o el Locutor AR o el locutor; si no, la original.
     */
    fun resolve(chosen: String?, available: List<String>): String = when {
        chosen != null && chosen in available -> chosen
        "comercial" in available -> "comercial"
        "ar_locutor" in available -> "ar_locutor"
        "locutor" in available -> "locutor"
        else -> "original"
    }

    /** Aviso de luces (v0.72.0): la frase con la voz elegida si el APK la trae; si no, la de siempre. */
    fun lightsClip(voice: String, files: List<String>): String =
        "luces_$voice".takeIf { "$it.ogg" in files } ?: "luces_apagadas"
}

/**
 * El sonido de inicio (puro, para los tests). v0.81.15: en vez de tres campanas, un acorde que
 * entra de a poco (re mayor con novena) y dos notas claras arriba, al estilo de los autos
 * actuales. Compuesto en el código: no copia el sonido de ninguna marca.
 */
object IntroChime {
    const val TOTAL_S = 2.6
    private const val FADE_S = 0.5

    /** Acorde de fondo: (frecuencia en Hz, volumen). Entra en [PAD_ATTACK_S] y se apaga desde [PAD_RELEASE_AT_S]. */
    private val PAD = listOf(146.83 to 0.9, 220.00 to 0.8, 293.66 to 0.7, 369.99 to 0.55, 659.25 to 0.3)
    private const val PAD_ATTACK_S = 0.55
    private const val PAD_RELEASE_AT_S = 1.2
    private const val PAD_RELEASE_S = 0.6
    private const val DETUNE = 0.004

    /** Notas claras: (frecuencia en Hz, comienzo en s, volumen). La y re agudos. */
    private val BELLS = listOf(Triple(880.00, 0.55, 0.55), Triple(1174.66, 0.85, 0.5))
    private const val BELL_DECAY_S = 0.55
    private const val BELL_ATTACK_S = 0.012

    /** Muestras de 16 bits, mono, con el pico a la mitad del máximo (sin saturar). */
    fun samples(rate: Int): ShortArray {
        val n = (rate * TOTAL_S).toInt()
        val buf = DoubleArray(n)
        for ((f, vol) in PAD) {
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val x = minOf(1.0, t / PAD_ATTACK_S)
                val attack = x * x * (3 - 2 * x)
                val release = if (t > PAD_RELEASE_AT_S) exp(-(t - PAD_RELEASE_AT_S) / PAD_RELEASE_S) else 1.0
                val w = 2 * PI * f * t
                // Dos voces apenas desafinadas: el acorde "respira" en vez de sonar a tono fijo
                buf[i] += vol * attack * release * 0.5 * (sin(w * (1 - DETUNE)) + sin(w * (1 + DETUNE)))
            }
        }
        for ((f, start, vol) in BELLS) {
            val from = (start * rate).toInt()
            for (i in from until n) {
                val t = (i - from).toDouble() / rate
                val env = vol * exp(-t / BELL_DECAY_S) * minOf(1.0, t / BELL_ATTACK_S)
                val w = 2 * PI * f * t
                buf[i] += env * (sin(w) + 0.25 * sin(2 * w))
            }
        }
        // Fundido final para que termine en silencio
        val fade = (FADE_S * rate).toInt()
        for (k in 0 until fade) buf[n - 1 - k] *= k.toDouble() / fade
        val peak = buf.fold(0.0) { m, v -> max(m, kotlin.math.abs(v)) }.takeIf { it > 0 } ?: 1.0
        val scale = 0.5 * Short.MAX_VALUE / peak
        return ShortArray(n) { (buf[it] * scale).toInt().toShort() }
    }
}

/** Reproduce el sonido de inicio y la frase grabada, según lo elegido. */
object IntroSound {
    enum class Mode(val label: String) { VOICE("voz y sonido"), CHIME("solo sonido"), OFF("apagado") }

    private const val RATE = 22_050
    /** No repetir si la pantalla se prende y apaga seguido. */
    private const val MIN_GAP_MS = 30_000L

    val mode = androidx.compose.runtime.mutableStateOf(Mode.CHIME)
    /** Voz elegida (id de [IntroVoices]). */
    val voice = androidx.compose.runtime.mutableStateOf("locutor")
    /** Voces que trae el APK. */
    val voices = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private var loaded = false
    /** Archivos de assets/voz (para el aviso de luces con la voz elegida). */
    @Volatile var assetFiles: List<String> = emptyList()
        private set
    private var lastAt = -MIN_GAP_MS

    /**
     * El modo guardado. v0.81.15: sin elección guardada suena solo el sonido (pedido del autor: el
     * slogan queda como opción, apagado por defecto). Pura.
     */
    fun savedMode(name: String?): Mode = Mode.entries.firstOrNull { it.name == name } ?: Mode.CHIME

    /** Voz y sonido → solo sonido → apagado → voz y sonido (pura). */
    fun next(m: Mode): Mode = Mode.entries[(m.ordinal + 1) % Mode.entries.size]

    fun label(): String = "Sonido de inicio: ${mode.value.label}" +
        if (mode.value == Mode.VOICE) " (${IntroVoices.labels[voice.value] ?: voice.value})" else ""

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences("inicio", Context.MODE_PRIVATE)
        val name = prefs.getString("sonido", null)
        mode.value = savedMode(name)
        val files = runCatching { context.assets.list("voz")?.toList() }.getOrNull().orEmpty()
        voices.value = IntroVoices.available(files)
        assetFiles = files
        // v0.72.3: clave nueva ("voz3"), así la elección vieja no tapa a la Comercial por defecto
        voice.value = IntroVoices.resolve(prefs.getString(IntroVoices.PREF, null), voices.value)
    }

    /** Elige [id] y lo hace escuchar ya (sonido y voz), sin importar el modo. */
    fun preview(context: Context, id: String) {
        load(context)
        voice.value = id
        context.getSharedPreferences("inicio", Context.MODE_PRIVATE).edit().putString(IntroVoices.PREF, id).apply()
        val app = context.applicationContext
        Thread({
            playChime()
            RecordedVoice.play(app, listOf(IntroVoices.clip(id)))
        }, "sonido-inicio-prueba").start()
    }

    fun cycle(context: Context) {
        load(context)
        mode.value = next(mode.value)
        context.getSharedPreferences("inicio", Context.MODE_PRIVATE).edit().putString("sonido", mode.value.name).apply()
    }

    /**
     * Cuando aparece la presentación. No suena con Android Auto o CarPlay conectados (el audio es
     * del celular).
     */
    fun onIntro(context: Context) {
        load(context)
        val now = SystemClock.elapsedRealtime()
        if (mode.value == Mode.OFF || now - lastAt < MIN_GAP_MS || Projection.sessionActive(context)) return
        lastAt = now
        val app = context.applicationContext
        val clips = if (mode.value == Mode.VOICE) listOf(IntroVoices.clip(voice.value)) else emptyList()
        Thread({
            playChime()
            if (clips.isNotEmpty()) RecordedVoice.play(app, clips)
        }, "sonido-inicio").start()
    }

    private fun playChime() {
        var track: AudioTrack? = null
        try {
            val pcm = IntroChime.samples(RATE)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((IntroChime.TOTAL_S * 1000).toLong() + 100)
        } catch (e: Exception) {
            AppLog.warn("Sonido de inicio: no se pudo reproducir", e)
        } finally {
            // AUD-062 (v0.67.1): se libera también si falla
            runCatching { track?.release() }
        }
    }
}
