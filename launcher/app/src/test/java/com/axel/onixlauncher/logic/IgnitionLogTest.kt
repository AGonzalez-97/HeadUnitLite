package com.axel.onixlauncher.logic

import com.axel.onixlauncher.AppLog
import com.axel.onixlauncher.IgnitionNet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.79.16: el registro del encendido no se pierde con los errores de red y se anota la red al encender. */
class IgnitionLogTest {

    @Test
    fun las_lineas_del_encendido_se_reconocen() {
        assertTrue(AppLog.isKeyEvent("Contacto: la radio avisó estado=1"))
        assertTrue(AppLog.isKeyEvent("Al encender: fuente de la radio 0; devuelvo el audio a Android (spotify)"))
        assertTrue(AppLog.isKeyEvent("Red al encender (+10 s): sin conexión"))
        assertFalse(AppLog.isKeyEvent("Sin respuesta de api.tomtom.com (UnknownHostException)"))
    }

    @Test
    fun el_encendido_sobrevive_a_cientos_de_errores_de_red() {
        AppLog.info("Contacto: la radio avisó estado=1 (prueba)")
        repeat(300) { AppLog.warn("Sin respuesta de api.tomtom.com $it") }
        assertTrue("los 80 eventos ya no lo tienen", AppLog.recent().none { it.contains("Contacto: la radio avisó estado=1 (prueba)") })
        assertTrue("el registro aparte sí", AppLog.recentKey().any { it.contains("Contacto: la radio avisó estado=1 (prueba)") })
        assertTrue("sin errores de red mezclados", AppLog.recentKey().none { it.contains("Sin respuesta") })
    }

    @Test
    fun red_al_encender_en_palabras() {
        assertEquals("Red al encender (+0 s): sin conexión", IgnitionNet.describe(0, false, null))
        assertEquals("Red al encender (+30 s): WIFI conectada", IgnitionNet.describe(30, true, "WIFI"))
        assertEquals("Red al encender (+60 s): red conectada", IgnitionNet.describe(60, true, null))
        assertEquals(listOf(0, 10, 30, 60), IgnitionNet.CHECKS_S)
    }
}
