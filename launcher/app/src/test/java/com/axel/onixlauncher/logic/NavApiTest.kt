package com.axel.onixlauncher.logic

import com.axel.onixlauncher.NavApi
import com.axel.onixlauncher.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Buscador y rutas de TomTom (v0.75.0) de la pantalla de navegación (org.json necesita Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavApiTest {

    @Test
    fun lugares_de_tomtom() {
        // Respuesta de TomTom (búsqueda) para "9 de julio 550, Tafí Viejo" (prueba del 05/11/2026)
        val json = """{"results":[
            {"type":"Point Address","address":{"streetName":"Calle 9 de Julio","streetNumber":"550","municipality":"Tafí Viejo","countrySubdivision":"Tucumán","freeformAddress":"Calle 9 de Julio 550, T4103AFL Tafí Viejo Tucumán"},"position":{"lat":-26.7368,"lon":-65.25799}},
            {"type":"POI","poi":{"name":"Estación Tafí Viejo"},"address":{"streetName":"Calle Bartolomé Mitre","municipality":"Tafí Viejo","freeformAddress":"Calle Bartolomé Mitre, Tafí Viejo"},"position":{"lat":-26.7354,"lon":-65.2575}},
            {"type":"Street","address":{},"position":{"lat":1}}]}"""
        val places = NavApi.parseTomTomPlaces(json)
        assertEquals(2, places.size)
        assertEquals("Calle 9 de Julio 550, Tafí Viejo, Tucumán", places[0].name)
        assertEquals(-26.7368, places[0].lat, 1e-6)
        assertEquals("Estación Tafí Viejo, Calle Bartolomé Mitre, Tafí Viejo", places[1].name)
        assertTrue(NavApi.parseTomTomPlaces("no es json").isEmpty())
    }

    @Test
    fun ruta_de_tomtom_con_maniobras_y_resumen() {
        val json = """{"routes":[{"summary":{"lengthInMeters":14000,"travelTimeInSeconds":1320},
            "legs":[{"points":[{"latitude":-26.8303,"longitude":-65.2038},{"latitude":-26.80,"longitude":-65.22},{"latitude":-26.7368,"longitude":-65.258}]}],
            "guidance":{"instructions":[
              {"routeOffsetInMeters":0,"point":{"latitude":-26.8303,"longitude":-65.2038},"maneuver":"DEPART","street":"Avenida Mate de Luna"},
              {"routeOffsetInMeters":3000,"point":{"latitude":-26.81,"longitude":-65.21},"maneuver":"TURN_RIGHT","street":"Laprida"},
              {"routeOffsetInMeters":9000,"point":{"latitude":-26.78,"longitude":-65.23},"maneuver":"ROUNDABOUT_RIGHT","roundaboutExitNumber":2,"street":"Ruta 9"},
              {"routeOffsetInMeters":12000,"point":{"latitude":-26.75,"longitude":-65.25},"maneuver":"KEEP_LEFT"},
              {"routeOffsetInMeters":14000,"point":{"latitude":-26.7368,"longitude":-65.258},"maneuver":"ARRIVE_RIGHT"}]}}]}"""
        val r = NavApi.parseTomTomRoute(json)
        assertNotNull(r)
        assertEquals(3, r!!.points.size)
        assertEquals(5, r.steps.size)
        assertEquals("Salí por Avenida Mate de Luna", r.steps[0].instruction)
        assertEquals("Girá a la derecha en Laprida", r.steps[1].instruction)
        assertEquals(6000.0, r.steps[1].distanceM, 0.1)          // hasta la maniobra siguiente
        assertEquals("En la rotonda, tomá la 2.ª salida hacia Ruta 9", r.steps[2].instruction)
        assertEquals("En la bifurcación, mantenete a la izquierda", r.steps[3].instruction)
        assertEquals("Llegaste a destino", r.steps[4].instruction)
        assertEquals("14,0 km · 22 min", NavApi.summary(r))
        assertEquals("850 m · 2 h 5 min", NavApi.summary(Route(850.0, 7500.0, emptyList())))
        assertNull(NavApi.parseTomTomRoute("{}"))
    }

    @Test
    fun maniobras_de_tomtom_a_las_de_la_guia() {
        assertEquals(Triple("turn", "left", 0), NavApi.maneuver("TURN_LEFT", 0))
        assertEquals(Triple("turn", "slight right", 0), NavApi.maneuver("BEAR_RIGHT", 0))
        assertEquals(Triple("turn", "sharp left", 0), NavApi.maneuver("SHARP_LEFT", 0))
        assertEquals(Triple("turn", "uturn", 0), NavApi.maneuver("MAKE_UTURN", 0))
        assertEquals(Triple("roundabout", "", 3), NavApi.maneuver("ROUNDABOUT_LEFT", 3))
        assertEquals("roundabout", NavApi.maneuver("ROUNDABOUT_CROSS", 1).first)
        assertEquals(Triple("fork", "slight right", 0), NavApi.maneuver("KEEP_RIGHT", 0))
        assertEquals("merge", NavApi.maneuver("ENTER_MOTORWAY", 0).first)
        assertEquals(Triple("off ramp", "slight left", 0), NavApi.maneuver("MOTORWAY_EXIT_LEFT", 0))
        assertEquals("arrive", NavApi.maneuver("ARRIVE", 0).first)
        assertEquals(Triple("continue", "straight", 0), NavApi.maneuver("STRAIGHT", 0))
        assertEquals(Triple("continue", "straight", 0), NavApi.maneuver("ALGO_NUEVO", 0))
    }

    @Test
    fun calle_y_localidad_del_inverso_de_tomtom() {
        val json = """{"addresses":[{"address":{"streetName":"Avenida Adolfo de la Vega","streetNumber":"473","municipality":"San Miguel de Tucumán","freeformAddress":"Avenida Adolfo de la Vega 473, San Miguel de Tucumán"}}]}"""
        assertEquals("Avenida Adolfo de la Vega 473, San Miguel de Tucumán", NavApi.parseTomTomReverse(json))
        assertEquals("San Miguel de Tucumán", NavApi.parseTomTomLocality(json))
        assertNull(NavApi.parseTomTomReverse("{}"))
    }

    @Test
    fun urls_de_tomtom_sin_la_clave_en_el_texto_y_con_punto_decimal() {
        java.util.Locale.setDefault(java.util.Locale("es", "AR"))
        val u = NavApi.tomTomSearchUrl("9 de julio 550", -26.83, -65.2, "CLAVE")
        assertTrue(u.startsWith("https://api.tomtom.com/search/2/search/9%20de%20julio%20550.json?"))
        assertTrue("lat=-26.830000" in u && "countrySet=AR" in u && "key=CLAVE" in u)
        assertTrue(NavApi.tomTomRouteUrl(-26.83, -65.2, -26.73, -65.25, "K").contains("/calculateRoute/-26.830000,-65.200000:-26.730000,-65.250000/json?"))
    }

    @Test
    fun estiloDelMapa3D() {
        val style = org.json.JSONObject(com.axel.onixlauncher.MapStyle.json())
        assertEquals(8, style.getInt("version"))
        val layers = style.getJSONArray("layers")
        val types = (0 until layers.length()).map { layers.getJSONObject(it).getString("type") }
        assertTrue("fill-extrusion" in types)          // edificios en 3D
        assertTrue("symbol" in types)                  // nombres de calles
        assertTrue(style.getJSONObject("sources").getJSONObject("omt").getString("url").startsWith("https://"))
        assertEquals(0.0, com.axel.onixlauncher.MapStyle.tilt(tilted = false, navigating = true), 0.0)
        assertEquals(60.0, com.axel.onixlauncher.MapStyle.tilt(tilted = true, navigating = true), 0.0)
        assertEquals(45.0, com.axel.onixlauncher.MapStyle.tilt(tilted = true, navigating = false), 0.0)
    }
}
