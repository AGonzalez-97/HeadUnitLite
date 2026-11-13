package com.axel.onixlauncher

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Temas festivos (v0.65.0; v0.66.0: decoran el auto, sin saludo): según la fecha de Argentina, el
 * Onix de Inicio se decora (nieve y luces en Navidad, bandera en fechas patrias, calabazas en
 * Halloween…) y el cielo suma un detalle. Dibujado en el código (sin imágenes de terceros). Con el
 * modo liviano, la decoración queda quieta.
 * Se apaga o se prueba cada tema en la ventana del saludo (tocando "Hola, …").
 */

/** Cómo se decora cada fiesta. */
enum class Decor { SNOW, FIREWORKS, HEARTS, CONFETTI, PATRIA, HALLOWEEN, PETALS }

/** Fiestas de Argentina: nombre (para el ajuste y el informe) y decoración del auto. */
enum class Holiday(val label: String, val decor: Decor) {
    NAVIDAD("Navidad", Decor.SNOW),
    ANIO_NUEVO("Año Nuevo", Decor.FIREWORKS),
    REYES("Reyes", Decor.CONFETTI),
    SAN_VALENTIN("San Valentín", Decor.HEARTS),
    CARNAVAL("Carnaval", Decor.CONFETTI),
    PASCUA("Pascua", Decor.PETALS),
    MALVINAS("2 de Abril (Malvinas)", Decor.PATRIA),
    PATRIA_MAYO("25 de Mayo", Decor.PATRIA),
    DIA_DEL_PADRE("Día del Padre", Decor.HEARTS),
    BANDERA("Día de la Bandera", Decor.PATRIA),
    INDEPENDENCIA("9 de Julio", Decor.PATRIA),
    DIA_DEL_AMIGO("Día del Amigo", Decor.CONFETTI),
    SAN_MARTIN("17 de Agosto (San Martín)", Decor.PATRIA),
    PRIMAVERA("Primavera", Decor.PETALS),
    DIA_DE_LA_MADRE("Día de la Madre", Decor.HEARTS),
    HALLOWEEN("Halloween", Decor.HALLOWEEN),
    SOBERANIA("Soberanía Nacional", Decor.PATRIA)
}

/** Qué fiesta es hoy (funciones puras, para los tests). */
object Festive {
    /** Encendido (ajuste en la ventana del saludo). */
    val enabled = mutableStateOf(true)
    /** Tema elegido para probar (null = el de la fecha). Se borra al cerrar la app. */
    val preview = mutableStateOf<Holiday?>(null)
    private var loaded = false

    /**
     * [n] puntos repartidos parejo a lo largo de un contorno (lista de puntos, en fracciones de la
     * caja del auto): el primero y el último son los extremos (pura, para los tests).
     */
    fun along(line: List<Pair<Float, Float>>, n: Int): List<Pair<Float, Float>> {
        if (line.size < 2 || n < 2) return line.take(1).let { if (n >= 1) it else emptyList() }
        val seg = line.zipWithNext { a, b -> kotlin.math.hypot(b.first - a.first, b.second - a.second) }
        val total = seg.sum()
        return List(n) { k ->
            var d = total * k / (n - 1)
            var i = 0
            while (i < seg.size - 1 && d > seg[i]) { d -= seg[i]; i++ }
            val f = if (seg[i] > 0f) (d / seg[i]).coerceIn(0f, 1f) else 0f
            val (ax, ay) = line[i]
            val (bx, by) = line[i + 1]
            (ax + (bx - ax) * f) to (ay + (by - ay) * f)
        }
    }

    /** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher), como (mes, día). */
    fun easter(year: Int): Pair<Int, Int> {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return month to day
    }

    /** Día del año (1..366) de una fecha, con el calendario gregoriano. */
    private fun dayOfYear(y: Int, m: Int, d: Int): Int =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.get(Calendar.DAY_OF_YEAR)

