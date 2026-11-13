package com.axel.onixlauncher

import androidx.compose.ui.graphics.Color

/**
 * Ambiente de Inicio: la hora del día y, si llueve, su versión con lluvia (v0.39.0: la lluvia
 * sigue la hora; antes había un solo fondo de lluvia de día).
 */
enum class Ambient {
    DIA, ATARDECER, NOCHE, LLUVIA, LLUVIA_ATARDECER, LLUVIA_NOCHE;

    val raining: Boolean get() = this == LLUVIA || this == LLUVIA_ATARDECER || this == LLUVIA_NOCHE

    /** El mismo momento del día sin lluvia. */
    val timeOfDay: Ambient get() = when (this) {
        LLUVIA -> DIA
        LLUVIA_ATARDECER -> ATARDECER
        LLUVIA_NOCHE -> NOCHE
        else -> this
    }

    val isNight: Boolean get() = timeOfDay == NOCHE
}

/** Foto de fondo de cada ambiente. */
fun backgroundFor(a: Ambient): Int = when (a) {
    Ambient.DIA -> R.drawable.bg_dia
    Ambient.ATARDECER -> R.drawable.bg_atardecer
    Ambient.NOCHE -> R.drawable.bg_noche
    Ambient.LLUVIA -> R.drawable.bg_lluvia
    Ambient.LLUVIA_ATARDECER -> R.drawable.bg_lluvia_atardecer
    Ambient.LLUVIA_NOCHE -> R.drawable.bg_lluvia_noche
}

/**
 * Tono que se le aplica a la foto del auto para que coincida con la luz del fondo. Atardecer y
 * noche están medidos en las imágenes de referencia (tools/make_bg_mockups.py imprime el tono).
 * Los de lluvia de atardecer y noche aplican al tono base el mismo oscurecimiento que
 * tools/make_bg_rain.py aplica al fondo.
 */
fun carTintFor(a: Ambient): Color = when (a) {
    Ambient.DIA -> Color.White
    Ambient.ATARDECER -> Color(0xFFF8EDEC)
    Ambient.NOCHE -> Color(0xFF96A2BC)
    Ambient.LLUVIA -> Color(0xFFA9B0BC)
    Ambient.LLUVIA_ATARDECER -> Color(0xFF9A989A)
    Ambient.LLUVIA_NOCHE -> Color(0xFF757E93)
}

/** Ambiente según la hora local y si llueve (el clima real, ver LauncherApp). */
fun ambientFor(hour: Float, raining: Boolean = false): Ambient {
    val t = when {
        hour >= 7.5f && hour < 17.5f -> Ambient.DIA
        (hour >= 17.5f && hour < 19.5f) || (hour >= 6f && hour < 7.5f) -> Ambient.ATARDECER
        else -> Ambient.NOCHE
    }
    if (!raining) return t
    return when (t) {
        Ambient.DIA -> Ambient.LLUVIA
        Ambient.ATARDECER -> Ambient.LLUVIA_ATARDECER
        else -> Ambient.LLUVIA_NOCHE
    }
}

/**
 * Nombre del saludo de Inicio (v0.40.0): se cambia tocando el saludo. Guarda el actual y los
 * últimos usados para elegirlos de un toque. SharedPreferences `saludo` (`nombre`, `nombres`).
 */
object GreetingName {
    const val DEFAULT = "Axel"
    private const val MAX_RECENT = 5

    private fun prefs(c: android.content.Context) = c.getSharedPreferences("saludo", android.content.Context.MODE_PRIVATE)

    fun load(c: android.content.Context): String = prefs(c).getString("nombre", null) ?: DEFAULT

    fun recent(c: android.content.Context): List<String> =
        prefs(c).getString("nombres", null)?.split('\n')?.filter { it.isNotBlank() } ?: listOf(DEFAULT)

    /** Guarda el nombre limpio y lo pone primero en los recientes. Devuelve el nombre guardado o null si estaba vacío. */
    fun save(c: android.content.Context, input: String): String? {
        val name = clean(input) ?: return null
        prefs(c).edit()
            .putString("nombre", name)
            .putString("nombres", addRecent(recent(c), name).joinToString("\n"))
            .apply()
        return name
    }

    /** Sin espacios de más y hasta 20 letras; null si queda vacío (función pura, para los tests). */
    fun clean(input: String): String? = input.trim().replace(Regex("\\s+"), " ").take(20).ifBlank { null }

    /** El nombre primero, sin repetir (sin importar mayúsculas), hasta 5. */
    fun addRecent(list: List<String>, name: String): List<String> =
        (listOf(name) + list.filterNot { it.equals(name, ignoreCase = true) }).take(MAX_RECENT)
}

/** Saludo según la hora: "Buenos días" de 6 a 12, "Buenas tardes" de 12 a 20, "Buenas noches" el resto. */
fun greetingFor(hour: Float): String = when {
    hour >= 6f && hour < 12f -> "Buenos días"
    hour >= 12f && hour < 20f -> "Buenas tardes"
    else -> "Buenas noches"
}
