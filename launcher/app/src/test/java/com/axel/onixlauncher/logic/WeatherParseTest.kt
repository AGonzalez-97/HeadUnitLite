package com.axel.onixlauncher.logic

import com.axel.onixlauncher.DEFAULT_LAT
import com.axel.onixlauncher.DEFAULT_LON
import com.axel.onixlauncher.DEFAULT_PLACE_NAME
import com.axel.onixlauncher.dayLabel
import com.axel.onixlauncher.parseWeather
import com.axel.onixlauncher.placeName
import com.axel.onixlauncher.weatherUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lectura de la respuesta de Open-Meteo (org.json necesita Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherParseTest {

    private val sample = """
        {"current":{"temperature_2m":23.6,"apparent_temperature":24.4,"relative_humidity_2m":58,
          "wind_speed_10m":11.7,"weather_code":2,"is_day":1},
         "daily":{"time":["2026-10-26","2026-10-27","2026-10-28"],
          "weather_code":[2,61,null],
          "temperature_2m_max":[27.4,19.2,25.0],
          "temperature_2m_min":[14.6,12.1,13.0],
          "precipitation_probability_max":[10,80,5]}}
    """.trimIndent()

    @Test
    fun lee_el_clima_actual_y_del_dia() {
        val w = parseWeather(sample)
        assertNotNull(w)
        w!!
        assertEquals(24, w.tempC)
        assertEquals(24, w.feelsC)
        assertEquals(2, w.code)
        assertEquals(58, w.humidity)
        assertEquals(12, w.windKmh)
        assertEquals(27, w.maxC)
        assertEquals(15, w.minC)
        assertEquals(10, w.rainProb)
        assertTrue(w.isDay)
    }

    @Test
    fun pronostico_desde_manana_y_sin_dias_incompletos() {
        val days = parseWeather(sample)!!.days
        assertEquals(1, days.size)
        assertEquals("MAR", days[0].day)
        assertEquals(61, days[0].code)
        assertEquals(19, days[0].maxC)
        assertEquals(12, days[0].minC)
    }

    @Test
    fun respuesta_invalida_devuelve_null() {
        assertNull(parseWeather("{}"))
        assertNull(parseWeather("no es json"))
    }

    @Test
    fun nombre_del_dia() {
        assertEquals("LUN", dayLabel("2026-10-26"))
        assertEquals("DOM", dayLabel("2026-11-01"))
        assertEquals("", dayLabel("fecha rota"))
    }

    @Test
    fun la_consulta_usa_la_casa_por_defecto_y_https() {
        val url = weatherUrl(DEFAULT_LAT, DEFAULT_LON)
        assertTrue(url.startsWith("https://api.open-meteo.com/"))
        assertTrue(url, "latitude=-26.7368" in url && "longitude=-65.258" in url)
    }

    /**
     * v0.79.7 (decisión del autor): sin GPS, el punto por defecto es su casa ("9 de Julio 550, Tafí Viejo"), con la
     * posición que dio TomTom en la prueba del 05/11 (NavApiTest). Antes eran dos puntos distintos de San Miguel de
     * Tucumán (clima y mapa, y "Mis lugares" y la búsqueda desde el celular), a 12 km de su casa.
     */
    @Test
    fun sin_ubicacion_el_lugar_y_el_punto_son_la_casa_del_autor() {
        assertEquals("Tafí Viejo", DEFAULT_PLACE_NAME)
        assertEquals(-26.7368, DEFAULT_LAT, 0.0005)
        assertEquals(-65.258, DEFAULT_LON, 0.0005)
        assertEquals(DEFAULT_PLACE_NAME, placeName(org.robolectric.RuntimeEnvironment.getApplication(), null))
    }
}
