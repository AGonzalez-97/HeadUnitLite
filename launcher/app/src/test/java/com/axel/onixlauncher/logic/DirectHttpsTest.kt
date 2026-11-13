package com.axel.onixlauncher.logic

import com.axel.onixlauncher.DirectHttps
import com.axel.onixlauncher.PhoneApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.71.0: rutas por TLS 1.3 directo (HU-013) y botón Teléfono del dock con la app de la radio.
 */
class DirectHttpsTest {

    @Test
    fun respuesta_con_largo_fijo() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 11\r\n\r\n{\"code\":1}\n"
        assertEquals(200 to "{\"code\":1}\n", DirectHttps.parse(raw.toByteArray()))
    }

    @Test
    fun respuesta_en_trozos_chunked() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\n{\"a\":\r\n4;x=1\r\n\"ok\"\r\n1\r\n}\r\n0\r\n\r\n"
        assertEquals(200 to "{\"a\":\"ok\"}", DirectHttps.parse(raw.toByteArray()))
    }

    @Test
    fun error_del_servidor_y_basura() {
        assertEquals(429, DirectHttps.parse("HTTP/1.1 429 Too Many Requests\r\n\r\nlento".toByteArray())?.first)
        assertNull(DirectHttps.parse("nada".toByteArray()))
        assertNull(DirectHttps.parse(ByteArray(0)))
    }

    @Test
    fun ruta_y_host_del_pedido() {
        val req = DirectHttps.requestText("https://router.project-osrm.org/route/v1/driving/1,2;3,4?overview=full", "Onix/1")
        assertEquals("GET /route/v1/driving/1,2;3,4?overview=full HTTP/1.1", req.lines().first())
        assertEquals(true, "Host: router.project-osrm.org\r\n" in req)
        assertEquals(true, req.endsWith("Connection: close\r\n\r\n"))
    }

    @Test
    fun telefono_prefiere_la_app_de_la_radio() {
        val apps = mapOf("com.spotify.music" to "Spotify", "com.nwd.android.phone" to "Bluetooth", "com.android.dialer" to "Teléfono")
        assertEquals("com.nwd.android.phone", PhoneApp.pick(apps))
        assertEquals("com.foo.btphone", PhoneApp.pick(mapOf("com.foo.btphone" to "BT Phone")))
        assertEquals("com.x.calls", PhoneApp.pick(mapOf("com.x.calls" to "Teléfono")))
        assertNull(PhoneApp.pick(mapOf("com.spotify.music" to "Spotify", "com.axel.onixlauncher" to "Onix teléfono")))
    }

    @Test
    fun el_registro_tapa_las_claves() {
        assertEquals("Sin respuesta de https://api.tomtom.com/x.json?key=***&lat=1",
            com.axel.onixlauncher.AppLog.redact("Sin respuesta de https://api.tomtom.com/x.json?key=AbC123&lat=1"))
        assertEquals("token=*** listo", com.axel.onixlauncher.AppLog.redact("token=xyz listo"))
        assertEquals("monkey business", com.axel.onixlauncher.AppLog.redact("monkey business"))
    }
}
