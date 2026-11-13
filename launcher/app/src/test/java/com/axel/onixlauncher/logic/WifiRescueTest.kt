package com.axel.onixlauncher.logic

import com.axel.onixlauncher.WifiRescue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.81.19: report of 2026-11-12 17:18. The phone hotspot lost signal and came back; the radio's
 * Wi-Fi stayed "connected" but no host name resolved any more (Spotify, TomTom, weather). The Wi-Fi
 * is restarted when name resolution keeps failing.
 */
class WifiRescueTest {
    private fun restart(
        failures: Int = 3, stuckMs: Long = 60_000L, wifi: Boolean = true, projection: Boolean = false,
        restarts: Int = 0, sinceRestartMs: Long = Long.MAX_VALUE
    ) = WifiRescue.shouldRestart(failures, stuckMs, wifi, projection, restarts, sinceRestartMs)

    @Test
    fun reinicia_el_wifi_si_los_nombres_no_resuelven_durante_un_minuto() {
        assertEquals(true, restart())
        assertEquals(false, restart(failures = 2))            // too few failures
        assertEquals(false, restart(stuckMs = 59_000L))       // a short cut recovers by itself
        assertEquals(false, restart(wifi = false))            // not on Wi-Fi: nothing to restart
        assertEquals(false, restart(projection = true))       // Android Auto / CarPlay may be using it
    }

    @Test
    fun espacia_los_reinicios_si_no_alcanza() {
        assertEquals(5 * 60_000L, WifiRescue.gapMs(1))
        assertEquals(10 * 60_000L, WifiRescue.gapMs(2))
        assertEquals(20 * 60_000L, WifiRescue.gapMs(3))
        assertEquals(20 * 60_000L, WifiRescue.gapMs(9))
        assertEquals(false, restart(restarts = 1, sinceRestartMs = 4 * 60_000L))
        assertEquals(true, restart(restarts = 1, sinceRestartMs = 5 * 60_000L))
        assertEquals(false, restart(restarts = 2, sinceRestartMs = 9 * 60_000L))
    }

    // v0.81.21: Android never reported the network as lost, so nothing asked again when it came back.
    @Test
    fun avisa_que_la_red_volvio_si_venia_fallando() {
        assertEquals(false, WifiRescue.recovered(0))
        assertEquals(false, WifiRescue.recovered(1))   // a single blip is not an outage
        assertEquals(true, WifiRescue.recovered(2))
        assertEquals(true, WifiRescue.recovered(40))
    }

    @Test
    fun solo_cuenta_las_fallas_de_nombre() {
        assertEquals(true, WifiRescue.isNameFailure(java.net.UnknownHostException("api.spotify.com")))
        assertEquals(false, WifiRescue.isNameFailure(java.net.SocketTimeoutException("timeout")))
        assertEquals(false, WifiRescue.isNameFailure(java.io.IOException("x")))
    }
}
