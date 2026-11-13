package com.axel.onixlauncher.logic

import com.axel.onixlauncher.RadioDirectory
import com.axel.onixlauncher.RadioFavorites
import com.axel.onixlauncher.RadioLogos
import com.axel.onixlauncher.icoToImage
import com.axel.onixlauncher.RadioCategory
import com.axel.onixlauncher.categoryOf
import com.axel.onixlauncher.looksLikeAudio
import com.axel.onixlauncher.MediaInfo
import com.axel.onixlauncher.MediaResult
import com.axel.onixlauncher.RadioCatalog
import com.axel.onixlauncher.RadioState
import com.axel.onixlauncher.showRadioCard
import com.axel.onixlauncher.Station
import com.axel.onixlauncher.TucumanFm
import com.axel.onixlauncher.firstStreamInPlaylist
import com.axel.onixlauncher.radioCandidates
import com.axel.onixlauncher.reconnectDelayMs
import com.axel.onixlauncher.RADIO_RECONNECTS
import com.axel.onixlauncher.stationInitials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica de la radio: frecuencias, orden de enlaces, mezcla del dial y validación de dominios. */
class RadioLogicTest {

    // --- Station.freq ---

    @Test
    fun frecuencia_desde_el_detalle() {
        assertEquals(95.5f, Station("La 100", "", "FM 95.5 · Tucumán").freq)
    }

    @Test
    fun frecuencia_con_coma_desde_el_nombre() {
        assertEquals(95.1f, Station("FM Sensaciones 95,1", "", "Tucumán").freq)
    }

    @Test
    fun frecuencia_fuera_de_la_banda_fm_se_ignora() {
        assertNull(Station("Radio 790.0", "", "AM").freq)
        assertNull(Station("Radio Online", "", "Tucumán · AAC 64 kbps").freq)
    }

    @Test
    fun el_dial_de_tucuman_tiene_frecuencias_validas_y_sin_repetir() {
        val freqs = TucumanFm.stations.map { it.freq }
        assertTrue(freqs.all { it != null && it in 87.5f..108f })
        assertEquals(freqs.size, freqs.toSet().size)
    }

    // --- Orden de los enlaces ---

    @Test
    fun local_primero_y_nacional_al_final() {
        assertEquals(
            listOf("page:https://a", "nac:https://b", "lookup:"),
            radioCandidates("page:https://a|nac:https://b", national = false)
        )
    }

    @Test
    fun modo_nacional_pone_la_senal_nacional_primero() {
        assertEquals(
            listOf("nac:https://b", "page:https://a", "lookup:"),
            radioCandidates("page:https://a|nac:https://b", national = true)
        )
    }

    @Test
    fun sin_enlace_guardado_solo_busca_por_nombre() {
        assertEquals(listOf("lookup:"), radioCandidates("", national = false))
        assertEquals(listOf("https://x", "lookup:"), radioCandidates(" https://x | ", national = false))
    }

    // --- Mezcla del dial con el directorio ---

    @Test
    fun merge_toma_el_enlace_por_frecuencia() {
        val dial = listOf(Station("Radio Uno", "", "FM 90.1 · Tucumán"))
        val extra = listOf(Station("Uno FM 90.1", "https://uno/stream", "Tucumán · MP3"))
        val out = RadioDirectory.merge(dial, extra)
        assertEquals(1, out.size)
        assertEquals("Radio Uno", out[0].name)
        assertEquals("https://uno/stream", out[0].url)
    }

    @Test
    fun merge_respeta_el_enlace_propio_y_suma_las_web() {
        val dial = listOf(Station("La 100", "page:https://ar.cienradios.com/x", "FM 95.5 · Tucumán"))
        val extra = listOf(
            Station("La 100 95.5", "https://otro", "Tucumán"),
            Station("Radio Web Tucumana", "https://web", "Tucumán · AAC")
        )
        val out = RadioDirectory.merge(dial, extra)
        assertEquals("page:https://ar.cienradios.com/x", out[0].url)
        assertTrue(out.any { it.name == "Radio Web Tucumana" })
    }