    /** El n-ésimo domingo de un mes, como día del mes. */
    private fun nthSunday(y: Int, m: Int, n: Int): Int {
        val first = Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }.get(Calendar.DAY_OF_WEEK)
        val firstSunday = 1 + (Calendar.SUNDAY - first + 7) % 7
        return firstSunday + 7 * (n - 1)
    }

    /** La fiesta de esa fecha ([m] de 1 a 12), o null. */
    fun on(y: Int, m: Int, d: Int): Holiday? {
        val (em, ed) = easter(y)
        val diff = dayOfYear(y, m, d) - dayOfYear(y, em, ed)
        return when {
            // Navidad: del 8 de diciembre (Día de la Virgen / armado del arbolito) al 26 de diciembre
            m == 12 && d in 8..26 -> Holiday.NAVIDAD
            // Año Nuevo: del 27 de diciembre al 2 de enero
            (m == 12 && d in 27..31) || (m == 1 && d in 1..2) -> Holiday.ANIO_NUEVO
            // Reyes: 5 y 6 de enero
            m == 1 && d in 5..6 -> Holiday.REYES
            // San Valentín: 13 y 14 de febrero
            m == 2 && d in 13..14 -> Holiday.SAN_VALENTIN
            // Carnaval: fin de semana largo (4 días antes de Pascua)
            diff in -49..-46 -> Holiday.CARNAVAL
            // Malvinas: 1 y 2 de abril
            m == 4 && d in 1..2 -> Holiday.MALVINAS
            // Pascua: de Viernes Santo a Domingo de Pascua
            diff in -2..0 -> Holiday.PASCUA
            // Semana de Mayo: del 18 al 25 de mayo (Semana de Mayo histórica)
            m == 5 && d in 18..25 -> Holiday.PATRIA_MAYO
            // Día del Padre: fin de semana del día del padre
            m == 6 && d in (nthSunday(y, 6, 3) - 1)..nthSunday(y, 6, 3) -> Holiday.DIA_DEL_PADRE
            // Día de la Bandera: semana del 15 al 20 de junio
            m == 6 && d in 15..20 -> Holiday.BANDERA
            // Semana de la Independencia: del 4 al 9 de julio
            m == 7 && d in 4..9 -> Holiday.INDEPENDENCIA
            // Día del Amigo: 19 y 20 de julio
            m == 7 && d in 19..20 -> Holiday.DIA_DEL_AMIGO
            // San Martín: 16 y 17 de agosto
            m == 8 && d in 16..17 -> Holiday.SAN_MARTIN
            // Primavera: del 20 al 23 de septiembre
            m == 9 && d in 20..23 -> Holiday.PRIMAVERA
            // Día de la Madre: fin de semana de la madre
            m == 10 && d in (nthSunday(y, 10, 3) - 1)..nthSunday(y, 10, 3) -> Holiday.DIA_DE_LA_MADRE
            // Halloween: semana de Halloween del 24 al 31 de octubre
            m == 10 && d in 24..31 -> Holiday.HALLOWEEN
            // Soberanía Nacional: 19 y 20 de noviembre
            m == 11 && d in 19..20 -> Holiday.SOBERANIA
            else -> null
        }
    }

    /** La fiesta que se muestra ahora: la de prueba, o la de hoy si los temas están prendidos. */
    fun current(now: Calendar): Holiday? = preview.value ?: if (!enabled.value) null
        else on(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))

    fun label(): String = "Temas festivos: " + if (enabled.value) "sí" else "no"

    /** Para probar: ninguno → Navidad → … → Soberanía → ninguno. */
    fun nextPreview(): String {
        val all = Holiday.entries
        preview.value = preview.value.let { p -> if (p == null) all.first() else all.getOrNull(p.ordinal + 1) }
        return "Ver tema: " + (preview.value?.label ?: "el de hoy")
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        enabled.value = context.getSharedPreferences("fiestas", Context.MODE_PRIVATE).getBoolean("prendido", true)
    }

    fun toggle(context: Context) {
        load(context)
        enabled.value = !enabled.value
        context.getSharedPreferences("fiestas", Context.MODE_PRIVATE).edit().putBoolean("prendido", enabled.value).apply()
    }
}

