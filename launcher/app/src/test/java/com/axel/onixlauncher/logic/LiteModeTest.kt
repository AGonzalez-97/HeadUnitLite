package com.axel.onixlauncher.logic

import com.axel.onixlauncher.LiteMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo liviano (v0.58.0). */
class LiteModeTest {

    @Test
    fun radioDelAutorEsGamaBaja() {
        // Allwinner T3: 32 bits, 4 núcleos
        assertTrue(LiteMode.isLowEnd(has64Bit = false, cores = 4, ramMb = 2048))
        assertTrue(LiteMode.isLowEnd(has64Bit = true, cores = 4, ramMb = 4096))
        assertTrue(LiteMode.isLowEnd(has64Bit = true, cores = 8, ramMb = 2000))
        assertFalse(LiteMode.isLowEnd(has64Bit = true, cores = 8, ramMb = 8192))
        assertFalse(LiteMode.isLowEnd(has64Bit = true, cores = 8, ramMb = 0))   // memoria desconocida: no decide
    }

    @Test
    fun eleccionManda() {
        assertTrue(LiteMode.on(LiteMode.Choice.AUTO, lowEnd = true))
        assertFalse(LiteMode.on(LiteMode.Choice.AUTO, lowEnd = false))
        assertTrue(LiteMode.on(LiteMode.Choice.ON, lowEnd = false))
        assertFalse(LiteMode.on(LiteMode.Choice.OFF, lowEnd = true))
    }
}
