package com.axel.onixlauncher

import org.json.JSONObject
import kotlin.math.roundToInt

/** Clima actual y del día. Datos de Open-Meteo (gratis, sin clave). */
class Weather(
    val tempC: Int,
    val code: Int,
    val feelsC: Int = tempC,
    val humidity: Int? = null,
    val windKmh: Int? = null,
    val maxC: Int? = null,
    val minC: Int? = null,
    val rainProb: Int? = null,
    val isDay: Boolean = true,
    val days: List<DayForecast> = emptyList()
) {
    val raining: Boolean
        get() = code in 51..67 || code in 80..82 || code in 95..99

    /** Estado corto para la tarjeta de hoy. */
    val shortLabel: String
        get() = when (code) {
            0 -> "Despejado"
            1, 2 -> "Nubes"
            3 -> "Nublado"
            45, 48 -> "Niebla"
            in 51..57 -> "Llovizna"
            in 61..67 -> "Lluvia"
            in 71..77 -> "Nieve"
            in 80..82 -> "Chaparrón"
            in 95..99 -> "Tormenta"
            else -> ""
        }

    val label: String
        get() = when (code) {
            0 -> if (isDay) "Despejado" else "Noche despejada"
            1, 2 -> "Algo nublado"
            3 -> "Nublado"
            45, 48 -> "Niebla"
            in 51..57 -> "Llovizna"
            in 61..67 -> "Lluvia"
            in 71..77 -> "Nieve"
            in 80..82 -> "Chaparrones"
            in 95..99 -> "Tormenta"
            else -> ""
        }
}

/** Pronóstico de un día: etiqueta corta (LUN, MAR...), código de clima y temperaturas. */
class DayForecast(val day: String, val code: Int, val maxC: Int, val minC: Int)

private val DayNames = listOf("DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB")

internal fun dayLabel(isoDate: String): String = try {
    val p = isoDate.split("-").map { it.toInt() }
    val c = java.util.Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2]) }
    DayNames[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
} catch (e: Exception) {
    ""
}

/** Enlace de Open-Meteo para una ubicación: clima actual y 5 días. */
fun weatherUrl(lat: Double, lon: Double): String =
    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=6"

/** Se llama fuera del hilo principal. Devuelve null si no hay internet o falla. */
fun fetchWeather(lat: Double, lon: Double): Weather? {
    val body = Net.get(weatherUrl(lat, lon)) ?: return null
    return parseWeather(body)
}

/** Convierte la respuesta JSON de Open-Meteo. Devuelve null si el formato no es el esperado. */
fun parseWeather(body: String): Weather? = try {
    val json = JSONObject(body)
    val cur = json.getJSONObject("current")
    val daily = json.optJSONObject("daily")
    fun dailyInt(key: String): Int? =
        daily?.optJSONArray(key)?.let { if (it.length() > 0 && !it.isNull(0)) it.getDouble(0).roundToInt() else null }
    Weather(
        tempC = cur.getDouble("temperature_2m").roundToInt(),
        code = cur.getInt("weather_code"),
        feelsC = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")).roundToInt(),
        humidity = if (cur.has("relative_humidity_2m")) cur.getInt("relative_humidity_2m") else null,
        windKmh = if (cur.has("wind_speed_10m")) cur.getDouble("wind_speed_10m").roundToInt() else null,
        maxC = dailyInt("temperature_2m_max"),
        minC = dailyInt("temperature_2m_min"),
        rainProb = dailyInt("precipitation_probability_max"),
        isDay = cur.optInt("is_day", 1) == 1,
        days = run {
            val times = daily?.optJSONArray("time")
            val codes = daily?.optJSONArray("weather_code")
            val maxs = daily?.optJSONArray("temperature_2m_max")
            val mins = daily?.optJSONArray("temperature_2m_min")
            if (times == null || codes == null || maxs == null || mins == null) emptyList()
            else (1 until times.length()).mapNotNull { i ->
                if (codes.isNull(i) || maxs.isNull(i) || mins.isNull(i)) null
                else DayForecast(
                    dayLabel(times.getString(i)), codes.getInt(i),
                    maxs.getDouble(i).roundToInt(), mins.getDouble(i).roundToInt()
                )
            }
        }
    )
} catch (e: Exception) {
    AppLog.warn("Clima: respuesta inesperada", e)
    null
}