/** Decoración de la fiesta sobre el fondo de Inicio. [animate] = false: quieta (modo liviano, capturas). */
@Composable
fun FestiveLayer(
    holiday: Holiday,
    animate: Boolean,
    ambient: Ambient = Ambient.DIA,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val t = if (animate) {
        val inf = rememberInfiniteTransition(label = "fiesta")
        val v by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0.35f
    // Posiciones fijas al azar (con semilla): no cambian en cada cuadro
    val seeds = remember(holiday) { val r = java.util.Random(holiday.ordinal.toLong()); List(36) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(modifier) {
        val isNight = ambient.isNight
        val isSunset = ambient.timeOfDay == Ambient.ATARDECER
        val isRaining = ambient.raining
        val particleMult = if (isRaining) 0.55f else 1.0f

        when (holiday.decor) {
            // Copos con profundidad: los lejanos, chicos y tenues; los cercanos, grandes y nítidos. Si llueve, atenuados.
            Decor.SNOW -> {
                lights(t, ambient)
                falling(seeds, t, 1f) { p, s ->
                    val snowAlpha = (if (isNight) 0.25f + 0.45f * s else 0.35f + 0.55f * s) * particleMult
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = snowAlpha), Color.Transparent), p, 3f + 5f * s), 3f + 5f * s, p)
                }
            }
            Decor.CONFETTI -> falling(seeds, t, 1.4f) { p, s ->
                val c = CONFETTI[(s * CONFETTI.size).toInt().coerceAtMost(CONFETTI.size - 1)]
                val turn = kotlin.math.abs(sin(2 * PI.toFloat() * (t * 3 + s)))
                val alpha = (0.55f + 0.35f * s) * particleMult * (if (isNight) 0.75f else 1.0f)
                drawRect(c.copy(alpha = alpha), p, Size(3f + 6f * turn, 4f + 6f * s))
            }
            Decor.PETALS -> falling(seeds, t, 0.7f) { p, s ->
                val alpha = (0.45f + 0.4f * s) * particleMult * (if (isNight) 0.70f else 1.0f)
                drawOval(Color(0xFFF7A8C4).copy(alpha = alpha), p, Size(8f + 6f * s, 5f + 3f * s))
            }
            Decor.HEARTS -> rising(seeds, t) { p, s ->
                val alpha = (0.35f + 0.35f * s) * particleMult * (if (isNight) 0.70f else 1.0f)
                heart(p, 6f + 8f * s, Color(0xFFFF4D6D).copy(alpha = alpha))
            }
            Decor.FIREWORKS -> fireworks(seeds, t, ambient)
            Decor.PATRIA -> patriaSky(ambient)
            Decor.HALLOWEEN -> halloween(seeds, t, ambient)
        }
    }
}

private val CONFETTI = listOf(Color(0xFFFFD23F), Color(0xFF4CB0FF), Color(0xFFFF6B6B), Color(0xFF5BD68A), Color(0xFFC77DFF))

/** Partículas que caen en la mitad de arriba (sobre el cielo, sin tapar los paneles). */
private fun DrawScope.falling(seeds: List<Triple<Float, Float, Float>>, t: Float, speed: Float, draw: DrawScope.(Offset, Float) -> Unit) {
    for ((x, y0, s) in seeds) {
        val y = ((y0 + t * speed * (0.5f + s)) % 1f) * size.height * 0.55f
        val sway = sin((t * 6f + x * 10f) * PI.toFloat()) * 8f
        draw(Offset(x * size.width + sway, y), s)
    }
}

private fun DrawScope.rising(seeds: List<Triple<Float, Float, Float>>, t: Float, draw: DrawScope.(Offset, Float) -> Unit) {
    for ((x, y0, s) in seeds.take(14)) {
        val y = (1f - (y0 + t * (0.4f + s * 0.4f)) % 1f) * size.height * 0.55f
        draw(Offset(x * size.width, y), s)
    }
}

