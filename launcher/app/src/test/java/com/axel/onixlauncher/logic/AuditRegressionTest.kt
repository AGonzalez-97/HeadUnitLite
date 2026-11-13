package com.axel.onixlauncher.logic

import com.axel.onixlauncher.RadioDirectory
import com.axel.onixlauncher.RadioService
import com.axel.onixlauncher.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regresiones de la revisión técnica de v0.38.1 (E1 y E2 del informe). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuditRegressionTest {

    /** E1: una emisora del dial sin frecuencia ya no cierra la app al mezclar con el directorio. */
    @Test
    fun merge_con_emisora_del_dial_sin_frecuencia() {
        val dial = listOf(Station("Radio Online Tucumán", "", "Solo por internet"))
        val extra = listOf(Station("Otra FM 90.1", "https://otra/stream", "Tucumán"))
        val out = RadioDirectory.merge(dial, extra)
        assertEquals("Radio Online Tucumán", out[0].name)
        assertEquals("", out[0].url)   // no se le asigna la de otra emisora
    }

    /** E1: sin frecuencia, sigue mezclando por nombre. */
    @Test
    fun merge_sin_frecuencia_mezcla_por_nombre() {
        val dial = listOf(Station("Radio Online Tucumán", "", "Solo por internet"))
        val extra = listOf(Station("Radio Online Tucumán", "https://online/stream", "Tucumán"))
        assertEquals("https://online/stream", RadioDirectory.merge(dial, extra)[0].url)
    }

    /** E2: las órdenes propias del servicio de radio solo se aceptan con la clave de la app. */
    @Test
    fun servicio_de_radio_rechaza_ordenes_de_otras_apps() {
        assertFalse(RadioService.accepts(RadioService.ACTION_PLAY, null, "clave"))
        assertFalse(RadioService.accepts(RadioService.ACTION_STOP, "otra", "clave"))
        assertTrue(RadioService.accepts(RadioService.ACTION_TOGGLE, "clave", "clave"))
        // Los controles del sistema (Media3, botones multimedia) no llevan clave y siguen andando
        assertTrue(RadioService.accepts("android.intent.action.MEDIA_BUTTON", null, "clave"))
        assertTrue(RadioService.accepts(null, null, "clave"))
    }
}
