package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/*
 * Spotify dentro del launcher (v0.35.0): listas, recientes y buscador con la API web de Spotify, y
 * reproducir en el equipo con Spotify Connect (requiere Premium).
 *
 * Inicio de sesión con PKCE: el autor registra una app en developer.spotify.com (Redirect URI
 * `onixlauncher://spotify`) y pega su Client ID en el launcher. El Client ID y los tokens se guardan
 * solo en el equipo (SharedPreferences `spotify`); no van al código, al repo ni a los registros.
 */

const val SPOTIFY_REDIRECT = "onixlauncher://spotify"
private const val SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing " +
    "user-read-recently-played playlist-read-private playlist-read-collaborative user-library-read"

/** Algo para reproducir: una lista, un álbum, un artista o un tema, con su portada y contexto si la API los informa. */
data class SpotifyItem(
    val name: String,
    val subtitle: String,
    val uri: String,
    val isTrack: Boolean,
    val imageUrl: String? = null,
    val contextUri: String? = null,
)

object SpotifyAuth {
    /** "desconectado", "conectado", o un mensaje de error para mostrar. */
    val status = mutableStateOf("desconectado")

    /**
     * Cómo se hacen los pedidos de tokens (método, enlace, cuerpo, tipo). AUD-085 (v0.79.6): los
     * tests lo reemplazan para probar la renovación sin red; en la app es siempre [Net.request].
     */
    internal var http: (String, String, String, String) -> Pair<Int, String>? =
        { method, url, body, type -> Net.request(method, url, body, type) }

    private fun prefs(c: Context) = c.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    /**
     * El Client ID que se usa: el de la app (GitHub Secret `SPOTIFY_CLIENT_ID`, puesto al compilar)
     * gana sobre el pegado a mano, así uno viejo escrito a mano no lo tapa. Pura, para los tests.
     */
    fun pickClientId(saved: String?, builtIn: String?): String? =
        builtIn?.trim()?.takeIf { it.isNotEmpty() } ?: saved?.trim()?.takeIf { it.isNotEmpty() }

    /** El Client ID que trae la app compilada, o null si el secreto no estaba cargado. */
    fun builtInClientId(): String? = pickClientId(null, BuildConfig.SPOTIFY_CLIENT_ID)

    fun clientId(c: Context): String? = pickClientId(prefs(c).getString("client_id", null), BuildConfig.SPOTIFY_CLIENT_ID)

    fun setClientId(c: Context, id: String) {
        prefs(c).edit().putString("client_id", id.trim()).apply()
    }

    fun isConnected(c: Context): Boolean = prefs(c).getString("refresh", null) != null

    fun refreshStatus(c: Context) {
        status.value = if (isConnected(c)) "conectado" else "desconectado"
    }

    fun logout(c: Context) {
        prefs(c).edit().remove("access").remove("refresh").remove("expires").apply()
        status.value = "desconectado"
    }