/** Guirnalda de luces de colores arriba (Navidad). De noche y atardecer brillan con halo; de día son tenues. */
private fun DrawScope.lights(t: Float, ambient: Ambient) {
    val colors = listOf(Color(0xFFFF5A5A), Color(0xFF5BD68A), Color(0xFFFFD23F), Color(0xFF4CB0FF))
    val n = 24
    val isNight = ambient.isNight
    val isSunset = ambient.timeOfDay == Ambient.ATARDECER
    val glowMult = if (isNight) 1.25f else if (isSunset) 0.95f else 0.45f
    val baseAlphaOn = if (isNight) 0.98f else if (isSunset) 0.88f else 0.70f
    val baseAlphaOff = if (isNight) 0.38f else 0.22f

    for (i in 0 until n) {
        val x = size.width * (i + 0.5f) / n
        val y = 8f + 14f * sin(PI.toFloat() * ((i % 6) / 6f))
        val on = ((t * 16).toInt() + i) % 3 != 0
        val c = Offset(x, y)
        val color = colors[i % colors.size]
        if (on && glowMult > 0.5f) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * glowMult), Color.Transparent), c, 14f), 14f, c)
        }
        drawCircle(color.copy(alpha = if (on) baseAlphaOn else baseAlphaOff), 5f, c)
    }
}

private fun DrawScope.heart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.4f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.2f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.6f, c.y - r * 1.2f, c.x + r * 1.4f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
}

/** Fuegos artificiales en el cielo (Año Nuevo): de noche/atardecer brillan con estelas y resplandor; de día son muy sutiles. */
private fun DrawScope.fireworks(seeds: List<Triple<Float, Float, Float>>, t: Float, ambient: Ambient) {
    val isNight = ambient.isNight
    val isSunset = ambient.timeOfDay == Ambient.ATARDECER
    val isRaining = ambient.raining
    val glowFactor = if (isNight) 1.0f else if (isSunset) 0.75f else 0.28f
    val sparkFactor = (if (isNight) 1.0f else if (isSunset) 0.85f else 0.40f) * (if (isRaining) 0.80f else 1.0f)

    for ((k, sd) in seeds.take(4).withIndex()) {
        val phase = (t * 2f + k / 4f) % 1f
        val c = Offset(size.width * (0.15f + 0.7f * sd.first), size.height * (0.08f + 0.25f * sd.second))
        val fade = (1f - phase).coerceIn(0f, 1f)
        val base = CONFETTI[k % CONFETTI.size]
        // Resplandor del estallido, fuerte al principio y modulado por ambiente
        val glowR = (40f + 90f * phase) * (if (isNight) 1.15f else 0.9f)
        drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.30f * fade * fade * glowFactor), Color.Transparent), c, glowR), glowR, c)
        val r = 20f + 80f * phase
        val drop = 30f * phase * phase   // la gravedad baja las chispas
        for (j in 0 until 16) {
            val a = 2 * PI * j / 16
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val tip = Offset(c.x + r * dx, c.y + r * dy + drop)
            val tail = Offset(c.x + r * 0.7f * dx, c.y + r * 0.7f * dy + drop * 0.6f)
            drawLine(base.copy(alpha = 0.55f * fade * sparkFactor), tail, tip, strokeWidth = 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.85f * fade * sparkFactor), 2.2f, tip)
        }
    }
}

