package com.axel.onixlauncher.logic

import com.axel.onixlauncher.LightsMode
import com.axel.onixlauncher.LightsReminder
import com.axel.onixlauncher.lightsLabel
import org.junit.Assert.assertEquals
import com.axel.onixlauncher.RepeatWatch
import com.axel.onixlauncher.SpeedWarning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aviso de luces apagadas andando (v0.50.0). */
class SafetyAlertsTest {

    /**
     * v0.79.7 (pedido del autor): acá es obligatorio andar con las luces prendidas, de día y de noche y a
     * cualquier velocidad ("aunque vaya a 2 km/h"). Antes avisaba desde 20 km/h de noche y desde 60 km/h de día.
     */
    @Test
    fun avisaAndandoSinLucesAunqueVayaDespacioYSeaDeDia() {
        for (kmh in listOf(2, 3, 5, 10, 20, 40, 80, 130)) {
            assertTrue("$kmh km/h", LightsReminder.needed(kmh, lightsKnown = true, lightsOn = false))
        }
    }

    @Test
    fun noAvisaSinNecesidad() {
        assertFalse(LightsReminder.needed(60, lightsKnown = true, lightsOn = true))    // prendidas
        assertFalse(LightsReminder.needed(1, lightsKnown = true, lightsOn = false))    // ruido del GPS con el auto parado
        assertFalse(LightsReminder.needed(0, lightsKnown = true, lightsOn = false))    // parado
        assertFalse(LightsReminder.needed(null, lightsKnown = true, lightsOn = false))
        assertFalse(LightsReminder.needed(60, lightsKnown = false, lightsOn = false))  // sin señal de la radio: no se sabe
    }

    /** Tiene que avisar apenas empieza a andar, no a los 15 s (a 2 km/h no habría llegado ni a la esquina). */
    @Test
    fun avisaApenasEmpiezaAAndar() {
        val w = RepeatWatch(LightsReminder.HOLD_MS, LightsReminder.REPEAT_MS)
        assertFalse(w.update(true, 0))
        assertFalse(w.update(true, LightsReminder.HOLD_MS - 1))
        assertTrue(w.update(true, LightsReminder.HOLD_MS))
        assertEquals(5_000L, LightsReminder.HOLD_MS)
    }

    @Test
    fun esperaYNoRepiteEnCadaSemaforo() {
        val w = RepeatWatch(holdMs = 15_000, repeatMs = 600_000)
        assertFalse(w.update(true, 0))
        assertFalse(w.update(true, 14_000))
        assertTrue(w.update(true, 15_000))           // avisa a los 15 s
        assertFalse(w.update(true, 16_000))          // una sola vez
        assertFalse(w.update(false, 60_000))         // semáforo
        assertFalse(w.update(true, 61_000))
        assertFalse(w.update(true, 120_000))         // pasaron los 15 s pero no los 10 min
        assertTrue(w.update(true, 615_000))          // 10 min después del primero: repite
    }

    @Test
    fun franjaMuestraElEstadoDeLasLuces() {
        assertEquals("Luces prendidas", lightsLabel(true, LightsMode.RADIO))
        assertEquals("Luces apagadas", lightsLabel(false, LightsMode.RADIO))
        assertEquals("Luces prendidas (encendidas)", lightsLabel(true, LightsMode.ON))
    }

    /** v0.63.0: aviso de velocidad, apagado de fábrica; avisa pasando el máximo elegido por más de 3 km/h. */
    @Test
    fun avisoDeVelocidad() {
        assertFalse(SpeedWarning.needed(150, null))          // apagado
        assertFalse(SpeedWarning.needed(null, 110))          // sin velocidad
        assertFalse(SpeedWarning.needed(113, 110))           // dentro de la tolerancia
        assertTrue(SpeedWarning.needed(114, 110))
        assertEquals(listOf(null, 60, 80, 100, 110, 120, 130), SpeedWarning.CHOICES)
        assertEquals(60, SpeedWarning.next(null))
        assertEquals(null, SpeedWarning.next(130))
        assertEquals("Aviso de velocidad: no", SpeedWarning.label(null))
        assertEquals("Aviso de velocidad: 110", SpeedWarning.label(110))
    }
}

