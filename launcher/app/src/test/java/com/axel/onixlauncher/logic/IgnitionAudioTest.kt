package com.axel.onixlauncher.logic

import com.axel.onixlauncher.IgnitionAudio
import com.axel.onixlauncher.LastAudio
import com.axel.onixlauncher.TunerMath
import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.79.4: al poner contacto, la fuente de audio de la radio vuelve a Android si sonaba Spotify o internet. */
class IgnitionAudioTest {
    @Test
    fun devuelve_la_fuente_a_android_salvo_con_el_fm_por_antena_o_en_pausa() {
        assertEquals(true, IgnitionAudio.shouldRestore("spotify", 0))                       // la radio quedó sin fuente
        assertEquals(true, IgnitionAudio.shouldRestore("radio:La 100", 0))
        assertEquals(true, IgnitionAudio.shouldRestore("spotify", TunerMath.SOURCE_ANDROID)) // igual se pide: destraba
        assertEquals(false, IgnitionAudio.shouldRestore("spotify", TunerMath.SOURCE_RADIO))  // el FM por antena es del usuario
        assertEquals(false, IgnitionAudio.shouldRestore(null, 0))                           // el usuario había pausado
    }

    // v0.79.13: after ignition Spotify can play without sound until its app is opened. The report shows
    // the radio only lets out the audio of the process it calls "valid" (sys.nwd.valid.play.pid); with
    // Spotify mute the valid pid was another one (7155) and the playing pid was Spotify's (13442).
    @Test
    fun pide_primer_plano_si_el_que_suena_no_es_el_pid_valido() {
        assertEquals(true, IgnitionAudio.needsForeground("7155", "13442"))              // informe 08:56, mudo
        assertEquals(true, IgnitionAudio.needsForeground("7155", "11040"))              // informe 10:37, mudo
        assertEquals(false, IgnitionAudio.needsForeground("14810", "14810"))            // informe 12:09, sonando
        assertEquals(false, IgnitionAudio.needsForeground("13271", "13271"))
        assertEquals(false, IgnitionAudio.needsForeground("7155", ""))                  // no suena nada
        assertEquals(false, IgnitionAudio.needsForeground("7155", null))
        assertEquals(true, IgnitionAudio.needsForeground("", "13442"))                  // sin pid válido
        assertEquals(false, IgnitionAudio.needsForeground("14810", "14810,20001"))      // el válido está entre los que suenan
        // v0.79.20 (informes 13:27 y 13:28): si la sesión de Spotify quedó en sonando=false, también pide primer plano
        assertEquals(true, IgnitionAudio.needsForeground("14810", "14810", sessionPlaying = false))
        assertEquals(true, IgnitionAudio.needsForeground("", "", sessionPlaying = false))
    }

    // v0.81.16: the radio does not kill the apps listed in NaviSound.ini at wake (firmware rule read
    // from NwdAudioService). With Spotify listed, its pid stayed 9938 across an ignition cycle and it
    // played with an empty valid pid, so bringing it to the front is not needed.
    @Test
    fun con_spotify_en_la_lista_de_la_radio_no_hace_falta_el_primer_plano() {
        val ini = "#navi package name   |  allow adjust navi volume\r\n" +
            "com.waze                                    true    appset\r\n" +
            "com.spotify.music                           true    appset\n"
        assertEquals(true, IgnitionAudio.radioProtects(ini, "com.spotify.music"))
        assertEquals(false, IgnitionAudio.radioProtects(ini, "com.spotify"))                 // whole name only
        assertEquals(false, IgnitionAudio.radioProtects("#com.spotify.music true appset", "com.spotify.music"))
        assertEquals(false, IgnitionAudio.radioProtects("com.waze true appset", "com.spotify.music"))
        assertEquals(false, IgnitionAudio.radioProtects(null, "com.spotify.music"))          // file not readable
    }

    // v0.81.18: "Retomar la música al dar contacto" can be turned off; then the launcher never
    // sends play by itself. With Android Auto / CarPlay active the audio belongs to the phone.
    @Test
    fun da_play_solo_si_el_ajuste_esta_prendido_y_no_hay_celular_proyectado() {
        assertEquals(true, LastAudio.mayAutoPlay(enabled = true, projection = false))
        assertEquals(false, LastAudio.mayAutoPlay(enabled = false, projection = false))
        assertEquals(false, LastAudio.mayAutoPlay(enabled = true, projection = true))
        assertEquals(false, LastAudio.mayAutoPlay(enabled = false, projection = true))
    }

    @Test
    fun reinicia_solo_si_spotify_era_lo_ultimo_aunque_figure_en_pausa() {
        assertEquals(true, IgnitionAudio.shouldKickSpotify("spotify", true))
        assertEquals(true, IgnitionAudio.shouldKickSpotify("spotify", false))
        assertEquals(false, IgnitionAudio.shouldKickSpotify("radio:La 100", true))
        assertEquals(false, IgnitionAudio.shouldKickSpotify(null, true))
    }
}
