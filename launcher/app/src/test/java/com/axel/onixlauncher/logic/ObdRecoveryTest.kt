package com.axel.onixlauncher.logic

import com.axel.onixlauncher.ObdRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.73.0: el OBD2 se recupera solo (tiempo máximo, reinicio del Bluetooth, reintento al dar contacto). */
class ObdRecoveryTest {

    /**
     * v0.81.14: cada intento le ocupa a la radio NWD un lugar de conexión que no libera (registro del
     * auto, 2026-11-11), así que se reintenta poco: 15 s, 30 s, 1, 2, 5 y después cada 10 minutos.
     */
    @Test
    fun reintenta_cada_vez_mas_espaciado() {
        assertEquals(15_000L, ObdRecovery.retryDelayMs(1))
        assertEquals(30_000L, ObdRecovery.retryDelayMs(2))
        assertEquals(60_000L, ObdRecovery.retryDelayMs(3))
        assertEquals(120_000L, ObdRecovery.retryDelayMs(4))
        assertEquals(300_000L, ObdRecovery.retryDelayMs(5))
        assertEquals(600_000L, ObdRecovery.retryDelayMs(6))
        assertEquals(600_000L, ObdRecovery.retryDelayMs(50))
        assertEquals(15_000L, ObdRecovery.retryDelayMs(0))
    }

    /** v0.81.14: tras una falla, la vuelta siguiente prueba la otra manera de conectar (una por vuelta). */
    @Test
    fun la_vuelta_siguiente_prueba_otra_manera() {
        assertEquals(0, ObdRecovery.nextWay(2, 3))
        assertEquals(1, ObdRecovery.nextWay(0, 3))
        assertEquals(2, ObdRecovery.nextWay(1, 3))
        assertEquals(0, ObdRecovery.nextWay(7, 3))
    }

    /** v0.81.14: el estado dice cuánto falta en minutos cuando la espera es larga. */
    @Test
    fun la_espera_se_dice_en_segundos_o_minutos() {
        assertEquals("15 s", ObdRecovery.waitText(15_000L))
        assertEquals("30 s", ObdRecovery.waitText(30_000L))
        assertEquals("1 min", ObdRecovery.waitText(60_000L))
        assertEquals("10 min", ObdRecovery.waitText(600_000L))
    }

    @Test
    fun contacto_y_pantalla_disparan_un_reintento() {
        assertTrue(ObdRecovery.isWakeSignal("com.nwd.action.ACTION_MCU_STATE_CHANGE", 1))
        assertFalse(ObdRecovery.isWakeSignal("com.nwd.action.ACTION_MCU_STATE_CHANGE", 3))
        assertTrue(ObdRecovery.isWakeSignal("android.intent.action.SCREEN_ON", null))
        assertFalse(ObdRecovery.isWakeSignal("otra.cosa", 1))
    }

    /**
     * v0.81.13: un aviso de contacto puesto no corta una conexión que anda. Solo se espera a que la
     * radio levante su Bluetooth si venía de contacto apagado o si no hay conexión abierta.
     */
    @Test
    fun el_contacto_puesto_no_corta_una_conexion_sana() {
        assertTrue(ObdRecovery.settlesAfterWake(mcuWasOff = true, hasLink = false))
        assertTrue(ObdRecovery.settlesAfterWake(mcuWasOff = false, hasLink = false))
        assertTrue(ObdRecovery.settlesAfterWake(mcuWasOff = true, hasLink = true))
        assertFalse(ObdRecovery.settlesAfterWake(mcuWasOff = false, hasLink = true))
    }

    @Test
    fun el_corte_del_lector_no_pone_en_cero_las_fallas_pero_el_contacto_y_la_pantalla_si() {
        // v0.81.10: cerrar un intento fallido desconecta el lector; si eso reiniciaba la cuenta,
        // nunca se llegaba a las 3 fallas y el reinicio del Bluetooth no corría
        assertFalse(ObdRecovery.restartsRetries("android.bluetooth.device.action.ACL_DISCONNECTED", null))
        assertTrue(ObdRecovery.restartsRetries("com.nwd.action.ACTION_MCU_STATE_CHANGE", 1))
        assertTrue(ObdRecovery.restartsRetries("com.nwd.action.ACTION_MCU_STATE_CHANGE", 0))
        assertFalse(ObdRecovery.restartsRetries("com.nwd.action.ACTION_MCU_STATE_CHANGE", 3))
        assertTrue(ObdRecovery.restartsRetries("android.intent.action.SCREEN_ON", null))
        assertFalse(ObdRecovery.restartsRetries("android.bluetooth.adapter.action.STATE_CHANGED", null))
    }

    @Test
    fun anota_lo_que_hace_el_bluetooth() {
        assertEquals("Bluetooth: alguien empezó a buscar equipos (puede cortar el lector)",
            ObdRecovery.bluetoothEvent("android.bluetooth.adapter.action.DISCOVERY_STARTED", -1, null))
        assertEquals("Bluetooth: se desconectó OBDII", ObdRecovery.bluetoothEvent("android.bluetooth.device.action.ACL_DISCONNECTED", -1, "OBDII"))
        assertEquals("Bluetooth: apagado", ObdRecovery.bluetoothEvent("android.bluetooth.adapter.action.STATE_CHANGED", 10, null))
        assertEquals(null, ObdRecovery.bluetoothEvent("android.bluetooth.adapter.action.STATE_CHANGED", 11, null))
        assertEquals(null, ObdRecovery.bluetoothEvent("android.intent.action.SCREEN_ON", -1, null))
    }

