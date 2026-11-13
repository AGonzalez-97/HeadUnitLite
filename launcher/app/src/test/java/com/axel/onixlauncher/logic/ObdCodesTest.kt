package com.axel.onixlauncher.logic

import com.axel.onixlauncher.AlertWatch
import com.axel.onixlauncher.Consumption
import com.axel.onixlauncher.Dtc
import com.axel.onixlauncher.EngineAlert
import com.axel.onixlauncher.EngineAlerts
import com.axel.onixlauncher.ObdData
import com.axel.onixlauncher.ObdReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OBD2 avanzado (v0.50.0): códigos de falla, alertas del motor y consumo, con el lector simulado. */
class ObdCodesTest {

    @Test
    fun decodificaLasCuatroLetras() {
        assertEquals("P0133", Dtc.decode(0x01, 0x33))
        assertEquals("C0035", Dtc.decode(0x40, 0x35))
        assertEquals("B1234", Dtc.decode(0x92, 0x34))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
    }

    @Test
    fun codigosEnCanDeUnaLinea() {
        assertEquals(listOf("P0133", "P0420"), Dtc.parse("4302013304 20", 3))
        assertEquals(emptyList<String>(), Dtc.parse("4300", 3))
        assertEquals(listOf("P0171"), Dtc.parse("SEARCHING...\r47010171\r\r>", 7))
    }

    @Test
    fun codigosEnProtocolosViejosConRelleno() {
        assertEquals(listOf("P0133"), Dtc.parse("43 01 33 00 00 00 00", 3))
        assertEquals(listOf("P0133", "P0420", "P0171"), Dtc.parse("43 01 33 04 20 01 71", 3))
    }

    @Test
    fun codigosEnCanDeVariasLineas() {
        val list = listOf("P0133", "P0420", "P0171", "P0302", "U0100")
        val raw = FakeElm327.codes(0x43, list)
        assertTrue(raw.contains("1:"))                    // de verdad viene en varias líneas
        assertEquals(list, Dtc.parse(raw, 3))
    }

    @Test
    fun sinRespuestaYSinCodigos() {
        assertEquals(emptyList<String>(), Dtc.parse("NO DATA", 3))
        assertNull(Dtc.parse("?", 3))
        assertNull(Dtc.parse("", 3))
        assertNull(Dtc.parse("4302 0133", 7))             // respuesta de otro modo
    }

    @Test
    fun luzDelMotorYBorrado() {
        assertEquals(true, Dtc.mil("41 01 82 07 65 00"))
        assertEquals(false, Dtc.mil("4101000765 00"))
        assertNull(Dtc.mil("NO DATA"))
        assertTrue(Dtc.cleared("44"))
        assertFalse(Dtc.cleared("NO DATA"))
    }

    @Test
    fun descripcionesEnCastellano() {
        assertEquals("Catalizador con bajo rendimiento (banco 1)", Dtc.describe("P0420"))
        assertEquals("Falla de encendido en el cilindro 2", Dtc.describe("P0302"))
        assertEquals("Mezcla de aire y nafta (código de la marca)", Dtc.describe("P1101"))
        assertEquals("Comunicación entre módulos", Dtc.describe("U0999"))
        assertEquals("Carrocería (código de la marca)", Dtc.describe("B1234"))
    }

    @Test
    fun alertasDelMotor() {
        assertEquals(EngineAlert.HOT, EngineAlerts.of(ObdData(coolantC = 112)))
        assertEquals(EngineAlert.NOT_CHARGING, EngineAlerts.of(ObdData(coolantC = 90, volts = 12.0f, rpm = 850)))
        assertEquals(EngineAlert.OVERCHARGING, EngineAlerts.of(ObdData(volts = 15.8f, rpm = 2000)))
        assertEquals(EngineAlert.LOW_BATTERY, EngineAlerts.of(ObdData(volts = 11.6f, rpm = 0)))
        assertNull(EngineAlerts.of(ObdData(coolantC = 95, volts = 14.2f, rpm = 2000)))
        assertNull(EngineAlerts.of(ObdData(volts = 11.0f)))              // sin rpm no se sabe si anda
        assertNull(EngineAlerts.of(null))
    }

