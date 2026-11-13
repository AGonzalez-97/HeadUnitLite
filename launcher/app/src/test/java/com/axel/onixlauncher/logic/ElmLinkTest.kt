package com.axel.onixlauncher.logic

import com.axel.onixlauncher.ElmLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * AUD-085 (v0.79.6): el lector del ELM327 sin Bluetooth. Un puerto falso hace de lector: lo que se
 * escribe llega a [onCommand], que contesta con [feed] (ya) o [feedLater] (tarde, como un lector lento).
 */
class ElmLinkTest {

    private class FakeElmPort(var onCommand: (String, FakeElmPort) -> Unit = { _, _ -> }) {
        private val queue = LinkedBlockingQueue<Int>()
        private val pending = StringBuilder()
        val written = StringBuilder()
        var flushes = 0

        val input = object : InputStream() {
            override fun available() = queue.size
            override fun read(): Int = queue.poll() ?: -1
        }
        val output = object : OutputStream() {
            override fun write(b: Int) {
                written.append(b.toChar())
                if (b == '\r'.code) {
                    val cmd = pending.toString()
                    pending.clear()
                    onCommand(cmd, this@FakeElmPort)
                } else {
                    pending.append(b.toChar())
                }
            }
            override fun flush() { flushes++ }
        }

        fun feed(text: String) = text.forEach { queue.add(it.code) }
        fun feedLater(text: String, ms: Long) {
            Thread { Thread.sleep(ms); feed(text) }.apply { isDaemon = true }.start()
        }
    }

    private fun link(port: FakeElmPort, onClose: () -> Unit = {}) = ElmLink(port.input, port.output, onClose)

    @Test
    fun devuelve_la_respuesta_sin_el_prompt_ni_los_saltos_de_linea() {
        val port = FakeElmPort { _, p -> p.feed("41 0C 1A F8 \r\r>") }
        assertEquals("41 0C 1A F8", link(port).send("010C"))
        assertEquals("el comando sale con \\r y se vacía el buffer", "010C\r", port.written.toString())
        assertEquals(1, port.flushes)
    }

    @Test
    fun descarta_lo_que_quedo_sin_leer_antes_de_mandar() {
        val port = FakeElmPort { _, p -> p.feed("41 0D 3C\r\r>") }
        port.feed("NO DATA\r\r>")   // resto de un comando anterior
        assertEquals("41 0D 3C", link(port).send("010D"))
    }

    @Test
    fun sin_prompt_devuelve_lo_que_llego_al_vencer_el_tiempo() {
        val port = FakeElmPort { _, p -> p.feed("SEARCHING...") }
        val t0 = System.nanoTime()
        val out = link(port).send("0100", timeoutMs = 150)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals("SEARCHING...", out)
        assertTrue("esperó el tiempo máximo ($ms ms)", ms >= 140)
        assertTrue("y no se quedó esperando de más ($ms ms)", ms < 2_000)
    }

    @Test
    fun una_respuesta_tardia_no_se_mezcla_con_el_comando_siguiente() {
        val port = FakeElmPort { cmd, p ->
            if (cmd == "010C") p.feedLater("41 0C 1A F8\r\r>", 250) else p.feed("41 0D 3C\r\r>")
        }
        val link = link(port)
        assertEquals("el lector tardó más que el tiempo máximo", "", link.send("010C", timeoutMs = 100))
        Thread.sleep(500)   // la respuesta tardía ya llegó y quedó sin leer
        assertEquals("la tardía se descarta; la respuesta es la del comando nuevo", "41 0D 3C", link.send("010D"))
    }

    @Test
    fun marca_cuando_salio_el_ultimo_comando_para_el_vigia() {
        val port = FakeElmPort { _, p -> p.feed("OK\r\r>") }
        val link = link(port)
        link.lastIoMs.set(0)
        link.send("ATZ")
        assertTrue(link.lastIoMs.get() > 0)
    }

    @Test
    fun cerrar_cierra_una_vez_y_no_tira_error_aunque_el_socket_falle() {
        var closed = 0
        val l = link(FakeElmPort()) { closed++ }; l.close(); l.close()
        assertEquals(1, closed)
        link(FakeElmPort()) { throw IOException("socket roto") }.close()   // no tiene que tirar
        assertFalse(closed > 1)
    }

    // v0.79.9: in the car the radio's Bluetooth stack can hang inside socket.close(); the reader
    // loop must never hang with it (the OBD2 stayed "conectando…" for minutes).
    @Test
    fun close_does_not_hang_when_the_socket_close_hangs() {
        val release = java.util.concurrent.CountDownLatch(1)
        val l = link(FakeElmPort(), onClose = { release.await() })
        val done = java.util.concurrent.CountDownLatch(1)
        // Runs in its own thread so that a hanging close() fails the test instead of freezing it.
        Thread { l.close(); done.countDown() }.apply { isDaemon = true }.start()
        val returned = done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        release.countDown()
        assertTrue("close() hung while the socket close hung", returned)
    }

    @Test
    fun close_still_closes_the_socket() {
        var closed = false
        link(FakeElmPort(), onClose = { closed = true }).close()
        assertTrue(closed)
    }

    // v0.79.10: when the reader hangs, the event log says in which step (the report only said "stuck").
    @Test
    fun the_step_says_where_a_write_is_blocked() {
        val release = java.util.concurrent.CountDownLatch(1)
        val blocked = object : OutputStream() {
            override fun write(b: Int) { release.await() }
        }
        val l = ElmLink(FakeElmPort().input, blocked) {}
        Thread { try { l.send("ATZ") } catch (_: Exception) {} }.apply { isDaemon = true }.start()
        Thread.sleep(300)
        assertEquals("escribiendo ATZ", l.step)
        release.countDown()
    }

    @Test
    fun the_step_after_a_full_command_is_the_wait_for_the_answer_then_done() {
        val port = FakeElmPort { _, p -> p.feed("OK>") }
        val l = link(port)
        l.send("ATE0")
        assertEquals("listo ATE0", l.step)
    }
}
