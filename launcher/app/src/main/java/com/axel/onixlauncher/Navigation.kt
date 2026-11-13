package com.axel.onixlauncher

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateOf
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Navegación giro a giro propia (v0.37.0), sin Google Maps ni Waze: la ruta (TomTom desde v0.75.0; antes OSRM) trae las
 * maniobras; mientras se maneja, el GPS indica cuál es la próxima, a qué distancia está, cuándo
 * avisar por voz (TextToSpeech del equipo, funciona sin internet) y si hay que recalcular porque
 * el auto se salió del camino. Las funciones de cálculo y textos son puras, para los tests.
 */

/** Una maniobra de la ruta: dónde es, qué hay que hacer y cuánto se recorre después. */
data class Step(
    val type: String,
    val modifier: String,
    val name: String,
    val exit: Int,
    val lat: Double,
    val lon: Double,
    val distanceM: Double
) {
    val instruction: String get() = Guidance.instruction(type, modifier, name, exit)
}

/** Lo que muestra el cartel de navegación en cada momento. */
data class GuidanceState(
    val step: Step?,
    val toManeuverM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val arrived: Boolean,
    val offRoute: Boolean,
    val speak: String?,
    /** Lo mismo con voces grabadas (v0.61.0). */
    val speakClips: List<String> = emptyList()
)

/** Sigue una ruta con las posiciones del GPS. Una instancia por ruta. */
class Guidance(private val route: Route) {
    private var index = if (route.steps.size > 1) 1 else 0
    private var said300 = false
    private var saidNow = false
    private var offCount = 0
    private var arrived = false

    fun update(lat: Double, lon: Double): GuidanceState {
        val steps = route.steps
        if (steps.isEmpty()) return GuidanceState(null, 0.0, 0.0, 0.0, false, false, null)
        var speak: String? = null
        var clips: List<String> = emptyList()
        var step = steps[index]
        var dist = distance(lat, lon, step.lat, step.lon)
        // Pasada la maniobra (a menos de 20 m), sigue la próxima
        if (dist < 20 && step.type != "arrive" && index < steps.size - 1) {
            index++
            said300 = false
            saidNow = false
            step = steps[index]
            dist = distance(lat, lon, step.lat, step.lon)
        }
        if (step.type == "arrive" && dist < 30) {
            if (!arrived) { speak = "Llegaste a destino"; clips = listOf("llegaste") }
            arrived = true
        } else if (dist <= 60 && !saidNow) {
            speak = step.instruction
            clips = VoiceClips.step(step.type, step.modifier, step.exit, null)
            saidNow = true
            said300 = true
        } else if (dist <= 300 && !said300) {
            speak = "En ${roundDistance(dist)}, ${step.instruction.replaceFirstChar { it.lowercase() }}"
            clips = VoiceClips.step(step.type, step.modifier, step.exit, dist)
            said300 = true
        }
        val remaining = dist + steps.drop(index).sumOf { it.distanceM }
        val off = distanceToLine(lat, lon, route.points) > 50
        offCount = if (off) offCount + 1 else 0
        val secs = if (route.distanceM > 0) route.durationS * remaining / route.distanceM else 0.0
        return GuidanceState(step, dist, remaining, secs, arrived, offCount >= 3, speak, clips)
    }

