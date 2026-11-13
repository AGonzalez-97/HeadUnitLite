package com.axel.onixlauncher.logic

import android.content.Context
import com.axel.onixlauncher.PhoneShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AUD-085 (v0.79.7): el bucle que escucha el canal de ntfy, sin red. Lo importante: seguir desde el
 * último mensaje leído (`since`), no repetir destinos viejos, ignorar lo que no es un mensaje y que
 * la hora se mire en cada mensaje (la conexión dura horas).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhoneShareStreamTest {

    private fun prefs() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("celular_prueba", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs().edit().clear().commit()
    }

    private fun msg(id: String, time: Long, text: String) = """{"id":"$id","time":$time,"event":"message","message":"$text"}"""

    @Test
    fun el_enlace_del_canal_sigue_desde_el_ultimo_mensaje() {
        assertEquals("https://ntfy.sh/mi-canal/json", PhoneShare.streamUrl("mi-canal", null))
        assertEquals("https://ntfy.sh/mi-canal/json?since=Ab12Cd", PhoneShare.streamUrl("mi-canal", "Ab12Cd"))
        assertEquals("el nombre del canal se codifica", "https://ntfy.sh/a+b%2Fc/json", PhoneShare.streamUrl("a b/c", null))
    }

    @Test
    fun entrega_solo_los_mensajes_frescos_y_deja_el_cursor_en_el_ultimo() {
        val lines = listOf(
            """{"id":"o1","time":1000,"event":"open"}""",
            msg("m1", 1000, "viejo"),                                   // hace 1000 s: pasa de los 15 min
            """{"id":"k1","time":1990,"event":"keepalive"}""",
            msg("m2", 1995, "Plaza Independencia"),                     // hace 5 s
            "esto no es json",
            "",
        )
        val delivered = mutableListOf<String>()
        PhoneShare.consume(lines.asSequence(), prefs(), now = { 2000L }) { delivered += it }
        assertEquals(listOf("Plaza Independencia"), delivered)
        assertEquals("el cursor es el id del último mensaje", "m2", prefs().getString("ultimo", null))
    }

    @Test
    fun un_mensaje_viejo_no_se_entrega_pero_igual_mueve_el_cursor() {
        val delivered = mutableListOf<String>()
        PhoneShare.consume(sequenceOf(msg("m9", 100, "de ayer")), prefs(), now = { 5000L }) { delivered += it }
        assertEquals(emptyList<String>(), delivered)
        assertEquals("así no vuelve a llegar al reconectar", "m9", prefs().getString("ultimo", null))
    }

    @Test
    fun los_eventos_que_no_son_mensajes_no_tocan_el_cursor() {
        PhoneShare.consume(sequenceOf("""{"id":"o1","time":1,"event":"open"}""", "basura"), prefs(), now = { 1L }) { }
        assertNull(prefs().getString("ultimo", null))
    }

    /** La conexión dura horas: lo fresco se decide con la hora de cada mensaje, no con la de cuando se conectó. */
    @Test
    fun la_hora_se_mira_en_cada_mensaje() {
        var now = 1_000L
        val delivered = mutableListOf<String>()
        val lines = sequence {
            yield(msg("a", 1_000, "primero"))
            now = 1_000L + PhoneShare.MAX_AGE_S + 60   // pasó más de lo permitido
            yield(msg("b", 1_000, "mismo horario de envío, pero ahora ya es viejo"))
        }
        PhoneShare.consume(lines, prefs(), now = { now }) { delivered += it }
        assertEquals(listOf("primero"), delivered)
    }

    @Test
    fun la_espera_para_reconectar_crece_y_tiene_tope() {
        assertEquals(10_000L, PhoneShare.nextWait(5_000L))
        assertEquals(40_000L, PhoneShare.nextWait(20_000L))
        assertEquals(60_000L, PhoneShare.nextWait(40_000L))
        assertEquals(60_000L, PhoneShare.nextWait(60_000L))
    }
}
