package com.axel.onixlauncher.logic

import com.axel.onixlauncher.TunerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Radio FM por la antena (v0.51.0): conversiones del sintonizador de la radio NWD. */
class TunerMathTest {

    @Test
    fun frecuenciaComoLaPideElServicio() {
        assertEquals(9550, TunerMath.toService(95.5f))
        assertEquals(10170, TunerMath.toService(101.7f))
        assertEquals(9610, TunerMath.toService(96.14f))          // redondea a pasos de 0,1
        assertEquals(8750, TunerMath.toService(80f))             // fuera de rango: al mínimo
        assertEquals(10800, TunerMath.toService(110f))
    }

    @Test
    fun frecuenciaComoLaDaElServicio() {
        assertEquals(95.5f, TunerMath.toMhz(9550), 0.001f)
        assertEquals(96.1f, TunerMath.toMhz(9610), 0.001f)
        assertEquals("104.9", TunerMath.label(TunerMath.toMhz(10490)))
    }

    @Test
    fun bandasYFuente() {
        assertTrue(TunerMath.isFm(0))
        assertTrue(TunerMath.isFm(7))
        assertFalse(TunerMath.isFm(3))                           // AM1
        assertTrue(TunerMath.isRadioSource(4))
        assertFalse(TunerMath.isRadioSource(-128))
    }

    @Test
    fun conLaAntenaElReproductorDeInternetNoPisaElEstado() {
        com.axel.onixlauncher.RadioState.note.value = com.axel.onixlauncher.ANTENNA_NOTE
        assertTrue(com.axel.onixlauncher.antennaActive())
        com.axel.onixlauncher.RadioState.note.value = "SEÑAL NACIONAL"
        assertFalse(com.axel.onixlauncher.antennaActive())
        com.axel.onixlauncher.RadioState.note.value = null
    }
}
