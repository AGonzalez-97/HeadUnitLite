package com.axel.onixlauncher

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Avisos de seguridad (v0.50.0), arriba de cualquier pantalla del launcher:
 *  - Luces apagadas andando: con la señal de luces de la radio NWD (RadioIll), si el auto se mueve
 *    (desde 2 km/h), de día y de noche. v0.79.7, pedido del autor: acá es obligatorio andar con las
 *    luces prendidas siempre, a cualquier velocidad (antes: desde 20 km/h de noche y 60 km/h de día).
 *  - Velocidad (v0.63.0): pasando el máximo que eligió el conductor en Mi auto.
 *  - Alertas del motor del OBD2 (ObdCodes.kt): recalentado, batería que no carga, voltaje alto,
 *    batería baja.
 * Cada aviso muestra un cartel, hace un pitido y lo dice con la voz del equipo (si la radio tiene
 * "Servicios de voz de Google"; si no, solo el pitido).
 */

/** Cuándo avisar que las luces están apagadas (funciones puras, para los tests). */
object LightsReminder {
    /**
     * Desde esta velocidad el auto "anda" (v0.79.7: 2 km/h, antes 20). Sirve para dejar afuera el ruido
     * del GPS con el auto parado, que da 0 o 1 km/h; no hay otra regla de velocidad ni de hora del día.
     */
    const val MIN_KMH = 2
    /** Tiene que durar esto para avisar (no avisa por un instante). v0.79.7: 5 s; con 15 s, a paso de hombre ni se llegaba. */
    const val HOLD_MS = 5_000L
    /** No repite el aviso antes de esto, aunque se pare en un semáforo y vuelva a arrancar. */
    const val REPEAT_MS = 10 * 60_000L
    const val SPEECH = "Atención: las luces están apagadas. Encendelas para un viaje más seguro."

    /**
     * Si hay que avisar. [lightsKnown] = la radio informa las luces (sin esa señal no se sabe y
     * no se avisa); [lightsOn] = luces prendidas según la radio.
     */
    fun needed(speedKmh: Int?, lightsKnown: Boolean, lightsOn: Boolean): Boolean =
        lightsKnown && !lightsOn && speedKmh != null && speedKmh >= MIN_KMH
}

/**
 * Aviso de velocidad (v0.63.0): el conductor elige un máximo en Mi auto (apagado de fábrica).
 * Si lo pasa por más de [TOLERANCE_KMH] durante [HOLD_MS], cartel y voz; repite cada [REPEAT_MS].
 */
object SpeedWarning {
    const val TOLERANCE_KMH = 3
    const val HOLD_MS = 5_000L
    const val REPEAT_MS = 2 * 60_000L
    /** Máximos para elegir; null = apagado. */
    val CHOICES = listOf(null, 60, 80, 100, 110, 120, 130)

    val limit = androidx.compose.runtime.mutableStateOf<Int?>(null)
    private var loaded = false

    fun needed(speedKmh: Int?, limitKmh: Int?): Boolean =
        limitKmh != null && speedKmh != null && speedKmh > limitKmh + TOLERANCE_KMH

    fun next(current: Int?): Int? = CHOICES[(CHOICES.indexOf(current) + 1) % CHOICES.size]

    fun label(limitKmh: Int?): String = "Aviso de velocidad: " + (limitKmh?.toString() ?: "no")

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val v = context.getSharedPreferences("velocidad", Context.MODE_PRIVATE).getInt("maximo", 0)
        limit.value = v.takeIf { it in CHOICES }
    }

    fun cycle(context: Context) {
        load(context)
        limit.value = next(limit.value)
        context.getSharedPreferences("velocidad", Context.MODE_PRIVATE).edit().putInt("maximo", limit.value ?: 0).apply()
    }
}

/**
 * Dispara un aviso cuando una condición dura [holdMs] y lo repite cada [repeatMs] mientras siga.
 * El último aviso se recuerda aunque la condición se corte, para no repetir en cada semáforo.
 */
class RepeatWatch(private val holdMs: Long, private val repeatMs: Long) {
    private var since = -1L
    private var lastFired = -1L

