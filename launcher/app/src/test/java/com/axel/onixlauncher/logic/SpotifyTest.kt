package com.axel.onixlauncher.logic

import com.axel.onixlauncher.SpotifyApi
import com.axel.onixlauncher.SpotifyAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spotify: desafío PKCE y lectura de respuestas de la API web (org.json y Base64 necesitan Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotifyTest {

    @Test
    fun desafio_pkce_del_rfc_7636() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun listas_y_recientes_sin_repetidos() {
        val pl = SpotifyApi.parsePlaylists(
            """{"items":[{"name":"Ruta","uri":"spotify:playlist:1","images":[{"url":"https://i.scdn.co/big.jpg"},{"url":"https://i.scdn.co/mid.jpg"}],"owner":{"display_name":"Axel"},"tracks":{"total":42}},{"name":"Sin uri"}]}"""
        )
        assertEquals(1, pl.size)
        assertEquals("Axel · 42 temas", pl[0].subtitle)
        assertEquals("https://i.scdn.co/mid.jpg", pl[0].imageUrl)
        assertFalse(pl[0].isTrack)
        val rec = SpotifyApi.parseRecent(
            """{"items":[{"track":{"name":"Tema","uri":"spotify:track:a","album":{"images":[{"url":"https://i.scdn.co/t.jpg"}]},"artists":[{"name":"A"},{"name":"B"}]}},
                {"track":{"name":"Tema","uri":"spotify:track:a","artists":[]}}]}"""
        )
        assertEquals(1, rec.size)
        assertEquals("A, B", rec[0].subtitle)
        assertEquals("https://i.scdn.co/t.jpg", rec[0].imageUrl)
        assertTrue(rec[0].isTrack)
    }

    @Test
    fun historial_de_busquedas_sin_duplicados_y_con_tope() {
        val q1 = SpotifyApi.pushQuery(emptyList(), "Soda Stereo")
        val q2 = SpotifyApi.pushQuery(q1, "Cerati")
        val q3 = SpotifyApi.pushQuery(q2, "soda stereo")
        assertEquals(listOf("soda stereo", "Cerati"), q3)
    }

    @Test
    fun letras_sincronizadas_y_renglon_activo_por_tiempo() {
        val json = """{"syncedLyrics":"[00:12.40] Ella durmió\n[00:17.85] Y yo desperté\n[00:23.10] Algún tiempo atrás"}"""
        val lines = com.axel.onixlauncher.Lyrics.parseResponse(json)
        assertEquals(3, lines.size)
        assertEquals(12_400L, lines[0].timeMs)
        assertEquals("Ella durmió", lines[0].text)
        assertEquals(-1, com.axel.onixlauncher.Lyrics.activeLineIndex(lines, 5_000L))
        assertEquals(0, com.axel.onixlauncher.Lyrics.activeLineIndex(lines, 15_000L))
        assertEquals(1, com.axel.onixlauncher.Lyrics.activeLineIndex(lines, 17_850L))
        assertEquals(2, com.axel.onixlauncher.Lyrics.activeLineIndex(lines, 30_000L))
    }

    @Test
    fun busqueda_y_dispositivos() {
        val s = SpotifyApi.parseSearch(
            """{"tracks":{"items":[{"name":"T","uri":"spotify:track:t","album":{"uri":"spotify:album:al1"},"artists":[{"name":"X"}]}]},
                "artists":{"items":[{"name":"X","uri":"spotify:artist:x"}]},
                "playlists":{"items":[null,{"name":"L","uri":"spotify:playlist:l"}]}}"""
        )
        assertEquals(listOf("T", "X", "L"), s.map { it.name })
        assertEquals("spotify:album:al1", s[0].contextUri)
        val trackBody = SpotifyApi.playBody(s[0])
        assertTrue(trackBody, trackBody.contains("\"context_uri\":\"spotify:album:al1\"") && trackBody.contains("\"uri\":\"spotify:track:t\""))
        val d = SpotifyApi.parseDevices("""{"devices":[{"id":"a","is_active":false},{"id":"b","is_active":true},{"id":"c","is_restricted":true}]}""")
        assertEquals(listOf("a" to false, "b" to true), d)
        assertTrue(SpotifyApi.parsePlaylists("roto").isEmpty())
    }

    // v0.81.17: opening a playlist shows its tracks; picking one plays it inside the playlist.
    @Test
    fun temas_de_una_lista_se_reproducen_dentro_de_la_lista() {
        val list = "spotify:playlist:37i9dQZF1DX"
        assertEquals("37i9dQZF1DX", SpotifyApi.playlistId(list))
        assertEquals(null, SpotifyApi.playlistId("spotify:album:1"))
        assertEquals(null, SpotifyApi.playlistId("spotify:playlist:"))
        val page = SpotifyApi.parsePlaylistTracks(
            """{"total":3,"items":[
                {"item":{"name":"Uno","uri":"spotify:track:1","artists":[{"name":"A"},{"name":"B"}],"album":{"uri":"spotify:album:9","images":[{"url":"g"},{"url":"m"}]}}},
                {"track":null},
                {"track":{"name":"Local","uri":""}},
                {"track":{"name":"Dos","uri":"spotify:track:2","artists":[{"name":"C"}]}}
            ]}""",
            list
        )
        assertEquals(3, page.total)
        assertEquals(listOf("Uno", "Dos"), page.items.map { it.name })
        assertEquals("A, B", page.items[0].subtitle)
        assertEquals("m", page.items[0].imageUrl)
        assertTrue(page.items.all { it.isTrack && it.contextUri == list })
        val body = org.json.JSONObject(SpotifyApi.playBody(page.items[1]))
        assertEquals(list, body.getString("context_uri"))
        assertEquals("spotify:track:2", body.getJSONObject("offset").getString("uri"))
        // v0.81.20: Spotify renamed tracks -> items in the playlist object (2026)
        val renamed = SpotifyApi.parsePlaylists("""{"items":[{"name":"Mía","uri":"spotify:playlist:9","items":{"total":12}}]}""")
        assertEquals("12 temas", renamed[0].subtitle)
        val broken = SpotifyApi.parsePlaylistTracks("roto", list)
        assertEquals(0, broken.total)
        assertTrue(broken.items.isEmpty())
    }
}