    /** Desafío PKCE (S256) de un verificador (función pura, para los tests). */
    fun challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Abre la página de Spotify para dar permiso; vuelve al launcher por `onixlauncher://spotify`. */
    fun startLogin(c: Context) {
        val id = clientId(c) ?: return
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        // AUD-093 (v0.79.6): `state` al azar; Spotify lo devuelve y solo así se acepta la redirección
        val state = Base64.encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        prefs(c).edit().putString("verifier", verifier).putString("state", state).apply()
        val url = "https://accounts.spotify.com/authorize?response_type=code" +
            "&client_id=" + enc(id) + "&redirect_uri=" + enc(SPOTIFY_REDIRECT) +
            "&code_challenge_method=S256&code_challenge=" + challenge(verifier) + "&scope=" + enc(SCOPES) +
            "&state=" + enc(state)
        status.value = "esperando el permiso en Spotify…"
        startSafe(c, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Recibe el código de la redirección y lo cambia por tokens. Llamar fuera del hilo principal. */
    fun handleRedirect(c: Context, uri: Uri) {
        // AUD-093: `onixlauncher://spotify` lo puede abrir cualquier app del equipo. Solo vale la
        // redirección que trae el `state` del permiso que pidió Onix; es de un solo uso y, si no
        // coincide, se ignora sin cambiar ni el mensaje de estado.
        val expected = prefs(c).getString("state", null)
        if (expected == null || uri.getQueryParameter("state") != expected) {
            AppLog.warn("Spotify: redirección ignorada (no corresponde a un permiso pedido desde la app)")
            return
        }
        prefs(c).edit().remove("state").apply()
        val code = uri.getQueryParameter("code")
        if (code == null) {
            status.value = "Spotify no dio permiso (${uri.getQueryParameter("error") ?: "sin código"})"
            return
        }
        val id = clientId(c) ?: return
        val verifier = prefs(c).getString("verifier", null) ?: return
        val body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(SPOTIFY_REDIRECT) +
            "&client_id=" + enc(id) + "&code_verifier=" + enc(verifier)
        val r = http("POST", "https://accounts.spotify.com/api/token", body, "application/x-www-form-urlencoded")
        if (r == null || r.first !in 200..299 || !saveTokens(c, r.second)) {
            status.value = "no se pudo conectar (HTTP ${r?.first ?: "sin red"})"
            AppLog.warn("Spotify: no se pudo cambiar el código por tokens (HTTP ${r?.first})")
            return
        }
        prefs(c).edit().remove("verifier").apply()   // de un solo uso
        AppLog.info("Spotify: conectado")
        status.value = "conectado"
    }

    private fun saveTokens(c: Context, json: String): Boolean = try {
        val o = JSONObject(json)
        val e = prefs(c).edit().putString("access", o.getString("access_token"))
            .putLong("expires", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 60_000)
        if (o.has("refresh_token")) e.putString("refresh", o.getString("refresh_token"))
        e.apply()
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Token vigente (lo renueva si venció), o null si no está conectado. Fuera del hilo principal.
     * AUD-053: sincronizado. Con dos pedidos a la vez (listas y reproducir) se renovaba dos veces;
     * Spotify invalida el primer token de renovación y el segundo pedido recibía 400 → se desconectaba.
     */
    @Synchronized
    fun token(c: Context): String? {
        val p = prefs(c)
        val access = p.getString("access", null)
        if (access != null && System.currentTimeMillis() < p.getLong("expires", 0)) return access
        val refresh = p.getString("refresh", null) ?: return null
        val id = clientId(c) ?: return null
        val r = http(
            "POST", "https://accounts.spotify.com/api/token",
            "grant_type=refresh_token&refresh_token=" + enc(refresh) + "&client_id=" + enc(id),
            "application/x-www-form-urlencoded"
        )
        if (r == null || r.first !in 200..299 || !saveTokens(c, r.second)) {
            if (r != null && r.first == 400) logout(c)   // permiso revocado: hay que conectar de nuevo
            return null
        }
        return p.getString("access", null)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

object SpotifyApi {
    private const val BASE = "https://api.spotify.com/v1"

    private fun get(c: Context, path: String): String? {
        val t = SpotifyAuth.token(c) ?: return null
        val r = Net.request("GET", BASE + path, headers = mapOf("Authorization" to "Bearer $t")) ?: return null
        return r.second.takeIf { r.first in 200..299 }
    }

    fun playlists(c: Context): List<SpotifyItem> = get(c, "/me/playlists?limit=40")?.let { parsePlaylists(it) }.orEmpty()

    fun recent(c: Context): List<SpotifyItem> = get(c, "/me/player/recently-played?limit=30")?.let { parseRecent(it) }.orEmpty()

    fun search(c: Context, q: String): List<SpotifyItem> =
        get(c, "/search?type=track,playlist,artist,album&limit=8&q=" + URLEncoder.encode(q, "UTF-8"))?.let { parseSearch(it) }.orEmpty()

    /** Una página de temas de una lista y cuántos tiene la lista en total. */
    data class TrackPage(val items: List<SpotifyItem>, val total: Int)

    const val PLAYLIST_PAGE = 50
    const val PLAYLIST_MAX = 500

    /** Id de una lista a partir de su URI (`spotify:playlist:<id>`), o null si no es una lista. Pura. */
    fun playlistId(uri: String): String? =
        uri.removePrefix("spotify:playlist:").takeIf { it != uri && it.isNotBlank() && ':' !in it }

    /**
     * v0.81.17: temas de una lista para elegir cuál escuchar (hasta [PLAYLIST_MAX], de a [PLAYLIST_PAGE]).
     * Devuelve null si Spotify no deja leerla (pasa con algunas listas armadas por Spotify).
     */
    fun playlistTracks(c: Context, playlistUri: String): List<SpotifyItem>? {
        val id = playlistId(playlistUri) ?: return null
        val out = mutableListOf<SpotifyItem>()
        var offset = 0
        while (offset < PLAYLIST_MAX) {
            val json = get(c, "/playlists/$id/items?limit=$PLAYLIST_PAGE&offset=$offset") ?: return out.ifEmpty { null }
            val page = parsePlaylistTracks(json, playlistUri)
            out += page.items
            offset += PLAYLIST_PAGE
            if (offset >= page.total) break
        }
        return out
    }

    /**
     * Lee una página de `GET /playlists/{id}/items`; cada tema lleva la lista como contexto. Pura.
     * v0.81.20: Spotify eliminó `/tracks` en 2026 y el tema viene en `item` (antes `track`); máximo 50 por página.
     */
    fun parsePlaylistTracks(json: String, playlistUri: String): TrackPage = try {
        val o = JSONObject(json)
        val items = o.getJSONArray("items")
        TrackPage(
            (0 until items.length()).mapNotNull { i ->
                items.optJSONObject(i)?.let { it.optJSONObject("item") ?: it.optJSONObject("track") }?.let { track(it) }?.copy(contextUri = playlistUri)
            },
            o.optInt("total", 0)
        )
    } catch (e: Exception) {
        TrackPage(emptyList(), 0)
    }

    /**
     * Cuerpo JSON para `PUT /me/player/play`. En el estéreo del auto, mandar `{"uris":[...]}` suelto
     * para un tema de Recientes o Buscar no arranca la reproducción, mientras que `context_uri`
     * (como en las listas) sí funciona: si el tema trae el URI de su álbum, se manda como
     * `context_uri` con `offset.uri` apuntando al tema elegido (v0.81.0). Pura, para los tests.
     */
    fun playBody(item: SpotifyItem): String = when {
        item.isTrack && !item.contextUri.isNullOrBlank() ->
            JSONObject()
                .put("context_uri", item.contextUri)
                .put("offset", JSONObject().put("uri", item.uri))
                .toString()
        item.isTrack -> JSONObject().put("uris", JSONArray().put(item.uri)).toString()
        else -> JSONObject().put("context_uri", item.uri).toString()
    }

    /**
     * Reproduce [item] en este equipo (Spotify Connect, Premium). Busca el dispositivo activo o el
     * Android; si Spotify no está abierto en ningún lado devuelve un mensaje para mostrar.
     */
    fun play(c: Context, item: SpotifyItem): String? {
        val t = SpotifyAuth.token(c) ?: return "Conectá Spotify primero"
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            leaveAntenna(c)
            LastAudio.markSpotify(c)
        }
        val auth = mapOf("Authorization" to "Bearer $t")
        val devices = Net.request("GET", "$BASE/me/player/devices", headers = auth)?.second?.let { parseDevices(it) }.orEmpty()
        val device = devices.firstOrNull { it.second } ?: devices.firstOrNull()
        if (device == null) {
            main.post { playUriOtherApp(c, SPOTIFY, item.uri) }
            return "Abrí Spotify una vez en la radio para que aparezca como dispositivo"
        }
        val body = playBody(item)
        var r = Net.request("PUT", "$BASE/me/player/play?device_id=" + device.first, body, headers = auth)
        if ((r == null || r.first !in 200..299) && item.isTrack && !item.contextUri.isNullOrBlank()) {
            val fallback = JSONObject().put("uris", JSONArray().put(item.uri)).toString()
            r = Net.request("PUT", "$BASE/me/player/play?device_id=" + device.first, fallback, headers = auth)
        }
        if (r != null && r.first in 200..299) {
            main.postDelayed({
                if (readMedia(c).info?.let { it.pkg == SPOTIFY && it.playing } != true) {
                    spotifyPlay(c)
                }
            }, 600L)
        }
        return when {
            r == null -> "Sin conexión"
            r.first in 200..299 -> null
            r.first == 403 -> "Spotify no deja reproducir (hace falta Premium)"
            else -> "Spotify respondió HTTP ${r.first}"
        }
    }

    // ---- lectura de respuestas (funciones puras, para los tests) ----

    fun parsePlaylists(json: String): List<SpotifyItem> = try {
        val items = JSONObject(json).getJSONArray("items")
        (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val owner = o.optJSONObject("owner")?.optString("display_name").orEmpty()
            val count = (o.optJSONObject("items") ?: o.optJSONObject("tracks"))?.optInt("total", -1) ?: -1
            SpotifyItem(
                o.optString("name"),
                listOfNotNull(owner.ifBlank { null }, if (count >= 0) "$count temas" else null).joinToString(" · "),
                o.optString("uri"),
                false,
                imageUrl(o),
            ).takeIf { it.uri.isNotBlank() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun parseRecent(json: String): List<SpotifyItem> = try {
        val items = JSONObject(json).getJSONArray("items")
        val seen = HashSet<String>()
        (0 until items.length()).mapNotNull { i ->
            val tr = items.optJSONObject(i)?.optJSONObject("track") ?: return@mapNotNull null
            track(tr)?.takeIf { seen.add(it.uri) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun parseSearch(json: String): List<SpotifyItem> = try {
        val o = JSONObject(json)
        val out = mutableListOf<SpotifyItem>()
        o.optJSONObject("tracks")?.optJSONArray("items")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { track(it) }?.let { out += it } }
        o.optJSONObject("artists")?.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                out += SpotifyItem(it.optString("name"), "Artista", it.optString("uri"), false, imageUrl(it))
            }
        }
        o.optJSONObject("albums")?.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                out += SpotifyItem(it.optString("name"), "Álbum · " + artists(it), it.optString("uri"), false, imageUrl(it))
            }
        }
        o.optJSONObject("playlists")?.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                out += SpotifyItem(it.optString("name"), "Lista", it.optString("uri"), false, imageUrl(it))
            }
        }
        out.filter { it.uri.isNotBlank() && it.name.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    /** Dispositivos de Spotify Connect: (id, activo). */
    fun parseDevices(json: String): List<Pair<String, Boolean>> = try {
        val a = JSONObject(json).getJSONArray("devices")
        (0 until a.length()).mapNotNull { i ->
            val d = a.optJSONObject(i) ?: return@mapNotNull null
            val id = d.optString("id")
            if (id.isBlank() || d.optBoolean("is_restricted")) null else id to d.optBoolean("is_active")
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Actualiza la lista de últimas búsquedas (sin duplicados, máximo [max]). Pura. */
    fun pushQuery(current: List<String>, query: String, max: Int = 6): List<String> {
        val q = query.trim()
        if (q.length < 2) return current
        return (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(max)
    }

    fun recentQueries(c: Context): List<String> =
        c.getSharedPreferences("spotify", Context.MODE_PRIVATE).getString("recent_queries", null)
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    fun rememberQuery(c: Context, query: String) {
        val updated = pushQuery(recentQueries(c), query)
        c.getSharedPreferences("spotify", Context.MODE_PRIVATE).edit()
            .putString("recent_queries", updated.joinToString("\n")).apply()
    }

    private fun track(o: JSONObject): SpotifyItem? {
        val uri = o.optString("uri")
        if (uri.isBlank()) return null
        val albumUri = o.optJSONObject("album")?.optString("uri")?.takeIf { it.isNotBlank() }
        return SpotifyItem(o.optString("name"), artists(o), uri, true, imageUrl(o), albumUri)
    }

    /** Extrae la portada mediana/chica de un objeto de Spotify (o de su álbum si es un tema). */
    private fun imageUrl(o: JSONObject): String? {
        val arr = o.optJSONArray("images") ?: o.optJSONObject("album")?.optJSONArray("images") ?: return null
        if (arr.length() == 0) return null
        val pick = arr.optJSONObject(1.coerceAtMost(arr.length() - 1)) ?: arr.optJSONObject(0)
        return pick?.optString("url")?.takeIf { it.isNotBlank() }
    }

    private fun artists(o: JSONObject): String {
        val a = o.optJSONArray("artists") ?: return ""
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") }.joinToString(", ")
    }
}

/** Caché en memoria de portadas de listas, álbumes y temas de Spotify (v0.79.19). */
internal object SpotifyCovers {
    private val memory = android.util.LruCache<String, ImageBitmap>(60)

    fun cached(url: String?): ImageBitmap? = url?.let { memory.get(it) }

    fun fetch(url: String): ImageBitmap? {
        memory.get(url)?.let { return it }
        val bmp = Net.getBytes(url)?.let { RadioLogos.decode(it) } ?: return null
        return bmp.asImageBitmap().also { memory.put(url, it) }
    }
}

/** Renglón de la letra de una canción; [timeMs] es -1 si la letra no trae tiempos sincronizados. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Letras de canciones en vivo desde LRCLIB (v0.79.19, gratis y sin clave). Busca por título y
 * artista de lo que está sonando y devuelve los renglones sincronizados en milisegundos.
 */
object Lyrics {
    private val cache = android.util.LruCache<String, List<LyricLine>>(30)

    fun seed(title: String, artist: String, lines: List<LyricLine>) {
        cache.put("${title.trim().lowercase()}|${artist.trim().lowercase()}", lines)
    }

    private val lrcRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.*)""")

    /** Convierte texto en formato LRC ("[00:12.40] Frase") a renglones ordenados por tiempo. Pura. */
    fun parseLrc(raw: String): List<LyricLine> =
        raw.lineSequence().mapNotNull { line ->
            val m = lrcRegex.matchEntire(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val sec = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fracStr = m.groupValues[3]
            val fracMs = when (fracStr.length) {
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val text = m.groupValues[4].trim()
            if (text.isEmpty()) null else LyricLine(min * 60_000L + sec * 1_000L + fracMs, text)
        }.sortedBy { it.timeMs }.toList()

    /** Extrae la letra (sincronizada si hay, o texto plano) de un JSON de LRCLIB. Pura. */
    fun parseResponse(json: String): List<LyricLine> = try {
        val trim = json.trim()
        val o = if (trim.startsWith("[")) JSONArray(trim).optJSONObject(0) else JSONObject(trim)
        if (o == null) emptyList()
        else {
            val synced = o.optString("syncedLyrics").trim()
            val syncedLines = if (synced.isNotEmpty() && synced != "null") parseLrc(synced) else emptyList()
            if (syncedLines.isNotEmpty()) syncedLines
            else {
                val plain = o.optString("plainLyrics").trim()
                if (plain.isNotEmpty() && plain != "null") {
                    plain.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1L, it) }.toList()
                } else emptyList()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Índice del renglón que suena en [positionMs], o -1 si no hay tiempos sincronizados. Pura. */
    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
        lines.indexOfLast { it.timeMs in 0..positionMs }

    fun fetch(title: String?, artist: String?): List<LyricLine> {
        val t = title?.trim().orEmpty()
        if (t.isEmpty() || t.equals("Nada sonando", ignoreCase = true)) return emptyList()
        val a = artist?.trim().orEmpty()
        val key = "${t.lowercase()}|${a.lowercase()}"
        cache.get(key)?.let { return it }
        val encT = URLEncoder.encode(t, "UTF-8")
        val encA = URLEncoder.encode(a, "UTF-8")
        val direct = Net.get("https://lrclib.net/api/get?track_name=$encT&artist_name=$encA")?.let { parseResponse(it) }.orEmpty()
        val res = direct.ifEmpty {
            val q = URLEncoder.encode("$t $a".trim(), "UTF-8")
            Net.get("https://lrclib.net/api/search?q=$q")?.let { parseResponse(it) }.orEmpty()
        }
        if (res.isNotEmpty()) cache.put(key, res)
        return res
    }
}