    companion object {
        /** Distancia en metros entre dos puntos (haversine). */
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        /** Distancia en metros de un punto a la línea de la ruta (proyección plana local). */
        fun distanceToLine(lat: Double, lon: Double, pts: List<Pair<Double, Double>>): Double {
            if (pts.isEmpty()) return 0.0
            if (pts.size == 1) return distance(lat, lon, pts[0].first, pts[0].second)
            val kx = 111_320.0 * cos(Math.toRadians(lat))
            val ky = 110_540.0
            var best = Double.MAX_VALUE
            for (i in 0 until pts.size - 1) {
                val ax = (pts[i].second - lon) * kx; val ay = (pts[i].first - lat) * ky
                val bx = (pts[i + 1].second - lon) * kx; val by = (pts[i + 1].first - lat) * ky
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val px = ax + t * dx; val py = ay + t * dy
                best = minOf(best, sqrt(px * px + py * py))
            }
            return best
        }

        /** "300 metros", "1,5 kilómetros". */
        fun roundDistance(m: Double): String = when {
            m >= 1000 -> String.format(Locale("es", "AR"), "%.1f kilómetros", m / 1000)
            m >= 100 -> "${(Math.round(m / 50) * 50)} metros"
            else -> "${(Math.round(m / 10) * 10).coerceAtLeast(10)} metros"
        }

        /** Texto corto para el cartel: "200 m", "1,2 km". */
        fun shortDistance(m: Double): String =
            if (m >= 1000) String.format(Locale("es", "AR"), "%.1f km", m / 1000) else "${(Math.round(m / 10) * 10)} m"

        /** Indicación en castellano rioplatense a partir de la maniobra (nombres de OSRM; TomTom se traduce en NavApi.maneuver). */
        fun instruction(type: String, modifier: String, name: String, exit: Int): String {
            val street = name.trim()
            val turn = when (modifier) {
                "left" -> "Girá a la izquierda"
                "right" -> "Girá a la derecha"
                "slight left" -> "Doblá levemente a la izquierda"
                "slight right" -> "Doblá levemente a la derecha"
                "sharp left" -> "Girá cerrado a la izquierda"
                "sharp right" -> "Girá cerrado a la derecha"
                "uturn" -> "Hacé un giro en U"
                else -> "Seguí derecho"
            }
            return when (type) {
                "depart" -> if (street.isNotEmpty()) "Salí por $street" else "Salí hacia el destino"
                "arrive" -> "Llegaste a destino"
                "waypoint" -> "Llegaste a la parada"
                "roundabout", "rotary", "roundabout turn" ->
                    "En la rotonda, tomá la " + (if (exit > 0) "${exit}.ª salida" else "salida") + (if (street.isNotEmpty()) " hacia $street" else "")
                "continue", "new name" -> if (modifier == "straight" || modifier.isEmpty()) {
                    if (street.isNotEmpty()) "Seguí por $street" else "Seguí derecho"
                } else turn + (if (street.isNotEmpty()) " por $street" else "")
                "fork" -> (if (modifier.contains("left")) "En la bifurcación, mantenete a la izquierda" else "En la bifurcación, mantenete a la derecha") +
                    (if (street.isNotEmpty()) " hacia $street" else "")
                "merge" -> "Incorporate" + (if (street.isNotEmpty()) " a $street" else "")
                else -> turn + (if (street.isNotEmpty() && modifier != "straight") " en $street" else if (street.isNotEmpty()) " por $street" else "")
            }
        }
    }
}

/** Voz de la navegación con el TextToSpeech del equipo (castellano). */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Para la prueba completa: "sin iniciar", "iniciando", "lista (es-AR)", "lista (es-ES)" o "sin voz en el equipo". */
    val state = mutableStateOf("sin iniciar")

    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        if (tts != null) return
        state.value = "iniciando"
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val ok = t.setLanguage(Locale("es", "AR"))
                if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale("es", "ES"))
                    state.value = "lista (es-ES)"
                } else {
                    state.value = "lista (es-AR)"
                }
                ready = true
            } else {
                state.value = "sin voz en el equipo"
                AppLog.warn("Navegación: el equipo no tiene voz (TextToSpeech)")
            }
        }
    }

    /**
     * [always] = avisos de seguridad (v0.50.0): hablan aunque la voz de la navegación esté apagada.
     * [clips] = las mismas palabras grabadas (v0.61.0): se usan si el equipo no tiene TextToSpeech.
     */
    /** Corta lo que se esté diciendo (al pasar a silencio). */
    fun stopTalking() {
        runCatching { tts?.stop() }
        RecordedVoice.stop()
    }

    fun say(text: String, always: Boolean = false, clips: List<String> = emptyList()) {
        // v0.78.0: modo de sonido (todo / solo avisos / silencio)
        val m = AlertPrefs.mode.value
        if (if (always) !AlertPrefs.alertSound(m) else !AlertPrefs.guidanceVoice(m)) return
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, if (always) "aviso" else "nav")
        } else {
            app?.let { RecordedVoice.play(it, clips) }
        }
    }
}

/** Destino pedido desde otra pantalla (Casa / Trabajo en Inicio): el mapa lo toma al aparecer. */
object NavRequest {
    val pending = mutableStateOf<Place?>(null)
    /** v0.76.0: arrancar la guía apenas esté la ruta (destino confirmado desde el celular). */
    @Volatile var startNow = false
    /** v0.77.0: buscar una categoría al abrir el mapa (nafta baja → combustible). */
    val category = mutableStateOf<PlaceCategory?>(null)
    /** v0.77.0: abrir Mis lugares al llegar al mapa (Casa o Trabajo sin guardar, desde Inicio). */
    val editPlaces = mutableStateOf(false)
}