    // --- Dominios de confianza (AUD-003) ---

    @Test
    fun solo_se_confia_en_cienradios() {
        assertTrue(RadioDirectory.isTrusted("https://ar.cienradios.com/player/la100_tucuman/"))
        assertTrue(RadioDirectory.isTrusted("http://buecrplb01.cienradios.com.ar/la100.aac"))
        assertFalse(RadioDirectory.isTrusted("https://cienradios.com.evil.example/x.aac"))
        assertFalse(RadioDirectory.isTrusted("https://example.com/cienradios.com.aac"))
        assertFalse(RadioDirectory.isTrusted("no es un enlace"))
    }

    @Test
    fun extrae_el_enlace_local_y_descarta_dominios_ajenos() {
        val html = """
            <script>var a = "https:\/\/malo.example\/tucuman.aac";
            var b = "https:\/\/buecrplb01.cienradios.com.ar\/la100.aac";
            var c = "https:\/\/buecrplb01.cienradios.com.ar\/la100_tucuman.aac?x=1";</script>
        """.trimIndent()
        assertEquals("https://buecrplb01.cienradios.com.ar/la100_tucuman.aac?x=1", RadioDirectory.extractStream(html))
    }

    @Test
    fun acepta_streamtheworld_como_audio_pero_no_como_pagina() {
        assertTrue(RadioDirectory.isTrustedStream("https://playerservices.streamtheworld.com/api/livestream-redirect/FM999_56.mp3"))
        assertFalse(RadioDirectory.isTrusted("https://playerservices.streamtheworld.com/x"))
        assertFalse(RadioDirectory.isTrustedStream("https://streamtheworld.com.evil.example/a.mp3"))
    }

    @Test
    fun extrae_enlace_de_streamtheworld_sin_extension() {
        val html = """<a href="https://playerservices.streamtheworld.com/api/livestream-redirect/AM790_56AAC_SC">"""
        assertEquals("https://playerservices.streamtheworld.com/api/livestream-redirect/AM790_56AAC_SC", RadioDirectory.extractStream(html))
    }

    @Test
    fun extrae_mount_de_la_configuracion_y_prefiere_tucuman() {
        val html = """{"stations":[{"mount":"FM999_56"},{"mount":"FM955TUC_56"}]}"""
        assertEquals(RadioDirectory.tritonUrl("FM955TUC_56"), RadioDirectory.extractStream(html))
    }

    @Test
    fun la_100_tiene_senal_nacional_de_streamtheworld() {
        val la100 = TucumanFm.stations.first { it.name == "La 100" }
        val c = radioCandidates(la100.url, national = true)
        assertEquals("nac:https://playerservices.streamtheworld.com/api/livestream-redirect/FM999_56.mp3", c.first())
        assertEquals("lookup:", c.last())
    }

    // --- Logos y favoritas ---

    @Test
    fun merge_toma_el_logo_del_directorio_sin_cambiar_el_enlace_propio() {
        val dial = listOf(Station("La 100", "page:https://ar.cienradios.com/x", "FM 95.5 · Tucumán"))
        val extra = listOf(Station("La 100 Tucumán 95.5", "https://otro", "Tucumán", "https://img.example/la100.png"))
        val out = RadioDirectory.merge(dial, extra)
        assertEquals("page:https://ar.cienradios.com/x", out[0].url)
        assertEquals("https://img.example/la100.png", out[0].logo)
    }

    @Test
    fun iniciales_de_la_insignia() {
        assertEquals("100", stationInitials("La 100"))
        assertEquals("MIT", stationInitials("Radio Mitre Tucumán"))
        assertEquals("RT", stationInitials("RT 88.1"))
        assertEquals("BBN", stationInitials("BBN Radio"))
        assertEquals("FM", stationInitials("   "))
    }

