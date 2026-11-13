package com.axel.onixlauncher.logic

import com.axel.onixlauncher.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v0.79.7: cuándo la posición del GPS está viva, qué dice el mapa si no lo está y cómo se mide la
 * frecuencia de las posiciones para la Prueba completa. El tiempo se pasa siempre como parámetro.
 */
class GpsFixTest {

    @Before
    fun limpiar() = GpsFix.clear()

    @Test
    fun la_posicion_esta_viva_hasta_10_segundos() {
        assertNull(GpsFix.status(0))
        assertNull(GpsFix.status(10_000))
        assertEquals("Sin señal de GPS hace 15 s", GpsFix.status(15_000))
        assertEquals("Sin señal de GPS hace 2 min", GpsFix.status(125_000))
        assertEquals("Sin señal de GPS hace 1 h", GpsFix.status(3_700_000))
    }

    @Test
    fun sin_ninguna_posicion_el_mapa_dice_que_busca() {
        assertEquals("Buscando GPS…", GpsFix.status(null))
    }

    @Test
    fun una_posicion_es_vieja_pasados_2_minutos_o_si_nunca_hubo() {
        assertTrue(GpsFix.isStale(null))
        assertFalse(GpsFix.isStale(119_000))
        assertFalse(GpsFix.isStale(120_000))
        assertTrue(GpsFix.isStale(120_001))
    }

    @Test
    fun la_edad_de_la_ultima_posicion() {
        assertNull(GpsFix.ageMs(5_000))
        GpsFix.record(receivedAtMs = 1_000, lagMs = null, accuracyM = null, satellites = null)
        assertEquals(3_000L, GpsFix.ageMs(4_000))
    }

    @Test
    fun intervalo_retraso_y_cantidad_de_posiciones() {
        listOf(0L to 200L, 1_000L to 400L, 2_000L to 300L, 3_500L to null, 4_500L to 300L)
            .forEach { (t, lag) -> GpsFix.record(t, lag, 5f, 9) }
        val r = GpsFix.report(now = 5_000)!!
        assertEquals(5, r.count)
        assertEquals("(4500 - 0) / 4", 1_125L, r.avgIntervalMs)
        assertEquals(1_500L, r.maxIntervalMs)
        assertEquals("el que no tiene retraso no cuenta", 300L, r.avgLagMs)
        assertEquals(500L, r.ageMs)
        assertEquals(5f, r.accuracyM)
        assertEquals(9, r.satellites)
    }

    @Test
    fun solo_se_guardan_las_ultimas_30() {
        repeat(40) { GpsFix.record(it * 1_000L, null, null, null) }
        val r = GpsFix.report(now = 100_000)!!
        assertEquals(30, r.count)
        assertEquals("el intervalo se mide sobre las últimas", 1_000L, r.avgIntervalMs)
    }

    @Test
    fun con_una_sola_posicion_no_hay_intervalo_ni_retraso() {
        GpsFix.record(1_000, null, null, null)
        val r = GpsFix.report(now = 2_000)!!
        assertNull(r.avgIntervalMs)
        assertNull(r.maxIntervalMs)
        assertNull(r.avgLagMs)
    }

    @Test
    fun sin_posiciones_no_hay_informe() {
        assertNull(GpsFix.report(now = 1_000))
    }

    @Test
    fun el_texto_del_informe() {
        listOf(0L to 200L, 1_000L to 400L, 2_000L to 300L, 3_500L to null, 4_500L to 300L)
            .forEach { (t, lag) -> GpsFix.record(t, lag, 5f, 9) }
        val text = GpsFix.report(now = 5_000)!!.text()
        assertEquals(
            "última hace 0,5 s · intervalo medio 1,1 s (máximo 1,5 s) · retraso de entrega 0,3 s · precisión 5 m · 9 satélites · 5 posiciones",
            text
        )
    }

    @Test
    fun el_texto_sin_datos_opcionales_no_inventa_nada() {
        GpsFix.record(1_000, null, null, null)
        assertEquals("última hace 1,0 s · 1 posiciones", GpsFix.report(now = 2_000)!!.text())
    }

    /** El retraso de entrega sale del reloj propio del equipo (no de la hora, que en estas radios puede estar mal). */
    @Test
    fun el_retraso_de_entrega_se_calcula_con_el_reloj_del_equipo() {
        assertEquals(300L, GpsFix.lagMs(elapsedRealtimeNanos = 9_700_000_000L, nowMs = 10_000))
        assertNull("la radio no informa el dato", GpsFix.lagMs(0, 10_000))
        assertNull("una posición del futuro no es creíble", GpsFix.lagMs(10_500_000_000L, 10_000))
        assertNull("más de una hora tampoco", GpsFix.lagMs(1_000_000L, 10_000_000_000L))
    }
}
