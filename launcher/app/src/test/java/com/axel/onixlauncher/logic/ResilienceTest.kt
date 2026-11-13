package com.axel.onixlauncher.logic

import com.axel.onixlauncher.PhoneShare
import com.axel.onixlauncher.RadioCatalog
import com.axel.onixlauncher.RouteRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.81.22: retry review of 2026-11-12. A failure caused by the network must not be remembered as
 * a real "not found", and what failed must be asked again when the network is back.
 */
class ResilienceTest {
    @Test
    fun un_destino_del_celular_sin_internet_queda_en_espera() {
        assertEquals("No encontré \"Plaza Independencia\"", PhoneShare.failureText("Plaza Independencia", offline = false))
        val waiting = PhoneShare.failureText("Plaza Independencia", offline = true)
        assertTrue(waiting, waiting.startsWith("Sin internet") && "Plaza Independencia" in waiting)
        assertEquals(60, Regex("\"(.*)\"").find(PhoneShare.failureText("x".repeat(200), offline = false))!!.groupValues[1].length)
    }

    @Test
    fun la_revision_de_radios_no_se_guarda_si_la_red_estaba_mal() {
        assertEquals(true, RadioCatalog.shouldSaveCheck(ok = 25, previousOk = 0, netDown = false))   // first review
        assertEquals(true, RadioCatalog.shouldSaveCheck(ok = 24, previousOk = 25, netDown = false))
        assertEquals(false, RadioCatalog.shouldSaveCheck(ok = 0, previousOk = 25, netDown = false))  // nothing answered
        assertEquals(false, RadioCatalog.shouldSaveCheck(ok = 20, previousOk = 25, netDown = true))  // names not resolving
        assertEquals(false, RadioCatalog.shouldSaveCheck(ok = 12, previousOk = 25, netDown = false)) // less than half: weak signal
        assertEquals(true, RadioCatalog.shouldSaveCheck(ok = 13, previousOk = 25, netDown = false))
    }

    @Test
    fun la_ruta_que_fallo_por_red_se_reintenta_espaciado() {
        assertEquals(3_000L, RouteRetry.waitMs(1, netDown = false))     // network is back: ask right away
        assertEquals(30_000L, RouteRetry.waitMs(1, netDown = true))
        assertEquals(60_000L, RouteRetry.waitMs(2, netDown = true))
        assertEquals(120_000L, RouteRetry.waitMs(4, netDown = true))
        assertEquals(120_000L, RouteRetry.waitMs(9, netDown = true))
    }
}