    @Test
    fun conexion_trabada_se_corta_y_cuenta_como_falla() {
        assertEquals(false, ObdRecovery.isStuck(lastProgressMs = 0, nowMs = 19_999))
        assertEquals(true, ObdRecovery.isStuck(lastProgressMs = 0, nowMs = ObdRecovery.STUCK_MS))
        // Un connect que no vuelve: a los [ms] se llama a onTimeout y sale una excepción
        var closed = false
        val e = runCatching {
            ObdRecovery.withHardTimeout(200, "conexión con el lector", onTimeout = { closed = true }) { Thread.sleep(5_000); "nunca" }
        }.exceptionOrNull()
        assertEquals(true, closed)
        assertEquals("conexión con el lector: no respondió en 0 s (trabado)", e?.message)
        // Lo que termina a tiempo devuelve su valor, y su error sale tal cual
        assertEquals("ok", ObdRecovery.withHardTimeout(2_000, "x") { "ok" })
        assertEquals("roto", runCatching { ObdRecovery.withHardTimeout<String>(2_000, "x") { throw java.io.IOException("roto") } }.exceptionOrNull()?.message)
    }

    /** AUD-072: cerrar la pantalla no es una falla del lector (no suma para el reinicio del Bluetooth). */
    @Test
    fun la_cancelacion_no_cuenta_como_falla_del_lector() {
        assertFalse(ObdRecovery.isReaderFailure(kotlinx.coroutines.CancellationException("pantalla cerrada")))
        assertTrue(ObdRecovery.isReaderFailure(IllegalStateException("el lector no responde")))
        assertTrue(ObdRecovery.isReaderFailure(java.io.IOException("socket cerrado")))
        // Un tiempo agotado con withTimeout sí es una falla (es una CancellationException, pero no se cerró nada)
        val timeout = runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.delay(1_000) } } }.exceptionOrNull()!!
        assertTrue(ObdRecovery.isReaderFailure(timeout))
    }

    /** AUD-080: una conexión que llega después del tope se cierra (no queda un canal Bluetooth tomado). */
    @Test
    fun la_conexion_que_llega_tarde_se_cierra() {
        val late = java.util.concurrent.CountDownLatch(1)
        var closed: String? = null
        val e = runCatching {
            ObdRecovery.withHardTimeout(100, "conexión con el lector", onLate = { closed = it; late.countDown() }) {
                // Como BluetoothSocket.connect(): no se corta con interrupt, termina cuando termina
                val end = System.nanoTime() + 300_000_000L
                while (System.nanoTime() < end) Thread.onSpinWait()
                "socket"
            }
        }.exceptionOrNull()
        assertEquals("conexión con el lector: no respondió en 0 s (trabado)", e?.message)
        assertTrue("el resultado tardío tiene que llegar a onLate", late.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals("socket", closed)
        // A tiempo: onLate no se llama
        var called = false
        assertEquals("ok", ObdRecovery.withHardTimeout(2_000, "x", onLate = { called = true }) { "ok" })
        assertFalse(called)
    }

    /** v0.81.12: el motor del auto arranca una sola vez por proceso, aunque la pantalla se vuelva a crear. */
    @Test
    fun el_motor_del_auto_arranca_una_sola_vez() {
        val gate = com.axel.onixlauncher.StartOnce()
        var starts = 0
        assertTrue(gate.run { starts++ })
        assertFalse(gate.run { starts++ })
        assertFalse(gate.run { starts++ })
        assertEquals(1, starts)
        // Si el arranque falla, el siguiente pedido lo vuelve a intentar
        val retry = com.axel.onixlauncher.StartOnce()
        assertTrue(runCatching { retry.run { error("sin Bluetooth") } }.isFailure)
        assertTrue(retry.run { starts++ })
        assertEquals(2, starts)
    }

    /** v0.81.13: la manera de conectar que funcionó se recuerda entre arranques; un valor roto vuelve a la de siempre. */
    @Test
    fun la_manera_de_conectar_guardada_se_valida() {
        assertEquals(0, ObdRecovery.savedWay(0))
        assertEquals(1, ObdRecovery.savedWay(1))
        assertEquals(2, ObdRecovery.savedWay(2))
        assertEquals(ObdRecovery.DEFAULT_PREFERRED_WAY, ObdRecovery.savedWay(-1))
        assertEquals(ObdRecovery.DEFAULT_PREFERRED_WAY, ObdRecovery.savedWay(7))
    }

    /** v0.81.13: el motor de Android Auto y CarPlay no trata al lector OBD2 como un teléfono. */
    @Test
    fun el_motor_de_proyeccion_ignora_al_lector() {
        val c = com.carlink.core.detect.PhoneClassifier
        assertTrue(c.isDiagnosticReader("OBDII"))
        assertTrue(c.isDiagnosticReader("obd2 ELM327"))
        assertTrue(c.isDiagnosticReader("V-LINK"))
        assertTrue(c.isDiagnosticReader("Vgate iCar"))
        assertFalse(c.isDiagnosticReader("iPhone de Axel"))
        assertFalse(c.isDiagnosticReader("Galaxy S23"))
        assertFalse(c.isDiagnosticReader(null))
        // La misma lista que usa Onix para encontrar el lector
        for (name in listOf("OBDII", "ELM327", "V-LINK", "VGATE", "iCar", "KONNWEI", "Veepeak", "Scan Tool", "Pixel 8", null)) {
            assertEquals(name, com.axel.onixlauncher.ObdParse.looksLikeObd(name), c.isDiagnosticReader(name))
        }
    }
}
