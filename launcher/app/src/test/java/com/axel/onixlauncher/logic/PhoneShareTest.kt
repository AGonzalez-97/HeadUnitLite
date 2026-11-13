package com.axel.onixlauncher.logic

import com.axel.onixlauncher.PhoneShare
import com.axel.onixlauncher.SharedDest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.76.0: destinos que llegan del celular por ntfy (dirección, ubicación de WhatsApp o link de Maps). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhoneShareTest {

    @Test
    fun direccion_escrita_en_un_mensaje() {
        assertEquals(SharedDest.Query("9 de Julio 550, Tafí Viejo"), PhoneShare.parse("  9 de Julio 550,\nTafí Viejo  "))
        assertNull(PhoneShare.parse("   "))
    }

    @Test
    fun ubicacion_compartida_por_whatsapp_y_links_de_maps() {
        // WhatsApp manda la ubicación como link de Maps, a veces con la coma codificada
        assertEquals(SharedDest.Coords(-26.7368, -65.25799, null),
            PhoneShare.parse("https://maps.google.com/maps?q=-26.7368%2C-65.25799&z=17&hl=es"))
        assertEquals(SharedDest.Coords(-26.82829, -65.25316, "Casa de Juan"),
            PhoneShare.parse("Casa de Juan\nhttps://www.google.com/maps/place/Casa/@-26.8,-65.2,17z/data=!3d-26.82829!4d-65.25316"))
        assertEquals(SharedDest.Coords(-26.83, -65.2038, null), PhoneShare.parse("geo:-26.83,-65.2038?z=16"))
        assertEquals(SharedDest.Coords(-26.7368, -65.25799, null), PhoneShare.parse("-26.7368, -65.25799"))
    }

    @Test
    fun link_de_maps_con_la_direccion_o_link_corto() {
        assertEquals(SharedDest.Query("Av. Adolfo de la Vega 473, Tucumán"),
            PhoneShare.parse("https://www.google.com/maps/search/?api=1&query=Av.+Adolfo+de+la+Vega+473%2C+Tucum%C3%A1n"))
        assertEquals(SharedDest.Link("https://maps.app.goo.gl/AbCdEf123"),
            PhoneShare.parse("Mirá dónde es: https://maps.app.goo.gl/AbCdEf123"))
        assertEquals(SharedDest.Query("Plaza Independencia"),
            PhoneShare.parse("https://www.google.com/maps/place/Plaza+Independencia/data=!4m2"))
    }

    @Test
    fun coordenadas_fuera_de_rango_no_valen() {
        assertTrue(PhoneShare.parse("123.4567, 500.1234") is SharedDest.Query)
    }

    @Test
    fun mensajes_de_ntfy() {
        val line = """{"id":"aB12","time":1762300000,"event":"message","topic":"t","message":"9 de Julio 550, Tafí Viejo"}"""
        assertEquals(Triple("aB12", 1762300000L, "9 de Julio 550, Tafí Viejo"), PhoneShare.parseEvent(line))
        assertNull(PhoneShare.parseEvent("""{"id":"x","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(PhoneShare.parseEvent("basura"))
        // Solo se toman los recientes (la radio pudo estar apagada horas)
        assertTrue(PhoneShare.isFresh(1_000_000, 1_000_000 + 60))
        assertTrue(!PhoneShare.isFresh(1_000_000, 1_000_000 + PhoneShare.MAX_AGE_S + 1))
    }
}
