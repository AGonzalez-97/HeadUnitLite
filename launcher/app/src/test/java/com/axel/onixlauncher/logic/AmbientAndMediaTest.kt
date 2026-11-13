package com.axel.onixlauncher.logic

import com.axel.onixlauncher.AboutInfo
import com.axel.onixlauncher.Ambient
import com.axel.onixlauncher.LastAudio
import com.axel.onixlauncher.LightsMode
import com.axel.onixlauncher.NwdSignals
import com.axel.onixlauncher.RadioIll
import com.axel.onixlauncher.ScreenBrightness
import com.axel.onixlauncher.lightsOn
import com.axel.onixlauncher.MediaInfo
import com.axel.onixlauncher.Net
import com.axel.onixlauncher.Weather
import com.axel.onixlauncher.ambientFor
import com.axel.onixlauncher.greetingFor
import com.axel.onixlauncher.IntroTiming
import com.axel.onixlauncher.GreetingName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ambiente por hora, estados del clima, posición de la música, utilidades y autoría. */
class AmbientAndMediaTest {

    @Test
    fun ambiente_segun_la_hora() {
        assertEquals(Ambient.NOCHE, ambientFor(5.9f))
        assertEquals(Ambient.ATARDECER, ambientFor(6f))
        assertEquals(Ambient.DIA, ambientFor(7.5f))
        assertEquals(Ambient.DIA, ambientFor(17.49f))
        assertEquals(Ambient.ATARDECER, ambientFor(17.5f))
        assertEquals(Ambient.NOCHE, ambientFor(19.5f))
        assertEquals(Ambient.NOCHE, ambientFor(0f))
    }

    @Test
    fun luces_segun_la_radio_siguen_el_modo_noche_y_no_la_hora() {
        assertTrue(lightsOn(LightsMode.RADIO, systemNight = true, ambient = Ambient.DIA))
        assertFalse(lightsOn(LightsMode.RADIO, systemNight = false, ambient = Ambient.NOCHE))
    }

    @Test
    fun luces_manuales_y_por_hora() {
        assertTrue(lightsOn(LightsMode.ON, false, Ambient.DIA))
        assertFalse(lightsOn(LightsMode.OFF, true, Ambient.NOCHE))
        assertTrue(lightsOn(LightsMode.HORA, false, Ambient.ATARDECER))
        assertFalse(lightsOn(LightsMode.HORA, true, Ambient.DIA))
        assertEquals(LightsMode.ON, LightsMode.RADIO.next())
        assertEquals(LightsMode.RADIO, LightsMode.HORA.next())
    }

    @Test
    fun avisos_de_la_radio_en_texto() {
        assertEquals(
            "ACTION_SYSTEM_VALUE_CHANGE · a=1, b=x",
            NwdSignals.describe("com.nwd.action.ACTION_SYSTEM_VALUE_CHANGE", mapOf("b" to "x", "a" to 1))
        )
    }

    @Test
    fun que_se_retoma_al_encender() {
        assertEquals("spotify", LastAudio.decide("spotify", null))
        assertEquals("radio:La 100", LastAudio.decide("radio", "La 100"))
        assertEquals(null, LastAudio.decide("radio", ""))
        assertEquals(null, LastAudio.decide(null, "La 100"))
    }

    @Test
    fun codigos_de_lluvia() {
        for (code in listOf(51, 61, 67, 80, 82, 95, 99)) assertTrue("código $code", Weather(20, code).raining)
        for (code in listOf(0, 1, 2, 3, 45, 71, 77)) assertFalse("código $code", Weather(20, code).raining)
    }

    @Test
    fun etiquetas_cortas_del_clima() {
        assertEquals("Despejado", Weather(20, 0).shortLabel)
        assertEquals("Lluvia", Weather(20, 63).shortLabel)
        assertEquals("Tormenta", Weather(20, 95).shortLabel)
        assertEquals("Noche despejada", Weather(20, 0, isDay = false).label)
    }

    private fun info(playing: Boolean, pos: Long, dur: Long) = MediaInfo(
        pkg = "com.spotify.music", title = "T", artist = "A", art = null, playing = playing,
        positionMs = pos, durationMs = dur, controller = null, readAt = 10_000L
    )

    @Test
    fun la_posicion_avanza_solo_si_suena() {
        assertEquals(35_000L, info(true, 30_000L, 200_000L).positionAt(15_000L))
        assertEquals(30_000L, info(false, 30_000L, 200_000L).positionAt(15_000L))
    }

    @Test
    fun la_posicion_no_pasa_la_duracion() {
        assertEquals(200_000L, info(true, 199_000L, 200_000L).positionAt(20_000L))
    }

    @Test
    fun host_de_un_enlace() {
        assertEquals("api.open-meteo.com", Net.host("https://API.open-meteo.com/v1/forecast?x=1"))
        assertEquals("", Net.host("::no"))
    }

    @Test
    fun autoria_y_atribucion_de_open_meteo() {
        assertEquals("Axel Hernán González", AboutInfo.AUTHOR)
        assertTrue(AboutInfo.COPYRIGHT.startsWith("© 2026 Axel Hernán González"))
        assertTrue(AboutInfo.credits.any { "Open-Meteo" in it.second && "CC BY 4.0" in it.second })
    }

