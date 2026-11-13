package com.axel.onixlauncher.logic

import com.axel.onixlauncher.AppLog
import com.axel.onixlauncher.Net
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * AUD-085 (v0.79.6): `Net` contra un servidor HTTP local (sin internet): redirecciones, tope de
 * tamaño, códigos de error y que no se registre lo que lleva un token.
 */
class NetTest {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @After
    fun tearDown() = server.stop(0)

    private fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { ex -> try { handler(ex) } finally { ex.close() } }
    }

    private fun reply(ex: HttpExchange, code: Int, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap()) {
        headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
        if (body.isEmpty()) ex.sendResponseHeaders(code, -1) else {
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.write(body)
        }
    }

    private fun redirectChain(prefix: String, hops: Int) {
        for (i in 1..hops) route("/$prefix$i") { reply(it, 302, headers = mapOf("Location" to "/$prefix${i + 1}")) }
        route("/$prefix${hops + 1}") { reply(it, 200, "fin".toByteArray()) }
    }

    @Test
    fun getBytes_sigue_hasta_4_redirecciones() {
        redirectChain("r", hops = 4)
        assertArrayEquals("fin".toByteArray(), Net.getBytes("$base/r1"))
    }

    @Test
    fun getBytes_con_mas_de_4_redirecciones_devuelve_null() {
        redirectChain("s", hops = 5)
        assertNull(Net.getBytes("$base/s1"))
    }

    @Test
    fun getBytes_respeta_el_tope_de_tamano() {
        route("/grande") { reply(it, 200, ByteArray(2_000) { 7 }) }
        assertNull("2000 bytes con tope de 1000", Net.getBytes("$base/grande", maxBytes = 1_000))
        assertEquals("justo en el tope entra", 2_000, Net.getBytes("$base/grande", maxBytes = 2_000)?.size)
    }

    @Test
    fun getBytes_con_error_o_redireccion_sin_destino_devuelve_null() {
        route("/no") { reply(it, 404) }
        route("/roto") { reply(it, 500) }
        route("/sin-destino") { reply(it, 302) }
        assertNull(Net.getBytes("$base/no"))
        assertNull(Net.getBytes("$base/roto"))
        assertNull(Net.getBytes("$base/sin-destino"))
    }

    @Test
    fun getBytes_sin_servidor_devuelve_null() {
        val port = ServerSocket(0).use { it.localPort }   // un puerto libre: nadie escucha
        assertNull(Net.getBytes("http://127.0.0.1:$port/x", timeoutMs = 1_000))
    }

    /** Contrato de la función ("null si falla"): un enlace roto, como los que trae un directorio de radios, no tira error. */
    @Test
    fun getBytes_con_un_enlace_invalido_devuelve_null() {
        assertNull(Net.getBytes("esto no es un enlace"))
        assertNull(Net.getBytes(""))
    }

    @Test
    fun get_devuelve_el_texto_y_null_si_no_es_2xx() {
        route("/ok") { reply(it, 200, "hola".toByteArray()) }
        route("/mal") { reply(it, 503, "caído".toByteArray()) }
        assertEquals("hola", Net.get("$base/ok"))
        assertNull(Net.get("$base/mal"))
    }

    @Test
    fun get_corta_las_respuestas_enormes() {
        route("/enorme") { reply(it, 200, ByteArray(1_300_000) { 'a'.code.toByte() }) }
        val text = Net.get("$base/enorme")!!
        assertTrue("lee de más del tope solo hasta un bloque (${text.length})", text.length in 1_000_000..1_010_000)
    }

    @Test
    fun request_devuelve_el_codigo_y_el_texto_tambien_en_un_error() {
        route("/json") { reply(it, 200, """{"a":1}""".toByteArray()) }
        route("/revocado") { reply(it, 400, """{"error":"invalid_grant"}""".toByteArray()) }
        assertEquals(200 to """{"a":1}""", Net.request("GET", "$base/json"))
        assertEquals(400 to """{"error":"invalid_grant"}""", Net.request("GET", "$base/revocado"))
    }

    @Test
    fun request_manda_el_cuerpo_el_tipo_y_los_encabezados() {
        var visto = ""
        route("/eco") { ex ->
            visto = ex.requestMethod + "|" + ex.requestHeaders.getFirst("Content-Type") + "|" +
                ex.requestHeaders.getFirst("Authorization") + "|" + ex.requestBody.readBytes().decodeToString()
            reply(ex, 200, "ok".toByteArray())
        }
        val r = Net.request("POST", "$base/eco", "a=1&b=2", "application/x-www-form-urlencoded", mapOf("Authorization" to "Bearer T0K3N"))
        assertEquals(200 to "ok", r)
        assertEquals("POST|application/x-www-form-urlencoded|Bearer T0K3N|a=1&b=2", visto)
    }

    @Test
    fun request_sin_servidor_devuelve_null_y_no_registra_el_token() {
        val port = ServerSocket(0).use { it.localPort }
        val r = Net.request("GET", "http://127.0.0.1:$port/x", headers = mapOf("Authorization" to "Bearer SECRETO123"), timeoutMs = 1_000)
        assertNull(r)
        val log = AppLog.recent().joinToString("\n")
        assertFalse("el registro no puede llevar el token: $log", "SECRETO123" in log)
        assertNotNull(log)
    }

    @Test
    fun el_registro_tapa_claves_y_tokens_de_los_enlaces() {
        AppLog.info("GET https://api.ejemplo.com/x?key=ABC123&token=XYZ789&access_token=QQQ&otro=1")
        val last = AppLog.recent().last()
        assertFalse(last, "ABC123" in last || "XYZ789" in last || "QQQ" in last)
        assertTrue(last, "otro=1" in last)
    }
}
