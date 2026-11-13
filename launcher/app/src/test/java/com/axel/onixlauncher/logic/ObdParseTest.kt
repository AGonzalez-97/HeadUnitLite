package com.axel.onixlauncher.logic

import com.axel.onixlauncher.ObdParse
import com.axel.onixlauncher.ObdWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lectura de respuestas del ELM327 (OBD2). */
class ObdParseTest {

    @Test
    fun temperatura_rpm_velocidad_y_nafta() {
        assertEquals(83, ObdParse.coolant("41 05 7B"))
        assertEquals(1726, ObdParse.rpm("41 0C 1A F8"))
        assertEquals(60, ObdParse.speed("41 0D 3C"))
        assertEquals(50, ObdParse.fuel("41 2F 80"))
    }

    @Test
    fun respuestas_sin_espacios_con_busqueda_y_prompt() {
        assertEquals(90, ObdParse.coolant("SEARCHING...\r410582\r\r>"))
        assertEquals(800, ObdParse.rpm("410C0C80"))
    }

    @Test
    fun sin_datos_queda_null() {
        assertNull(ObdParse.fuel("NO DATA"))
        assertNull(ObdParse.rpm("41 0C 1A"))
        assertNull(ObdParse.coolant("?"))
        assertNull(ObdParse.speed(""))
    }

    /** AUD-085 (v0.79.6): un error del lector o del auto nunca se toma por un dato. */
    @Test
    fun errores_del_lector_y_respuestas_de_otro_pid_quedan_null() {
        for (raw in listOf("CAN ERROR", "BUS INIT: ERROR", "UNABLE TO CONNECT", "STOPPED", "7F 01 12", "7F 01 11\r\r>")) {
            assertNull(raw, ObdParse.rpm(raw))
            assertNull(raw, ObdParse.coolant(raw))
        }
        // la respuesta de la velocidad no es la de las rpm
        assertNull(ObdParse.rpm("41 0D 3C"))
        assertNull(ObdParse.speed("41 0C 1A F8"))
    }

    /** AUD-085: con varias ECU contestando gana la primera línea válida; con encabezados (ATH1) también anda. */
    @Test
    fun varias_ecu_y_encabezados() {
        assertEquals(1726, ObdParse.rpm("41 0C 1A F8\r41 0C 00 00\r\r>"))
        assertEquals(1726, ObdParse.rpm("7E8 04 41 0C 1A F8\r\r>"))
        assertEquals(60, ObdParse.speed("NO DATA\r7E8 03 41 0D 3C\r\r>"))
    }

    /** AUD-096 (v0.79.7): el informe se copia al portapapeles y a Descargas; de la dirección del lector solo se ve el último byte. */
    @Test
    fun la_direccion_bluetooth_sale_enmascarada() {
        assertEquals("XX:XX:XX:XX:XX:FF", ObdParse.maskAddress("AA:BB:CC:DD:EE:FF"))
        assertEquals("XX:XX:XX:XX:XX:0A", ObdParse.maskAddress("00:1d:a5:68:98:0a"))
        assertEquals("?", ObdParse.maskAddress(null))
        assertEquals("?", ObdParse.maskAddress("  "))
        // lo que no parece una dirección no se repite tal cual
        assertEquals("(dirección no reconocida)", ObdParse.maskAddress("lo-que-sea"))
    }

    @Test
    fun voltaje_de_atrv() {
        assertEquals(12.6f, ObdParse.volts("12.6V")!!, 0.001f)
        assertEquals(14.1f, ObdParse.volts("14.1 V\r>")!!, 0.001f)
        assertNull(ObdParse.volts("?"))
    }

    @Test
    fun nombres_de_lectores() {
        assertTrue(ObdParse.looksLikeObd("OBDII"))
        assertTrue(ObdParse.looksLikeObd("V-LINK"))
        assertTrue(ObdParse.looksLikeObd("Vgate iCar Pro"))
        assertFalse(ObdParse.looksLikeObd("JBL Flip 5"))
        assertFalse(ObdParse.looksLikeObd(null))
    }

    @Test
    fun reiniciaElLectorSiElAutoNoContesta() {
        var m = 0
        repeat(ObdWatch.MAX_ECU_MISSES - 1) { m = ObdWatch.misses(m, ecuAnswered = false) }
        assertFalse(ObdWatch.mustReinit(m))
        m = ObdWatch.misses(m, ecuAnswered = true)                  // contestó: vuelve a cero
        assertEquals(0, m)
        m = ObdWatch.misses(m, ecuAnswered = false)
        assertFalse(ObdWatch.mustReinit(m))
        repeat(ObdWatch.MAX_ECU_MISSES) { m = ObdWatch.misses(m, ecuAnswered = false) }
        assertTrue(ObdWatch.mustReinit(m))
    }

    @Test
    fun kilometraje_total_del_auto_si_lo_informa() {
        // 0x0007A64C décimas = 50.132,4 km → 50.132
        assertEquals(50132, com.axel.onixlauncher.ObdParse.odometerKm("41 A6 00 07 A6 4C\r>"))
        assertEquals(null, com.axel.onixlauncher.ObdParse.odometerKm("NO DATA"))
        assertEquals(null, com.axel.onixlauncher.ObdParse.odometerKm("41 A6 00 00 00 00"))
    }

    /**
     * AUD-075 (v0.79.5): una lectura con los mismos valores no es un cambio. El estado conserva el
     * objeto anterior, así Compose no vuelve a dibujar toda la pantalla cada 0,7 s con el auto quieto.
     */
    @Test
    fun lectura_igual_no_cambia_el_estado() {
        val state = androidx.compose.runtime.mutableStateOf<com.axel.onixlauncher.ObdData?>(null)
        val first = com.axel.onixlauncher.ObdData(coolantC = 90, rpm = 800, speedKmh = 0, volts = 14.1f)
        state.value = first
        state.value = com.axel.onixlauncher.ObdData(coolantC = 90, rpm = 800, speedKmh = 0, volts = 14.1f)
        org.junit.Assert.assertSame("mismos valores: no hay cambio", first, state.value)
        state.value = com.axel.onixlauncher.ObdData(coolantC = 90, rpm = 825, speedKmh = 0, volts = 14.1f)
        assertEquals(825, state.value?.rpm)
    }

    /**
     * v0.81.13: una lectura vacía no corta el Bluetooth (antes, una sola lo cortaba). Recién a las
     * [ObdWatch.MAX_SILENT_POLLS] seguidas sin que conteste ni el lector se reconecta.
     */
    @Test
    fun una_lectura_vacia_no_corta_la_conexion() {
        var silent = 0
        silent = ObdWatch.silent(silent, readerAnswered = false)
        assertFalse(ObdWatch.mustReconnect(silent))
        silent = ObdWatch.silent(silent, readerAnswered = false)
        assertFalse(ObdWatch.mustReconnect(silent))
        silent = ObdWatch.silent(silent, readerAnswered = true)      // contestó: vuelve a cero
        assertEquals(0, silent)
        repeat(ObdWatch.MAX_SILENT_POLLS) { silent = ObdWatch.silent(silent, readerAnswered = false) }
        assertTrue(ObdWatch.mustReconnect(silent))
    }
}