    @Test
    fun laAlertaTieneQueDurar() {
        val w = AlertWatch()
        assertNull(w.update(EngineAlert.NOT_CHARGING, 0))
        assertNull(w.update(EngineAlert.NOT_CHARGING, 59_000))
        assertEquals(EngineAlert.NOT_CHARGING, w.update(EngineAlert.NOT_CHARGING, 60_000))
        assertNull(w.update(null, 61_000))                               // se normalizó: se borra ya
        assertNull(w.update(EngineAlert.HOT, 62_000))
        assertEquals(EngineAlert.HOT, w.update(EngineAlert.HOT, 70_000))  // recalentado avisa a los 8 s
    }

    @Test
    fun consumoPorPresionDeAdmision() {
        // Ralentí: ~1,5 a 2,5 g/s de aire en un 1.0
        val idle = Consumption.airFromMap(30f, 30f, 800f)
        assertTrue("aire en ralentí $idle", idle in 1.5f..2.5f)
        // 60 km/h a 2000 rpm y 50 kPa: entre 15 y 25 km/l
        val kml = Consumption.kmPerLiter(Consumption.airFromMap(50f, 30f, 2000f), 60f)!!
        assertTrue("consumo $kml", kml in 15f..25f)
        assertNull(Consumption.kmPerLiter(5f, 0f))                       // parado no hay km/l
        assertEquals(12f, Consumption.smooth(10f, 20f, 0.2f)!!, 0.001f)
        assertEquals(10f, Consumption.smooth(10f, null)!!, 0.001f)
    }

    @Test
    fun lectorSimuladoSinMaf() {
        val elm = FakeElm327(mafGs = null)
        val reader = ObdReader(elm::send)
        val d = reader.poll()
        assertEquals(90, d.coolantC)
        assertEquals(2000, d.rpm)
        assertEquals(60, d.speedKmh)
        assertEquals(14.1f, d.volts!!, 0.001f)
        assertEquals(false, reader.hasMaf)
        assertNotNull(d.kmPerLiter)
        assertTrue("010B" in elm.sent && "010F" in elm.sent)
        // Ya sabe que no hay MAF: no lo vuelve a pedir
        elm.sent.clear()
        reader.poll()
        assertFalse("0110" in elm.sent)
    }

    @Test
    fun lectorSimuladoConMaf() {
        val elm = FakeElm327(mafGs = 8f, mapKpa = null)
        val reader = ObdReader(elm::send)
        val d = reader.poll()
        assertEquals(true, reader.hasMaf)
        assertFalse("010B" in elm.sent)
        // 8 g/s a 60 km/h ≈ 22 km/l
        assertEquals(60f / (8f * 3600f / (Consumption.AIR_FUEL * Consumption.FUEL_G_PER_L)), d.kmPerLiter!!, 0.01f)
        // Parado: sin km/l
        elm.speedKmh = 0
        assertNull(reader.poll().kmPerLiter)
    }

    @Test
    fun lectorSimuladoLeeYBorraCodigos() {
        val elm = FakeElm327(stored = mutableListOf("P0420", "P0133"), pending = mutableListOf("P0171"))
        val reader = ObdReader(elm::send)
        val codes = reader.readCodes(1L)
        assertEquals(true, codes.mil)
        assertEquals(listOf("P0420", "P0133"), codes.stored)
        assertEquals(listOf("P0171"), codes.pending)
        assertEquals(3, codes.total)
        assertTrue(reader.clearCodes())
        val after = reader.readCodes(2L)
        assertEquals(false, after.mil)
        assertEquals(0, after.total)
        // Si el auto no acepta el borrado, se informa
        val stubborn = FakeElm327(stored = mutableListOf("P0300"), acceptClear = false)
        assertFalse(ObdReader(stubborn::send).clearCodes())
    }
}
