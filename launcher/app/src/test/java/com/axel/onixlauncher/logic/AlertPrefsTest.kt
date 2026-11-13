package com.axel.onixlauncher.logic

import com.axel.onixlauncher.AlertPrefs
import com.axel.onixlauncher.SoundMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.78.0: sonido como Google Maps y Waze (todo / solo avisos / silencio) y cada aviso por separado. */
class AlertPrefsTest {

    @Test
    fun tres_modos_que_se_recorren_con_el_boton() {
        assertEquals(SoundMode.ALERTS, AlertPrefs.next(SoundMode.ALL))
        assertEquals(SoundMode.MUTE, AlertPrefs.next(SoundMode.ALERTS))
        assertEquals(SoundMode.ALL, AlertPrefs.next(SoundMode.MUTE))
    }

    @Test
    fun que_suena_en_cada_modo() {
        assertTrue(AlertPrefs.guidanceVoice(SoundMode.ALL))
        assertFalse(AlertPrefs.guidanceVoice(SoundMode.ALERTS))
        assertFalse(AlertPrefs.guidanceVoice(SoundMode.MUTE))
        assertTrue(AlertPrefs.alertSound(SoundMode.ALL))
        assertTrue(AlertPrefs.alertSound(SoundMode.ALERTS))
        assertFalse(AlertPrefs.alertSound(SoundMode.MUTE))
    }

    @Test
    fun se_guarda_y_se_lee() {
        assertEquals(SoundMode.ALERTS, AlertPrefs.parseMode("ALERTS"))
        assertEquals(SoundMode.ALL, AlertPrefs.parseMode(null))
        assertEquals(SoundMode.ALL, AlertPrefs.parseMode("otra cosa"))
    }
}