/** Halloween: cielo anaranjado al ras, murciélagos que cruzan y niebla baja sobre el asfalto. */
private fun DrawScope.halloween(seeds: List<Triple<Float, Float, Float>>, t: Float, ambient: Ambient) {
    val isNight = ambient.isNight
    val isSunset = ambient.timeOfDay == Ambient.ATARDECER
    val isRaining = ambient.raining

    val skyOrange = if (isNight) Color(0x38E66A00) else if (isSunset) Color(0x45FF7A00) else Color(0x20FF8C1A)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, skyOrange), startY = size.height * 0.35f, endY = size.height * 0.7f))

    // Niebla: bandas suaves que se desplazan despacio sobre el pavimento
    val fogColor = when {
        isRaining -> Color(0x44CCD5E2)   // más densa con lluvia / asfalto mojado
        isNight -> Color(0x33D8DEE8)     // reflejos nocturnos suaves
        isSunset -> Color(0x28E2D5CC)    // teñida por el crepúsculo
        else -> Color(0x18D8DEE8)        // tenue bajo el sol
    }
    for (k in 0 until 4) {
        val cx = ((k * 0.31f + t * (0.05f + 0.02f * k)) % 1.3f - 0.15f) * size.width
        val cy = size.height * (0.70f + 0.04f * k)
        val rx = size.width * 0.35f
        scale(1f, 0.18f, pivot = Offset(cx, cy)) {
            drawCircle(Brush.radialGradient(listOf(fogColor, Color.Transparent), Offset(cx, cy), rx), rx, Offset(cx, cy))
        }
    }
    val batColor = if (isNight) Color(0xEE0D0614) else Color(0xCC120A1A)
    for ((k, sd) in seeds.take(5).withIndex()) {
        val x = ((sd.first + t * (0.5f + sd.third)) % 1f) * size.width
        val y = size.height * (0.06f + 0.22f * sd.second)
        val flap = if (((t * 40).toInt() + k) % 2 == 0) 6f else -4f
        val p = Path().apply {
            moveTo(x - 18f, y - flap); lineTo(x - 6f, y); lineTo(x, y - 5f); lineTo(x + 6f, y); lineTo(x + 18f, y - flap)
            lineTo(x + 8f, y + 6f); lineTo(x, y + 2f); lineTo(x - 8f, y + 6f); close()
        }
        drawPath(p, batColor)
    }
}

/** Fechas patrias: franja celeste y blanca muy suave en el cielo, con un Sol de Mayo tenue adaptado a la hora. */
private fun DrawScope.patriaSky(ambient: Ambient) {
    val band = size.height * 0.3f
    val isNight = ambient.isNight
    val isSunset = ambient.timeOfDay == Ambient.ATARDECER
    val isRaining = ambient.raining

    val (celeste, blanco, solColor) = when {
        isNight -> Triple(Color(0x2235557A), Color(0x14D0D8E4), Color(0x2DF4B01E))
        isSunset -> Triple(Color(0x2C5585A8), Color(0x18F6E8DC), Color(0x40F29E1B))
        isRaining -> Triple(Color(0x226490BA), Color(0x14E0E8F0), Color(0x28E0A020))
        else -> Triple(Color(0x3374ACDF), Color(0x1AFFFFFF), Color(0x40F6B40E))
    }

    drawRect(Brush.verticalGradient(
        listOf(celeste, blanco, celeste, Color.Transparent),
        startY = 0f, endY = band * 1.3f
    ), size = Size(size.width, band * 1.3f))
    val sun = Offset(size.width * 0.5f, band * 0.42f)
    drawCircle(Brush.radialGradient(listOf(solColor, Color.Transparent), sun, band * 0.5f), band * 0.5f, sun)
}

/*
 * Decoración sobre el auto (v0.66.0; v0.67.0: más realista y animada). Se dibuja en la caja de la
 * foto del Onix (de frente, en tres cuartos), con contornos medidos sobre la foto en fracciones de
 * la caja (ver docs/tasks/inicio-y-fiestas.md): borde del techo, frente del capó (sobre la parrilla y
 * el faro derecho) y línea de la ventanilla lateral. Con el modo liviano queda quieta.
 */
private val ROOF = listOf(0.26f to 0.195f, 0.40f to 0.135f, 0.55f to 0.075f, 0.68f to 0.035f, 0.785f to 0.015f)
private val HOOD = listOf(
    0.04f to 0.375f, 0.12f to 0.36f, 0.25f to 0.362f, 0.40f to 0.375f,
    0.43f to 0.42f, 0.55f to 0.405f, 0.65f to 0.39f, 0.745f to 0.375f
)
private val BELT = listOf(0.80f to 0.255f, 0.88f to 0.265f, 0.975f to 0.30f)
private val BULBS = listOf(Color(0xFFFF4040), Color(0xFF3DDC84), Color(0xFFFFD23F), Color(0xFF4CB0FF), Color(0xFFFF9F1C))

