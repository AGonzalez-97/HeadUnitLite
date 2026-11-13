package com.axel.onixlauncher.logic

import android.content.Context
import android.net.Uri
import com.axel.onixlauncher.SpotifyAuth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * AUD-085 (v0.79.6): el token de Spotify sin red. Los pedidos pasan por `SpotifyAuth.http`, que acá
 * se reemplaza. Reglas: no pedir nada si el token sirve, renovar al vencer, no desconectar por un
 * corte de red o un error del servidor, desconectar solo si Spotify revoca el permiso (400) y
 * renovar una sola vez aunque lleguen dos pedidos a la vez (AUD-053).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpotifyTokenTest {

    private lateinit var ctx: Context
    private lateinit var saved: (String, String, String, String) -> Pair<Int, String>?
    private val calls = AtomicInteger()
    private val bodies = mutableListOf<String>()
    private val urls = mutableListOf<String>()

    private fun prefs() = ctx.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    private fun fakeHttp(answer: () -> Pair<Int, String>?) {
        SpotifyAuth.http = { _, url, body, _ ->
            synchronized(bodies) { calls.incrementAndGet(); urls += url; bodies += body }
            answer()
        }
    }

    /** Conectado, con el token vencido (o vigente) y un token de renovación. */
    private fun seed(expiresInMs: Long, refresh: String? = "r1", clientId: String? = "cid") {
        prefs().edit().clear().commit()
        if (clientId != null) SpotifyAuth.setClientId(ctx, clientId)
        prefs().edit().putString("access", "viejo").putLong("expires", System.currentTimeMillis() + expiresInMs).apply {
            if (refresh != null) putString("refresh", refresh)
        }.commit()
    }

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        saved = SpotifyAuth.http
        calls.set(0)
        bodies.clear(); urls.clear()
    }

    @After
    fun tearDown() {
        SpotifyAuth.http = saved
    }

    @Test
    fun token_vigente_no_pide_nada() {
        seed(expiresInMs = 600_000)
        fakeHttp { error("no tendría que pedir nada") }
        assertEquals("viejo", SpotifyAuth.token(ctx))
        assertEquals(0, calls.get())
    }

    @Test
    fun vencido_renueva_guarda_y_la_vez_siguiente_no_pide() {
        seed(expiresInMs = -1_000)
        fakeHttp { 200 to """{"access_token":"nuevo","expires_in":3600}""" }
        assertEquals("nuevo", SpotifyAuth.token(ctx))
        assertEquals("https://accounts.spotify.com/api/token", urls.single())
        val body = bodies.single()
        // El Client ID de la compilación (secrets.properties o GitHub Secrets) le gana al guardado
        val cid = SpotifyAuth.builtInClientId() ?: "cid"
        assertTrue(body, "grant_type=refresh_token" in body && "refresh_token=r1" in body && "client_id=$cid" in body)
        assertEquals("Spotify no manda otro refresh: se conserva el que había", "r1", prefs().getString("refresh", null))
        assertEquals("nuevo", SpotifyAuth.token(ctx))
        assertEquals("el token nuevo ya sirve", 1, calls.get())
    }

    @Test
    fun si_spotify_manda_un_refresh_nuevo_se_guarda() {
        seed(expiresInMs = -1_000)
        fakeHttp { 200 to """{"access_token":"nuevo","expires_in":3600,"refresh_token":"r2"}""" }
        assertEquals("nuevo", SpotifyAuth.token(ctx))
        assertEquals("r2", prefs().getString("refresh", null))
    }

    @Test
    fun un_corte_de_red_o_un_error_del_servidor_no_desconectan() {
        for (answer in listOf<Pair<Int, String>?>(null, 500 to "x", 503 to "x", 429 to "x", 401 to "x", 200 to "esto no es json")) {
            seed(expiresInMs = -1_000)
            fakeHttp { answer }
            assertNull("$answer", SpotifyAuth.token(ctx))
            assertTrue("$answer: tiene que seguir conectado", SpotifyAuth.isConnected(ctx))
        }
    }

    @Test
    fun si_spotify_revoca_el_permiso_400_se_desconecta() {
        seed(expiresInMs = -1_000)
        fakeHttp { 400 to """{"error":"invalid_grant"}""" }
        assertNull(SpotifyAuth.token(ctx))
        assertFalse(SpotifyAuth.isConnected(ctx))
        assertEquals("desconectado", SpotifyAuth.status.value)
    }

    @Test
    fun sin_cuenta_conectada_o_sin_client_id_no_pide_nada() {
        fakeHttp { error("no tendría que pedir nada") }
        seed(expiresInMs = -1_000, refresh = null)
        assertNull(SpotifyAuth.token(ctx))
        assertEquals(0, calls.get())
        // Sin Client ID guardado solo se puede probar si la compilación tampoco trae uno
        org.junit.Assume.assumeTrue(SpotifyAuth.builtInClientId() == null)
        seed(expiresInMs = -1_000, clientId = null)
        assertNull(SpotifyAuth.token(ctx))
        assertEquals(0, calls.get())
    }

    /** AUD-053: dos pedidos a la vez con el token vencido renuevan una sola vez; el segundo usa el nuevo. */
    @Test
    fun dos_pedidos_a_la_vez_renuevan_una_sola_vez() {
        seed(expiresInMs = -1_000)
        fakeHttp {
            Thread.sleep(200)   // da tiempo a que el otro pedido llegue mientras se renueva
            200 to """{"access_token":"nuevo","expires_in":3600}"""
        }
        val pool = Executors.newFixedThreadPool(2)
        val go = CountDownLatch(1)
        val results = (1..2).map { pool.submit<String?> { go.await(); SpotifyAuth.token(ctx) } }
        go.countDown()
        val tokens = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(listOf("nuevo", "nuevo"), tokens)
        assertEquals("una sola renovación", 1, calls.get())
    }

    /** Deja un permiso "en curso": el `state` que la app mandó a Spotify (AUD-093). */
    private fun pending(state: String = "st1", verifier: String? = null) {
        prefs().edit().putString("state", state).apply { if (verifier != null) putString("verifier", verifier) }.commit()
    }

    @Test
    fun redireccion_con_error_o_sin_codigo_no_pide_tokens() {
        seed(expiresInMs = 600_000)
        fakeHttp { error("no tendría que pedir nada") }
        pending()
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?error=access_denied&state=st1"))
        assertTrue(SpotifyAuth.status.value, "access_denied" in SpotifyAuth.status.value)
        pending()
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?state=st1"))
        assertTrue(SpotifyAuth.status.value, "sin código" in SpotifyAuth.status.value)
        // con código pero sin haber empezado el permiso (sin verificador): no se pide nada
        pending()
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc&state=st1"))
        assertEquals(0, calls.get())
    }

    /** AUD-093: una redirección que no trae el `state` del pedido de la app se ignora, y no toca el estado. */
    @Test
    fun redireccion_con_otro_state_o_sin_state_se_ignora_sin_tocar_nada() {
        seed(expiresInMs = 600_000)
        SpotifyAuth.refreshStatus(ctx)
        assertEquals("conectado", SpotifyAuth.status.value)
        pending(verifier = "ver123")
        fakeHttp { error("no tendría que pedir nada") }
        for (uri in listOf(
            "onixlauncher://spotify?code=abc&state=otro",
            "onixlauncher://spotify?code=abc",
            "onixlauncher://spotify?error=access_denied",
        )) SpotifyAuth.handleRedirect(ctx, Uri.parse(uri))
        assertEquals(0, calls.get())
        assertEquals("ni siquiera cambia el mensaje", "conectado", SpotifyAuth.status.value)
        assertEquals("el permiso en curso sigue valiendo", "st1", prefs().getString("state", null))
        assertEquals("ver123", prefs().getString("verifier", null))
    }

    @Test
    fun sin_un_permiso_en_curso_se_ignora_cualquier_redireccion() {
        seed(expiresInMs = 600_000)
        SpotifyAuth.refreshStatus(ctx)
        fakeHttp { error("no tendría que pedir nada") }
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc&state=st1"))
        assertEquals(0, calls.get())
        assertEquals("conectado", SpotifyAuth.status.value)
    }

    @Test
    fun el_state_es_de_un_solo_uso() {
        seed(expiresInMs = -1_000, refresh = null)
        pending(verifier = "ver123")
        fakeHttp { 200 to """{"access_token":"a1","expires_in":3600,"refresh_token":"r9"}""" }
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc&state=st1"))
        assertEquals(1, calls.get())
        assertNull(prefs().getString("state", null))
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc&state=st1"))
        assertEquals("la misma redirección repetida no vuelve a pedir tokens", 1, calls.get())
    }

    @Test
    fun el_pedido_de_permiso_manda_un_state_distinto_cada_vez() {
        seed(expiresInMs = -1_000, refresh = null)
        val app = RuntimeEnvironment.getApplication()
        fun login(): Pair<String?, String?> {
            SpotifyAuth.startLogin(ctx)
            val sent = Uri.parse(org.robolectric.Shadows.shadowOf(app).nextStartedActivity.dataString)
            return sent.getQueryParameter("state") to prefs().getString("state", null)
        }
        val (sent1, saved1) = login()
        val (sent2, saved2) = login()
        assertTrue("el state sale en el enlace: $sent1", !sent1.isNullOrEmpty() && sent1.length >= 16)
        assertEquals("y es el que la app guardó para comparar", saved1, sent1)
        assertEquals(saved2, sent2)
        assertTrue("cambia en cada pedido", sent1 != sent2)
    }

    @Test
    fun redireccion_con_codigo_conecta_y_el_verificador_es_de_un_solo_uso() {
        seed(expiresInMs = -1_000, refresh = null)
        pending(verifier = "ver123")
        fakeHttp { 200 to """{"access_token":"a1","expires_in":3600,"refresh_token":"r9"}""" }
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc%2Fdef&state=st1"))
        val body = bodies.single()
        assertTrue(body, "grant_type=authorization_code" in body && "code=abc%2Fdef" in body && "code_verifier=ver123" in body)
        assertTrue(SpotifyAuth.isConnected(ctx))
        assertEquals("conectado", SpotifyAuth.status.value)
        assertNull("el verificador se borra al usarlo", prefs().getString("verifier", null))
    }

    @Test
    fun redireccion_con_error_del_servidor_avisa_y_no_conecta() {
        seed(expiresInMs = -1_000, refresh = null)
        pending(verifier = "ver123")
        fakeHttp { 400 to """{"error":"invalid_grant"}""" }
        SpotifyAuth.handleRedirect(ctx, Uri.parse("onixlauncher://spotify?code=abc&state=st1"))
        assertFalse(SpotifyAuth.isConnected(ctx))
        assertEquals("no se pudo conectar (HTTP 400)", SpotifyAuth.status.value)
    }
}