    /** true = avisar ahora. */
    fun update(active: Boolean, nowMs: Long): Boolean {
        if (!active) {
            since = -1L
            return false
        }
        if (since < 0) since = nowMs
        if (nowMs - since < holdMs) return false
        if (lastFired >= 0 && nowMs - lastFired < repeatMs) return false
        lastFired = nowMs
        return true
    }
}

/** Sonido de la navegación y los avisos (v0.78.0), como Google Maps y Waze. */
enum class SoundMode(val label: String) { ALL("🔊 Todo"), ALERTS("⚠ Solo avisos"), MUTE("🔇 Silencio") }

/**
 * Preferencias de avisos (v0.78.0, pedido del autor): el modo de sonido (todo / solo avisos /
 * silencio) y cada aviso prendido o apagado (nafta baja, luces, velocidad, motor). Un aviso
 * apagado no muestra cartel ni suena; en silencio los carteles se ven pero no suena nada.
 */
object AlertPrefs {
    val mode = androidx.compose.runtime.mutableStateOf(SoundMode.ALL)
    val fuel = androidx.compose.runtime.mutableStateOf(true)
    val lights = androidx.compose.runtime.mutableStateOf(true)
    val speed = androidx.compose.runtime.mutableStateOf(true)
    val engine = androidx.compose.runtime.mutableStateOf(true)
    private var loaded = false

    fun next(m: SoundMode): SoundMode = SoundMode.entries[(m.ordinal + 1) % SoundMode.entries.size]
    /** Indicaciones de la navegación ("En 200 metros, girá…"). */
    fun guidanceVoice(m: SoundMode): Boolean = m == SoundMode.ALL
    /** Pitido y voz de los avisos de seguridad. */
    fun alertSound(m: SoundMode): Boolean = m != SoundMode.MUTE
    fun parseMode(s: String?): SoundMode = SoundMode.entries.firstOrNull { it.name == s } ?: SoundMode.ALL

    private fun prefs(c: Context) = c.getSharedPreferences("avisos", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        mode.value = parseMode(p.getString("sonido", null))
        fuel.value = p.getBoolean("nafta", true)
        lights.value = p.getBoolean("luces", true)
        speed.value = p.getBoolean("velocidad", true)
        engine.value = p.getBoolean("motor", true)
    }

    fun save(c: Context) {
        prefs(c).edit().putString("sonido", mode.value.name).putBoolean("nafta", fuel.value).putBoolean("luces", lights.value)
            .putBoolean("velocidad", speed.value).putBoolean("motor", engine.value).apply()
    }

    fun cycleMode(c: Context) { mode.value = next(mode.value); save(c) }
}

/** Pitido de aviso por los parlantes del auto y, después, la voz (nada en silencio, v0.78.0). */
private suspend fun warn(context: Context, speech: String, clip: String) {
    AlertPrefs.load(context)
    if (!AlertPrefs.alertSound(AlertPrefs.mode.value)) {
        AppLog.info("Aviso en silencio: $speech")
        return
    }
    withContext(Dispatchers.Default) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            delay(700)
            tone.release()
        } catch (e: Exception) {
            AppLog.warn("Aviso: no se pudo hacer el pitido", e)
        }
    }
    Voice.init(context)
    // v0.72.0: la frase con la voz elegida (ElevenLabs) suena aunque haya TextToSpeech
    if (clip.startsWith("luces_") && clip != "luces_apagadas") RecordedVoice.play(context.applicationContext, listOf(clip))
    else Voice.say(speech, always = true, clips = listOf(clip))
    AppLog.info("Aviso: $speech")
}

