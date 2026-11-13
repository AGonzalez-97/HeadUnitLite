package com.axel.onixlauncher.logic

import com.axel.onixlauncher.ElmLink
import com.axel.onixlauncher.ObdRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch

/**
 * v0.79.12: in the car the OBD2 socket "connects" but the very first write (ATZ) blocks forever, and
 * only the first way of connecting was ever tried. Each way is verified with a probe write; a way
 * that does not accept it is closed and the next one is tried.
 */
class ObdConnectTest {

    private val release = CountDownLatch(1)

    private class Way(val link: ElmLink, val closed: BooleanArray)

    private fun way(blocksOnWrite: Boolean): Way {
        val closed = booleanArrayOf(false)
        // A healthy reader answers the prompt ">" once after a command is written.
        val answered = java.util.concurrent.atomic.AtomicBoolean(false)
        val input = object : InputStream() {
            override fun available() = if (answered.get()) 1 else 0
            override fun read(): Int { answered.set(false); return '>'.code }
        }
        val output = object : OutputStream() {
            override fun write(b: Int) {
                if (blocksOnWrite) release.await()
                if (b == 13) answered.set(true)
            }
        }
        return Way(ElmLink(input, output) { closed[0] = true }, closed)
    }

    @Test
    fun the_first_way_that_accepts_the_write_is_used() {
        val a = way(false)
        val b = way(false)
        val got = ObdRecovery.connectVerified(listOf({ a.link }, { b.link }), probeMs = 2_000)
        assertSame(a.link, got)
        assertFalse(a.closed[0])
    }

    @Test
    fun a_way_whose_write_blocks_is_closed_and_the_next_is_tried() {
        val a = way(true)
        val b = way(false)
        val log = mutableListOf<String>()
        val got = ObdRecovery.connectVerified(listOf({ a.link }, { b.link }), probeMs = 300, log = { log += it })
        release.countDown()
        assertSame(b.link, got)
        assertTrue("the blocked way is closed", a.closed[0])
        assertFalse(b.closed[0])
        assertTrue(log.toString(), log.any { it.contains("1") && it.contains("escribiendo ATZ") })
    }

    @Test
    fun a_way_that_fails_to_open_is_skipped() {
        val b = way(false)
        val got = ObdRecovery.connectVerified(listOf({ throw java.io.IOException("no abre") }, { b.link }), probeMs = 2_000)
        assertSame(b.link, got)
    }

    @Test
    fun when_no_way_works_the_last_error_is_thrown() {
        val a = way(true)
        val b = way(true)
        try {
            ObdRecovery.connectVerified(listOf({ a.link }, { b.link }), probeMs = 200)
            fail("it should have thrown")
        } catch (e: Exception) {
            assertTrue(e.message, e.message!!.contains("trabado"))
        } finally {
            release.countDown()
        }
        assertTrue(a.closed[0] && b.closed[0])
        assertEquals(true, true)
    }

    @Test
    fun a_silent_way_that_never_answers_is_closed_and_the_next_is_tried() {
        val closedSilent = booleanArrayOf(false)
        val silentInput = object : InputStream() {
            override fun available() = 1
            override fun read(): Int = -1
        }
        val silentOutput = object : OutputStream() {
            override fun write(b: Int) {}
        }
        val silent = ElmLink(silentInput, silentOutput) { closedSilent[0] = true }
        val healthy = way(false)
        var chosen = -1
        val got = ObdRecovery.connectVerified(
            listOf({ silent }, { healthy.link }),
            probeMs = 6_000,
            onSuccess = { chosen = it },
        )
        assertSame(healthy.link, got)
        assertTrue("the silent way must be closed", closedSilent[0])
        assertEquals(1, chosen)
    }

    /**
     * v0.81.14: on the NWD head unit every opened socket takes a client slot of its Bluetooth daemon
     * that is never released (car log, 2026-11-11: client_fd 13, 21, 22 in 20 s). One socket per round.
     */
    @Test
    fun with_one_socket_per_round_a_blocked_way_is_not_followed_by_another() {
        val a = way(true)
        var opened = 0
        try {
            ObdRecovery.connectVerified(
                listOf({ opened++; a.link }, { opened++; way(false).link }),
                probeMs = 200,
                maxSockets = 1,
            )
            fail("it should have thrown")
        } catch (e: Exception) {
            assertTrue(e.message, e.message!!.contains("trabado"))
        } finally {
            release.countDown()
        }
        assertEquals(1, opened)
        assertTrue(a.closed[0])
    }

    @Test
    fun a_way_that_fails_to_open_does_not_use_the_socket_budget() {
        val b = way(false)
        val got = ObdRecovery.connectVerified(
            listOf({ throw java.io.IOException("no abre") }, { b.link }),
            probeMs = 2_000,
            maxSockets = 1,
        )
        assertSame(b.link, got)
    }

    @Test
    fun tries_the_preferred_way_first() {
        assertEquals(2, ObdRecovery.DEFAULT_PREFERRED_WAY)
        assertEquals(15_000L, ObdRecovery.WAKE_SETTLE_MS)
        assertTrue(ObdRecovery.isMcuOff(0))
        assertFalse(ObdRecovery.isMcuOff(1))
        assertEquals(listOf(2, 0, 1), ObdRecovery.wayOrder(3, preferred = 2))
        assertEquals(listOf(0, 1, 2), ObdRecovery.wayOrder(3, preferred = 0))
        val a = way(false)
        val b = way(false)
        val got = ObdRecovery.connectVerified(listOf({ a.link }, { b.link }), probeMs = 2_000, preferred = 1)
        assertSame(b.link, got)
        assertFalse(a.closed[0])
    }
}
