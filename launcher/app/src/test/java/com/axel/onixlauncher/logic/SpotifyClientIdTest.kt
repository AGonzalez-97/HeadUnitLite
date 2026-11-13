package com.axel.onixlauncher.logic

import com.axel.onixlauncher.SpotifyAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.79.6: el Client ID de Spotify puede venir del GitHub Secret `SPOTIFY_CLIENT_ID` (se carga al
 * compilar) o pegarse a mano en la radio. El de la app gana: así uno viejo, escrito a mano, no
 * tapa al nuevo. No es un secreto (PKCE no usa "client secret" y el Client ID va en el enlace de permiso).
 */
class SpotifyClientIdTest {

    @Test
    fun el_de_la_app_gana_sobre_el_pegado_a_mano() {
        assertEquals("de-la-app", SpotifyAuth.pickClientId(saved = "viejo-a-mano", builtIn = "de-la-app"))
    }

    @Test
    fun sin_el_de_la_app_vale_el_pegado_a_mano() {
        assertEquals("a-mano", SpotifyAuth.pickClientId(saved = "a-mano", builtIn = ""))
        assertEquals("a-mano", SpotifyAuth.pickClientId(saved = "a-mano", builtIn = null))
        assertEquals("a-mano", SpotifyAuth.pickClientId(saved = "a-mano", builtIn = "   "))
    }

    @Test
    fun sin_ninguno_no_hay_client_id() {
        assertNull(SpotifyAuth.pickClientId(saved = null, builtIn = ""))
        assertNull(SpotifyAuth.pickClientId(saved = "  ", builtIn = null))
    }

    @Test
    fun se_ignoran_los_espacios_de_los_bordes() {
        assertEquals("abc123", SpotifyAuth.pickClientId(saved = null, builtIn = "  abc123 \n"))
    }
}