/** Cartel de los avisos de seguridad. Se pone arriba de las pantallas, dentro del lienzo de 1024 × 600. */
@Composable
fun SafetyOverlay(speed: Int?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Voice.init(context) }

    // Luces apagadas andando y velocidad (v0.63.0). AUD-058 (v0.64.1): se revisan cada segundo con
    // la velocidad en vivo (GpsSpeed.live). Antes usaban la de la pantalla, que se congela con
    // Android Auto o CarPlay encima: el aviso podía repetirse con el auto ya parado.
    var lightsWarning by remember { mutableStateOf(false) }
    var speedWarning by remember { mutableStateOf(false) }
    val watch = remember { RepeatWatch(LightsReminder.HOLD_MS, LightsReminder.REPEAT_MS) }
    val speedWatch = remember { RepeatWatch(SpeedWarning.HOLD_MS, SpeedWarning.REPEAT_MS) }
    var fuelOfferAt by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        AlertPrefs.load(context)
        SpeedWarning.load(context)
        while (true) {
            val now = System.currentTimeMillis()
            val live = GpsSpeed.live()
            // Nafta baja (v0.77.0): ofrece buscar estación, una vez cada 30 min, andando
            val fuel = ObdState.data.value?.fuelPct
            if (AlertPrefs.fuel.value && FuelWatch.shouldOffer(fuel, (live ?: 0) >= 10, fuelOfferAt, now)) {
                fuelOfferAt = now
                FuelWatch.offer.value = fuel
                warn(context, "Atención: queda poca nafta. ¿Busco una estación en el camino?", "nafta_baja")
            }
            val needLights = AlertPrefs.lights.value && LightsReminder.needed(live, RadioIll.present.value, RadioIll.on.value)
            if (!needLights) lightsWarning = false
            if (watch.update(needLights, now)) {
                lightsWarning = true
                IntroSound.load(context)   // v0.72.0: el aviso con la voz elegida en Diagnóstico
                warn(context, LightsReminder.SPEECH, IntroVoices.lightsClip(IntroSound.voice.value, IntroSound.assetFiles))
            }
            val max = SpeedWarning.limit.value
            val fast = AlertPrefs.speed.value && SpeedWarning.needed(live, max)
            if (!fast) speedWarning = false
            if (speedWatch.update(fast, System.currentTimeMillis())) {
                speedWarning = true
                warn(context, "Atención: vas a más de $max kilómetros por hora.", "velocidad_alta")
            }
            delay(1000)
        }
    }
    val limit = SpeedWarning.limit.value

    // Alertas del motor: avisa una vez cada vez que aparece una nueva
    val engine = ObdState.alert.value?.takeIf { AlertPrefs.engine.value }
    var engineClosed by remember { mutableStateOf<EngineAlert?>(null) }
    LaunchedEffect(engine) {
        if (engine == null) {
            engineClosed = null
        } else {
            warn(context, "Atención: ${engine.title}. ${engine.advice}.", VoiceClips.alert(engine))
        }
    }

    // Mantenimiento de Mi auto (v0.79.0): lo más urgente, una vez por día, sin sonido
    var reminder by remember { mutableStateOf<MaintItem?>(null) }
    LaunchedEffect(Unit) {
        CarCare.load(context)
        delay(8000)                      // después de la presentación
        reminder = CarCare.noticeToday(context)
    }

    val tripDone = CarCare.lastTrip.value
    LaunchedEffect(tripDone) {
        if (tripDone != null) { delay(20_000); CarCare.lastTrip.value = null }
    }
    when {
        engine != null && engine != engineClosed -> SafetyBanner(
            Color(0xF2B3261E), engine.title, engine.advice, onClose = { engineClosed = engine }
        )
        speedWarning -> SafetyBanner(
            Color(0xF2C2410C), "Velocidad alta", "Vas a ${speed ?: "--"} km/h · máximo $limit", onClose = { speedWarning = false }
        )
        lightsWarning -> SafetyBanner(
            Color(0xF2C77700), "Luces apagadas", "Encendelas para un viaje más seguro", onClose = { lightsWarning = false }
        )
        reminder != null -> reminder?.let { r ->
            SafetyBanner(
                Color(0xF21E4FB8), "Mantenimiento: ${r.name}",
                Maintenance.describe(r, CarCare.carKm(), Reminders.today()) + " · Mi auto en el dock",
                onClose = { CarCare.noticeShown(context); reminder = null }
            )
        }
        // Fin de viaje (v0.79.0): resumen corto al apagar el auto o a los 5 min parado
        tripDone != null -> tripDone?.let { t ->
            SafetyBanner(Color(0xF21E4FB8), "Viaje terminado", t.short(), onClose = { CarCare.lastTrip.value = null })
        }
    }
}

@Composable
internal fun SafetyBanner(color: Color, title: String, advice: String, onClose: () -> Unit) {
    Box(Modifier.offset(202.dp, 14.dp).width(620.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color)
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text("!", color = color.copy(alpha = 1f), fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(advice, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
            }
            Text("Tocá para cerrar", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
        }
    }
}
