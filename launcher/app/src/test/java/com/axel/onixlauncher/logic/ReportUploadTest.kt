package com.axel.onixlauncher.logic

import com.axel.onixlauncher.LiveSearch
import com.axel.onixlauncher.ReportUpload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.81.23: the full report is sent to the author's PC through a separate ntfy channel, and the
 * map search suggests places while typing.
 */
class ReportUploadTest {
    @Test
    fun el_informe_va_a_un_canal_aparte_del_de_destinos() {
        assertEquals("https://ntfy.sh/mi-canal-informes", ReportUpload.url("mi-canal"))
        assertEquals("https://ntfy.sh/mi-canal-informes", ReportUpload.url("  mi-canal \r"))
        assertEquals(null, ReportUpload.url(""))            // no channel configured: nothing is sent
        assertEquals(null, ReportUpload.url("   "))
    }

    @Test
    fun el_estado_del_envio_se_dice_en_claro() {
        assertEquals("Enviado a la PC.", ReportUpload.statusText(200, hasTopic = true))
        assertTrue(ReportUpload.statusText(null, hasTopic = true).startsWith("Sin internet"))
        assertEquals("No se pudo enviar a la PC (HTTP 413).", ReportUpload.statusText(413, hasTopic = true))
        assertEquals("Sin canal para enviarlo: mandalo en el chat.", ReportUpload.statusText(null, hasTopic = false))
    }

    @Test
    fun el_buscador_del_mapa_sugiere_mientras_se_escribe() {
        assertEquals(false, LiveSearch.due("Av", last = ""))               // too short
        assertEquals(false, LiveSearch.due("  9 d ", last = ""))
        assertEquals(true, LiveSearch.due("9 de Julio", last = ""))
        assertEquals(false, LiveSearch.due("9 de Julio ", last = "9 de Julio"))   // already searched
        assertEquals(true, LiveSearch.due("9 de Julio 5", last = "9 de Julio"))
    }
}