@Composable
fun CarDecor(
    holiday: Holiday,
    animate: Boolean,
    ambient: Ambient = Ambient.DIA,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val t = if (animate) {
        val inf = rememberInfiniteTransition(label = "adornos")
        val v by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else 0.3f
    val carTint = carTintFor(ambient)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun pts(line: List<Pair<Float, Float>>, n: Int, up: Float = 0f) =
            Festive.along(line, n).map { (x, y) -> Offset(w * x, h * (y - up)) }
        // v0.81.7: ploteo integral del auto con la bandera argentina y adornos navideños con corona y bombillas C9
        when (holiday.decor) {
            Decor.SNOW -> {
                snowCap(pts(ROOF, 40), h, carTint)
                lightString(pts(ROOF, 22, -0.004f), h, t, 0, ambient, animate)
                lightString(pts(HOOD, 24), h, t, 1, ambient, animate)
                lightString(pts(BELT, 9), h, t, 2, ambient, animate)
                christmasWreath(Offset(w * 0.25f, h * 0.44f), h * 0.075f, t, animate)
            }
            Decor.PATRIA -> cockade(Offset(w * 0.40f, h * 0.30f), h * 0.032f, carTint)
            Decor.HALLOWEEN, Decor.HEARTS, Decor.FIREWORKS, Decor.CONFETTI, Decor.PETALS -> Unit
        }
    }
}