    @Test
    fun lluviaSigueLaHoraDelDia() {
        assertEquals(Ambient.LLUVIA, ambientFor(12f, raining = true))
        assertEquals(Ambient.LLUVIA_ATARDECER, ambientFor(18f, raining = true))
        assertEquals(Ambient.LLUVIA_NOCHE, ambientFor(23f, raining = true))
        assertEquals(Ambient.LLUVIA_ATARDECER, ambientFor(6.5f, raining = true))
        assertEquals(Ambient.NOCHE, Ambient.LLUVIA_NOCHE.timeOfDay)
        assertTrue(Ambient.LLUVIA_NOCHE.isNight && Ambient.LLUVIA_NOCHE.raining)
        assertFalse(Ambient.ATARDECER.raining)
    }

    @Test
    fun saludoSegunLaHora() {
        assertEquals("Buenas noches", greetingFor(5.9f))
        assertEquals("Buenos días", greetingFor(6f))
        assertEquals("Buenas tardes", greetingFor(12f))
        assertEquals("Buenas tardes", greetingFor(19.9f))
        assertEquals("Buenas noches", greetingFor(20f))
    }

    @Test
    fun tramosDeLaPresentacion() {
        assertEquals(0f, IntroTiming.phase(0.1f, 0.4f, 0.7f), 0f)
        assertEquals(0.5f, IntroTiming.phase(0.55f, 0.4f, 0.7f), 1e-4f)
        assertEquals(1f, IntroTiming.phase(0.9f, 0.4f, 0.7f), 0f)
    }

    @Test
    fun nombreDelSaludo() {
        assertEquals("Atalia", GreetingName.clean("  Atalia "))
        assertEquals("Ana María", GreetingName.clean("Ana   María"))
        assertEquals(null, GreetingName.clean("   "))
        assertEquals(listOf("Atalia", "Axel"), GreetingName.addRecent(listOf("Axel"), "Atalia"))
        assertEquals(listOf("axel", "Atalia"), GreetingName.addRecent(listOf("Atalia", "Axel"), "axel"))
        assertEquals(5, GreetingName.addRecent(listOf("a", "b", "c", "d", "e"), "f").size)
    }

    @Test
    fun cambiosDeAjustesDeLuces() {
        val before = mapOf("mcu_back_light_state" to "1", "screen_brightness" to "240")
        val after = mapOf("mcu_back_light_state" to "0", "screen_brightness" to "240", "key_cur_daynight_color" to "2")
        assertEquals(
            listOf("key_cur_daynight_color: (no estaba) → 2", "mcu_back_light_state: 1 → 0"),
            NwdSignals.diff(before, after)
        )
        assertTrue(NwdSignals.diff(before, before).isEmpty())
    }

    @Test
    fun leeGetprop() {
        val text = "[sys.nwd.bt_call]: [end]\n[ro.product.model]: [k2001-nwd]\nbasura\n[sys.nwd.ill]: []"
        val m = NwdSignals.parseGetprop(text)
        assertEquals("end", m["sys.nwd.bt_call"])
        assertEquals("", m["sys.nwd.ill"])
        assertEquals(3, m.size)
    }

    @Test
    fun senalDeLucesDeLaRadio() {
        assertTrue(RadioIll.parse("1"))
        assertFalse(RadioIll.parse("0"))
        assertFalse(RadioIll.parse(null))
        // Con la señal de la radio, el modo "según la radio" prende las luces de la app
        assertTrue(lightsOn(LightsMode.RADIO, systemNight = RadioIll.parse("1"), ambient = Ambient.DIA))
    }

    @Test
    fun ruidoDelRegistroDeAjustes() {
        assertTrue(NwdSignals.noisy("system/nwd_system_time: 1 → 2"))
        assertTrue(NwdSignals.noisy("sys.nwd.cur.pkg: a → b"))
        assertFalse(NwdSignals.noisy("system/high_beam_state: 0 → 1"))
        assertFalse(NwdSignals.noisy("system/screen_brightness: 240 → 120"))
    }

    @Test
    fun brilloDeLaVentana() {
        assertEquals(1f, ScreenBrightness.windowValue(true), 0f)
        assertEquals(-1f, ScreenBrightness.windowValue(false), 0f)
    }

    @Test
    fun lucesEnElEstadoDeLaRadio() {
        assertTrue(RadioIll.fromState("2"))      // 0 → 2 al prender las luces (informe 28/10 11:41:29)
        assertTrue(RadioIll.fromState("3"))
        assertFalse(RadioIll.fromState("0"))
        assertFalse(RadioIll.fromState("1"))
        assertFalse(RadioIll.fromState(null))
    }

    @Test
    fun teclaDelVolanteParaElInforme() {
        assertEquals(
            "TECLA · KEYCODE_MEDIA_NEXT (código 87, scan 163, fuente 257)",
            NwdSignals.keyLine(87, "KEYCODE_MEDIA_NEXT", 163, 257)
        )
    }
}
