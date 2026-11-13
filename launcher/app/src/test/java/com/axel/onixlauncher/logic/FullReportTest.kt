package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Check
import com.axel.onixlauncher.CheckResult
import com.axel.onixlauncher.FullReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Prueba completa de Diagnóstico (v0.41.0): resumen, criterios de cada revisión y nombre del archivo. */
class FullReportTest {
    /** AUD-103 (v0.79.5): dos toques seguidos no arrancan dos pruebas en paralelo. */
    @Test
    fun la_prueba_completa_no_arranca_dos_veces() {
        FullReport.running.value = false
        assertTrue(FullReport.tryBegin())
        assertFalse("la segunda, mientras corre la primera, no arranca", FullReport.tryBegin())
        FullReport.running.value = false
        assertTrue("al terminar se puede volver a pedir", FullReport.tryBegin())
        FullReport.running.value = false
    }

    @Test
    fun resumen() {
        val checks = listOf(
            Check("Red", "Conexión", CheckResult.OK, ""),
            Check("OBD2", "Lector", CheckResult.ND, ""),
            Check("Radio", "Prueba", CheckResult.FALLA, ""),
            Check("GPS", "Encendido", CheckResult.OK, "")
        )
        assertEquals("2 OK · 1 FALLA · 1 N/D", FullReport.summary(checks))
    }

    @Test
    fun respuestasHttp() {
        assertEquals(CheckResult.OK, FullReport.httpResult(200))
        assertEquals(CheckResult.FALLA, FullReport.httpResult(null))
        assertEquals(CheckResult.FALLA, FullReport.httpResult(500))
        assertEquals(CheckResult.FALLA, FullReport.httpResult(401))
        assertEquals(CheckResult.OK, FullReport.httpResult(401, authOk = true))   // Spotify responde, falta sesión
    }

    @Test
    fun radioConAudio() {
        assertTrue(FullReport.audioOk("[local] http://x/stream → HTTP 200 · audio/mpeg · 65536B"))
        assertTrue(FullReport.audioOk("→ HTTP 200 · application/vnd.apple.mpegurl · 512B"))
        assertFalse(FullReport.audioOk("→ HTTP 200 · text/html · 9000B"))
        assertFalse(FullReport.audioOk("→ HTTP 404 · audio/mpeg · 0B"))
    }

    @Test
    fun nombreDelArchivo() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 28, 10, 50, 0) }.time
        assertEquals("onix_informe_2026-10-28_10-50.txt", FullReport.fileName(at))
    }

    @Test
    fun permisosQueNoExistenEnAndroid7() {
        assertEquals(31, FullReport.permissionSince("android.permission.BLUETOOTH_CONNECT"))
        assertEquals(28, FullReport.permissionSince("android.permission.FOREGROUND_SERVICE"))
        assertEquals(null, FullReport.permissionSince("android.permission.INTERNET"))
    }

    /** v0.62.0: voces que faltan en el APK, comparando con las que usa la app. */
    @Test
    fun vocesFaltantes() {
        assertEquals(emptyList<String>(), FullReport.missingClips(listOf("a.ogg", "b.ogg", "LEEME.txt"), listOf("a", "b")))
        assertEquals(listOf("c"), FullReport.missingClips(listOf("a.ogg", "b.ogg"), listOf("a", "b", "c")))
    }

    /** v0.62.0: teclas distintas que llegaron (botones del volante), sin repetir. */
    @Test
    fun teclasVistas() {
        val lines = listOf(
            "10:00:01 TECLA · KEYCODE_MEDIA_NEXT (código 87, scan 0, fuente 257)",
            "10:00:02 ILL · luces=1",
            "10:00:03 TECLA · KEYCODE_MEDIA_NEXT (código 87, scan 0, fuente 257)",
            "10:00:04 TECLA · KEYCODE_VOLUME_UP (código 24, scan 115, fuente 257)"
        )
        assertEquals(
            listOf("KEYCODE_MEDIA_NEXT (código 87, scan 0, fuente 257)", "KEYCODE_VOLUME_UP (código 24, scan 115, fuente 257)"),
            FullReport.keysSeen(lines)
        )
    }
}