    @Test
    fun favoritas_se_agregan_y_se_quitan() {
        val a = RadioFavorites.toggled(setOf("La 100"), "LV7")
        assertEquals(setOf("La 100", "LV7"), a)
        assertEquals(setOf("LV7"), RadioFavorites.toggled(a, "La 100"))
    }

    @Test
    fun reconexion_con_esperas_crecientes_y_tope() {
        val delays = (1..RADIO_RECONNECTS).map { reconnectDelayMs(it) }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.sum() <= 30_000L)
    }

    @Test
    fun mount_en_javascript_y_no_confunde_amount() {
        val js = """var cfg = { amount: 'ABCD', tritonMount: 'LA100TUC' };"""
        assertEquals(RadioDirectory.tritonUrl("LA100TUC"), RadioDirectory.extractStream(js))
        assertNull(RadioDirectory.extractStream("""{"amount":"ABCD1"}"""))
    }

    @Test
    fun prefiere_la_frecuencia_de_tucuman() {
        val links = listOf(RadioDirectory.tritonUrl("FM999_56"), RadioDirectory.tritonUrl("FM955_56"))
        assertEquals(RadioDirectory.tritonUrl("FM955_56"), RadioDirectory.pickStream(links))
    }

    @Test
    fun logo_desde_la_pagina_web_pero_no_de_redes_sociales() {
        val web = Station("Radio X", "", "", homepage = "https://www.radiox.com.ar/en-vivo")
        assertEquals(
            listOf("https://www.radiox.com.ar/apple-touch-icon.png", "https://www.radiox.com.ar/favicon.ico"),
            RadioLogos.candidates(web)
        )
        val fb = Station("Radio Y", "", "", logo = "https://y/logo.png", homepage = "https://www.facebook.com/radioy")
        assertEquals(listOf("https://y/logo.png"), RadioLogos.candidates(fb))
    }

    @Test
    fun ico_con_png_adentro_devuelve_el_png() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 1, 2)
        val header = ByteArray(22)
        header[2] = 1; header[4] = 1 // tipo ícono, 1 imagen
        header[6] = 64 // ancho 64
        header[14] = png.size.toByte() // tamaño
        header[18] = 22 // posición
        assertEquals(png.toList(), icoToImage(header + png)!!.toList())
        assertNull(icoToImage(png))
    }

    @Test
    fun cadenas_nacionales_y_nombres_parecidos() {
        assertTrue(RadioDirectory.isChain("Los 40 Tucumán"))
        assertTrue(RadioDirectory.isChain("Radio Mitre Tucumán"))
        assertFalse(RadioDirectory.isChain("Radio El Palomar"))
        assertTrue(RadioDirectory.sameStation("Radio El Palomar", "El Palomar FM 87.5"))
        assertFalse(RadioDirectory.sameStation("Radio Ciudad", "Radio Belén"))
    }

    @Test
    fun categorias_conocidas_y_por_etiquetas() {
        assertEquals(RadioCategory.NOTICIAS, categoryOf(Station("Radio Mitre Tucumán", "", "")))
        assertEquals(RadioCategory.MUSICA, categoryOf(Station("La 100", "", "")))
        assertEquals(RadioCategory.DEPORTES, categoryOf(Station("Radio Sport FM", "", "")))
        assertEquals(RadioCategory.RELIGIOSA, categoryOf(Station("FM X", "", "", tags = "christian,gospel")))
        assertEquals(RadioCategory.NOTICIAS, categoryOf(Station("FM Y", "", "", tags = "news,talk")))
        assertEquals(RadioCategory.MUSICA, categoryOf(Station("FM Z", "", "", tags = "cumbia,cuarteto")))
        assertEquals(RadioCategory.OTRAS, categoryOf(Station("Radio El Palomar", "", "")))
    }

    @Test
    fun reconoce_si_una_respuesta_es_audio() {
        assertTrue(looksLikeAudio("audio/mpeg", ""))
        assertTrue(looksLikeAudio("application/vnd.apple.mpegurl", ""))
        assertTrue(looksLikeAudio("", "#EXTM3U\n#EXT-X-VERSION:3"))
        assertFalse(looksLikeAudio("text/html; charset=utf-8", "<!doctype html>"))
        assertFalse(looksLikeAudio("", "<html>"))
        assertTrue(looksLikeAudio("application/octet-stream", "ID3"))
    }

    @Test
    fun casi_todo_el_dial_tiene_logo_y_ninguno_es_svg() {
        val sinLogo = TucumanFm.stations.filter { it.logo == null }.map { it.name }
        assertTrue("sin logo: $sinLogo", sinLogo.size <= 6)
        assertTrue(TucumanFm.stations.mapNotNull { it.logo }.all { it.startsWith("https://") && !it.endsWith(".svg") })
    }

    @Test
    fun la_100_usa_la_senal_de_streamtheworld_que_existe() {
        val la100 = TucumanFm.stations.first { it.name == "La 100" }
        assertEquals(
            listOf("nac:https://playerservices.streamtheworld.com/api/livestream-redirect/FM999_56.mp3", "lookup:"),
            radioCandidates(la100.url, national = false)
        )
    }

    @Test
    fun la_mayoria_de_las_radios_conocidas_tiene_enlace_propio() {
        val conEnlace = TucumanFm.stations.count { it.url.isNotEmpty() }
        assertTrue("con enlace: $conEnlace", conEnlace >= 20)
    }

    @Test
    fun tarjeta_de_musica_pasa_a_spotify_cuando_la_radio_esta_en_pausa() {
        val spotify = MediaInfo("com.spotify.music", "Tema", "Artista", null, true, 0L, 1000L, null, readAt = 0L)
        val own = "com.axel.onixlauncher"
        try {
            RadioState.current.value = null
            assertFalse(showRadioCard(MediaResult(true, spotify), own))
            RadioState.current.value = "La 100"
            RadioState.playing.value = true
            assertTrue(showRadioCard(MediaResult(true, spotify), own))
            RadioState.playing.value = false
            assertFalse(showRadioCard(MediaResult(true, spotify), own))
            assertTrue(showRadioCard(MediaResult(false, null), own))
        } finally {
            RadioState.current.value = null
            RadioState.playing.value = false
        }
    }

    @Test
    fun la_100_nunca_se_oculta() {
        assertTrue("La 100" in RadioCatalog.PINNED)
        assertEquals("FM 95.5 · San Miguel de Tucumán", TucumanFm.stations.first { it.name == "La 100" }.detail)
    }

    @Test
    fun cadenas_sin_senal_de_tucuman_usan_la_nacional() {
        for (n in listOf("Radio Mitre Tucumán", "Radio 10", "Vale Radio")) {
            val url = TucumanFm.stations.first { it.name == n }.url
            assertTrue(n, radioCandidates(url, national = false).any { it.startsWith("nac:https://") })
        }
        assertFalse(TucumanFm.stations.any { "buecrplb01" in it.url })
    }

    @Test
    fun sin_enlaces_de_audio_devuelve_null() {
        assertNull(RadioDirectory.extractStream("<html>nada</html>"))
    }

    // --- Listas .m3u / .pls ---

    @Test
    fun primer_enlace_de_m3u_y_pls() {
        assertEquals("http://a/stream", firstStreamInPlaylist("#EXTM3U\n#EXTINF:-1,Radio\nhttp://a/stream\nhttp://b"))
        assertEquals("https://c/live", firstStreamInPlaylist("[playlist]\nNumberOfEntries=1\nFile1=https://c/live\nTitle1=X"))
        assertNull(firstStreamInPlaylist("[playlist]\nNumberOfEntries=0"))
        assertEquals("http://d/live?id=5", firstStreamInPlaylist("http://d/live?id=5"))
    }
}