/** Corona navideña de ramas de pino, bayas rojas, moño de terciopelo y micro-luces en la parrilla. */
private fun DrawScope.christmasWreath(c: Offset, r: Float, t: Float, animate: Boolean) {
    val pinoOscuro = Color(0xFF13381B)
    val pinoMedio = Color(0xFF1E5627)
    val pinoClaro = Color(0xFF2E7D32)

    // 1. Anillos de follaje de pino
    drawCircle(pinoOscuro, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.50f))
    drawCircle(pinoMedio, r * 0.95f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.35f))

    // 2. Acículas de pino que salen radialmente dando textura 3D
    val nNeedles = 28
    for (i in 0 until nNeedles) {
        val angle = (i * 2 * PI / nNeedles).toFloat()
        val rBase = r * (0.80f + 0.18f * sin(i * 3.7f))
        val needleLen = r * 0.35f
        val x1 = c.x + cos(angle) * rBase
        val y1 = c.y + sin(angle) * rBase
        val x2 = c.x + cos(angle + 0.18f) * (rBase + needleLen)
        val y2 = c.y + sin(angle + 0.18f) * (rBase + needleLen)
        drawLine(if (i % 2 == 0) pinoClaro else pinoMedio, Offset(x1, y1), Offset(x2, y2), strokeWidth = r * 0.09f)
    }

    // 3. Bayas rojas navideñas en racimos
    val berryColor = Color(0xFFD32F2F)
    val berryHighlight = Color(0xFFFFCDD2)
    val berryRadius = r * 0.13f
    for (i in 0 until 6) {
        val bAngle = (i * 2 * PI / 6 + 0.25f).toFloat()
        val bx = c.x + cos(bAngle) * r * 0.95f
        val by = c.y + sin(bAngle) * r * 0.95f
        drawCircle(berryColor, berryRadius, Offset(bx, by))
        drawCircle(berryHighlight, berryRadius * 0.35f, Offset(bx - berryRadius * 0.3f, by - berryRadius * 0.3f))
        drawCircle(berryColor, berryRadius * 0.85f, Offset(bx + berryRadius * 0.8f, by + berryRadius * 0.35f))
    }

    // 4. Micro-luces LED cálidas incrustadas que titilan con vida
    val microLedCount = 8
    for (i in 0 until microLedCount) {
        val lAngle = (i * 2 * PI / microLedCount + 0.15f).toFloat()
        val lx = c.x + cos(lAngle) * r * 0.90f
        val ly = c.y + sin(lAngle) * r * 0.90f
        val lOn = if (animate) {
            0.25f + 0.75f * (0.5f + 0.5f * sin(2 * PI.toFloat() * (t * 3.5f + i * 0.45f)))
        } else 0.85f
        drawCircle(Color(0xFFFFF9C4).copy(alpha = lOn * 0.85f), r * 0.16f, Offset(lx, ly))
        drawCircle(Color.White.copy(alpha = lOn), r * 0.07f, Offset(lx, ly))
    }

    // 5. Moño rojo de terciopelo en la parte superior de la corona
    val bowCenter = Offset(c.x, c.y - r * 0.82f)
    val redBow = Color(0xFFC62828)
    val redDark = Color(0xFF8E0000)
    // Cintas colgantes
    val ribbon1 = Path().apply {
        moveTo(bowCenter.x - r * 0.12f, bowCenter.y + r * 0.10f)
        lineTo(bowCenter.x - r * 0.45f, bowCenter.y + r * 0.95f)
        lineTo(bowCenter.x - r * 0.30f, bowCenter.y + r * 0.85f)
        lineTo(bowCenter.x - r * 0.08f, bowCenter.y + r * 0.90f)
        close()
    }
    val ribbon2 = Path().apply {
        moveTo(bowCenter.x + r * 0.12f, bowCenter.y + r * 0.10f)
        lineTo(bowCenter.x + r * 0.45f, bowCenter.y + r * 0.95f)
        lineTo(bowCenter.x + r * 0.30f, bowCenter.y + r * 0.85f)
        lineTo(bowCenter.x + r * 0.08f, bowCenter.y + r * 0.90f)
        close()
    }
    drawPath(ribbon1, redDark)
    drawPath(ribbon2, redDark)
    // Lazos izquierdo y derecho
    drawOval(redBow, topLeft = Offset(bowCenter.x - r * 0.60f, bowCenter.y - r * 0.30f), size = Size(r * 0.60f, r * 0.40f))
    drawOval(redBow, topLeft = Offset(bowCenter.x, bowCenter.y - r * 0.30f), size = Size(r * 0.60f, r * 0.40f))
    // Nudo central
    drawCircle(Color(0xFFE53935), r * 0.18f, bowCenter)
}

/** Tira de luces con bombillas C9 facetadas, casquillos, filamentos y dinámica viva (chaser + sparkle). */
private fun DrawScope.lightString(
    p: List<Offset>,
    h: Float,
    t: Float,
    seed: Int,
    ambient: Ambient,
    animate: Boolean
) {
    if (p.size < 2) return
    val wire = Path().apply {
        moveTo(p[0].x, p[0].y)
        for (i in 1 until p.size) {
            val mid = Offset((p[i - 1].x + p[i].x) / 2, (p[i - 1].y + p[i].y) / 2 + h * 0.013f)
            quadraticTo(mid.x, mid.y, p[i].x, p[i].y)
        }
    }
    drawPath(wire, Color(0xFF132A13), style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.005f))

    val r = h * 0.012f
    val isNight = ambient.isNight
    val isSunset = ambient.timeOfDay == Ambient.ATARDECER
    val glowMult = if (isNight) 1.5f else if (isSunset) 1.1f else 0.55f
    val glowR = r * (if (isNight) 6.0f else if (isSunset) 4.5f else 3.0f)

    for ((i, c) in p.withIndex()) {
        val color = BULBS[(i + seed) % BULBS.size]
        val on = if (animate) {
            val wave = 0.5f + 0.5f * sin(2 * PI.toFloat() * (t * 2.5f - i * 0.16f + seed * 0.5f))
            val sparkle = if (sin(2 * PI.toFloat() * (t * 7.0f + i * 1.618f)) > 0.82f) 0.35f else 0f
            (0.12f + 0.68f * wave + sparkle).coerceIn(0.12f, 1f)
        } else 0.85f

        // 1. Resplandor volumétrico y reflejo sobre la chapa del auto
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.65f * on * glowMult), Color.Transparent),
                center = c,
                radius = glowR
            ),
            radius = glowR,
            center = c
        )

        // 2. Casquillo / zócalo verde oscuro sujeto al cable
        val socketW = r * 0.9f
        val socketH = r * 0.65f
        drawRect(Color(0xFF182E18), Offset(c.x - socketW / 2, c.y - socketH), Size(socketW, socketH))

        // 3. Bulbo C9 facetado en forma de lágrima/diamante
        val bulbTipY = c.y + r * 1.7f
        val bulbPath = Path().apply {
            moveTo(c.x - r * 0.55f, c.y)
            quadraticTo(c.x - r * 0.95f, c.y + r * 0.85f, c.x, bulbTipY)
            quadraticTo(c.x + r * 0.95f, c.y + r * 0.85f, c.x + r * 0.55f, c.y)
            close()
        }
        drawPath(bulbPath, color.copy(alpha = 0.40f + 0.60f * on))

        // 4. Filamento interno incandescente brillante
        drawCircle(Color.White.copy(alpha = 0.85f * on), r * 0.35f, Offset(c.x, c.y + r * 0.55f))
    }
}

