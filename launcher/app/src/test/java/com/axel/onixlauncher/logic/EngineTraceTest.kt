package com.axel.onixlauncher.logic

import com.axel.onixlauncher.EngineTrace
import com.axel.onixlauncher.Projection
import com.carlink.core.core.Engine
import com.carlink.core.core.LinkProblem
import com.carlink.core.core.LinkStage
import com.carlink.core.core.LinkState
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.79.1: los pasos del motor de CarPlay y el motivo exacto quedan en la Prueba completa. */
class EngineTraceTest {

    @Test
    fun pasos_de_carplay_en_palabras_con_el_nombre_del_motor() {
        assertEquals("buscando el iPhone en el USB (DiscoveringIphone)", EngineTrace.describe(CarPlayStatus.DiscoveringIphone))
        assertEquals("el iPhone confía en la radio (Pairing)", EngineTrace.describe(CarPlayStatus.Pairing))
        assertEquals("el iPhone no aparece en el USB (IphoneNotListed)", EngineTrace.describe(CarPlayStatus.IphoneNotListed(false)))
        assertEquals("falló: iPhone USB permission was denied", EngineTrace.describe(CarPlayStatus.Failed("iPhone USB permission was denied")))
        // Un paso sin traducción queda con el nombre del motor
        assertEquals("ControlEnded", EngineTrace.describe(CarPlayStatus.ControlEnded))
    }

    @Test
    fun estado_del_motor_con_el_motivo_exacto() {
        val failed = LinkState(LinkStage.FAILED, Engine.CARPLAY, problem = LinkProblem.USB, detail = "Lockdown pairing timed out")
        assertEquals("No se pudo conectar CarPlay · Revisá el cable USB y volvé a enchufar · motivo: Lockdown pairing timed out",
            EngineTrace.session(failed))
        val connecting = LinkState(LinkStage.CONNECTING, Engine.CARPLAY, step = 2, stepCount = 4)
        assertEquals("Conectando CarPlay… · Paso 2 de 4", EngineTrace.session(connecting))
        assertEquals("motivo: Lockdown pairing timed out", Projection.detailOf(failed))
        assertNull(Projection.detailOf(connecting))
    }

    @Test
    fun no_repite_la_misma_linea_y_guarda_las_ultimas() {
        EngineTrace.clear()
        EngineTrace.add("a", 0L)
        EngineTrace.add("a", 1_000L)
        EngineTrace.add("b", 2_000L)
        assertEquals(2, EngineTrace.recent().size)
        repeat(EngineTrace.MAX + 10) { EngineTrace.add("x$it", 3_000L + it) }
        assertEquals(EngineTrace.MAX, EngineTrace.recent().size)
        assertTrue(EngineTrace.recent().last().endsWith("x${EngineTrace.MAX + 9}"))
    }

    @Test
    fun registro_de_carplay_sin_direcciones_y_con_la_sesion_anterior_si_la_actual_es_corta() {
        val previous = (1..100).map { "p$it" }
        val tail = EngineTrace.logTail(listOf("USB attach 00:11:22:33:44:55"), previous, n = 10)
        assertEquals(10, tail.size)
        assertEquals("(sesión actual)", tail[8])
        assertEquals("USB attach [address]", tail[9])
        val long = (1..50).map { "c$it" }
        assertEquals(long.takeLast(10), EngineTrace.logTail(long, previous, n = 10))
    }

    @Test
    fun equipos_usb_con_apple_marcado() {
        assertEquals("05ac:12a8 · iPhone (Apple) · 4 configuraciones", EngineTrace.usbLine(0x05ac, 0x12a8, "iPhone", 4))
        assertEquals("1a86:7523 · sin nombre · 1 configuración", EngineTrace.usbLine(0x1a86, 0x7523, null, 1))
    }
}
