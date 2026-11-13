package com.axel.onixlauncher.logic

import com.axel.onixlauncher.FuelWatch
import com.axel.onixlauncher.NavApi
import com.axel.onixlauncher.Nearby
import com.axel.onixlauncher.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.77.0: lugares cerca, paradas en el camino y aviso de nafta baja (org.json necesita Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NearbyPlacesTest {

    @Test
    fun url_de_lugares_cerca_por_categoria() {
        val u = Nearby.nearbyUrl(PlaceCategory.FUEL, -26.83, -65.2, "K")
        assertTrue(u.startsWith("https://api.tomtom.com/search/2/nearbySearch/.json?key=K"))
        assertTrue("categorySet=7311" in u && "lat=-26.830000" in u && "lon=-65.200000" in u)
        assertTrue("categorySet=7369,7313" in Nearby.nearbyUrl(PlaceCategory.PARKING, 0.0, 0.0, "K"))
    }

    @Test
    fun lugares_cerca_con_marca_calle_y_distancia() {
        val json = """{"results":[
            {"dist":1234.5,"poi":{"name":"YPF","brands":[{"name":"YPF"}]},"address":{"streetName":"Avenida Mate de Luna","streetNumber":"2100","municipality":"San Miguel de Tucumán"},"position":{"lat":-26.82,"lon":-65.23}},
            {"dist":310,"poi":{"name":"Shell Select"},"address":{"streetName":"Avenida Aconquija"},"position":{"lat":-26.81,"lon":-65.28}}]}"""
        val r = Nearby.parse(json)
        assertEquals(2, r.size)
        assertEquals("Shell Select · Avenida Aconquija · 310 m", r[0].name)          // la más cerca primero
        assertEquals("YPF · Avenida Mate de Luna 2100 · 1,2 km", r[1].name)
        assertTrue(Nearby.parse("x").isEmpty())
    }

    @Test
    fun en_el_camino_con_el_desvio() {
        val json = """{"results":[
            {"detourTime":420,"poi":{"name":"Axion"},"address":{"streetName":"Ruta 9"},"position":{"lat":-26.7,"lon":-65.2}},
            {"detourTime":60,"poi":{"name":"YPF"},"address":{"streetName":"Avenida Perón"},"position":{"lat":-26.75,"lon":-65.21}}]}"""
        val r = Nearby.parse(json)
        assertEquals("YPF · Avenida Perón · desvío 1 min", r[0].name)
        assertEquals("Axion · Ruta 9 · desvío 7 min", r[1].name)
        // El cuerpo del pedido: los puntos de la ruta que faltan (como mucho 150)
        val body = Nearby.alongBody((0 until 1000).map { -26.0 - it * 0.001 to -65.0 })
        val pts = org.json.JSONObject(body).getJSONObject("route").getJSONArray("points")
        assertTrue(pts.length() in 2..150)
        assertEquals(-26.0, pts.getJSONObject(0).getDouble("lat"), 1e-9)
        assertEquals(-26.999, pts.getJSONObject(pts.length() - 1).getDouble("lat"), 1e-9)
    }

    @Test
    fun ruta_con_parada_y_maniobra_de_la_parada() {
        val u = NavApi.tomTomRouteUrl(-26.83, -65.2, -26.73, -65.25, "K", via = -26.8 to -65.21)
        assertTrue("/calculateRoute/-26.830000,-65.200000:-26.800000,-65.210000:-26.730000,-65.250000/json?" in u)
        assertEquals("waypoint", NavApi.maneuver("WAYPOINT_REACHED", 0).first)
        assertEquals("waypoint", NavApi.maneuver("WAYPOINT_LEFT", 0).first)
        assertEquals("Llegaste a la parada", com.axel.onixlauncher.Guidance.instruction("waypoint", "", "", 0))
        assertEquals("llegaste", com.axel.onixlauncher.VoiceClips.maneuver("waypoint", "", 0))
    }

    @Test
    fun aviso_de_nafta_baja() {
        assertTrue(FuelWatch.shouldOffer(pct = 10, moving = true, lastOfferMs = 0, nowMs = 10_000_000))
        assertFalse(FuelWatch.shouldOffer(pct = 30, moving = true, lastOfferMs = 0, nowMs = 10_000_000))
        assertFalse(FuelWatch.shouldOffer(pct = 10, moving = false, lastOfferMs = 0, nowMs = 10_000_000))
        assertFalse(FuelWatch.shouldOffer(pct = null, moving = true, lastOfferMs = 0, nowMs = 10_000_000))
        // Una vez cada 30 min
        assertFalse(FuelWatch.shouldOffer(10, true, lastOfferMs = 10_000_000, nowMs = 10_000_000 + 60_000))
        assertTrue(FuelWatch.shouldOffer(10, true, lastOfferMs = 10_000_000, nowMs = 10_000_000 + FuelWatch.EVERY_MS))
    }
}