/** Nieve acumulada sobre el techo, con relieve y carámbanos teñidos según la luz ambiental. */
private fun DrawScope.snowCap(p: List<Offset>, h: Float, carTint: Color) {
    val cap = Path().apply {
        moveTo(p.first().x, p.first().y)
        for ((i, c) in p.withIndex()) {
            val bump = h * (0.045f + 0.012f * sin(i * 1.7f) + 0.006f * sin(i * 4.3f))
            lineTo(c.x, c.y - bump)
        }
        for (c in p.asReversed()) lineTo(c.x, c.y + h * 0.006f)
        close()
    }
    val topSnow = tintSnow(Color.White, carTint)
    val botSnow = tintSnow(Color(0xFFDDE7F2), carTint)
    drawPath(cap, Brush.verticalGradient(listOf(topSnow, botSnow), startY = p.last().y - h * 0.06f, endY = p.first().y + h * 0.01f))
    val icicleColor = tintSnow(Color(0xE6E8F4FF), carTint)
    for (i in 2 until p.size - 1 step 4) {
        val c = p[i]
        val len = h * (0.018f + 0.012f * ((i * 7) % 3))
        drawPath(Path().apply {
            moveTo(c.x - h * 0.006f, c.y); lineTo(c.x + h * 0.006f, c.y); lineTo(c.x, c.y + len); close()
        }, icicleColor)
    }
}

/** Escarapela con sus cintas, modulada por el tinte ambiental de la carrocería. */
private fun DrawScope.cockade(c: Offset, r: Float, carTint: Color) {
    val celeste = tintSnow(Color(0xFF74ACDF), carTint)
    val blanco = tintSnow(Color.White, carTint)
    for (dir in listOf(-1f, 1f)) drawPath(Path().apply {
        moveTo(c.x, c.y); lineTo(c.x + dir * r * 0.9f, c.y + r * 1.9f); lineTo(c.x + dir * r * 0.35f, c.y + r * 1.6f); close()
    }, celeste)
    drawCircle(celeste, r, c)
    drawCircle(blanco, r * 0.66f, c)
    drawCircle(celeste, r * 0.33f, c)
    drawCircle(blanco.copy(alpha = 0.35f), r * 0.9f, Offset(c.x - r * 0.25f, c.y - r * 0.25f), style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.08f))
}

private fun tintSnow(c: Color, tint: Color): Color = Color(
    red = (c.red * (0.45f + 0.55f * tint.red)).coerceIn(0f, 1f),
    green = (c.green * (0.45f + 0.55f * tint.green)).coerceIn(0f, 1f),
    blue = (c.blue * (0.45f + 0.55f * tint.blue)).coerceIn(0f, 1f),
    alpha = c.alpha
)
