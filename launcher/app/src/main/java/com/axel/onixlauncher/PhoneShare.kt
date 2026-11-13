package com.axel.onixlauncher

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/*
 * Destinos desde el celular (v0.76.0, pedido del autor): "me pasan por WhatsApp una dirección y la
 * abro con Onix", sin cable. En el celular, Compartir → ntfy (app gratuita y de código abierto) al
 * canal privado del autor; Onix escucha ese canal por internet (ntfy.sh), busca la dirección con
 * TomTom y pregunta "¿Ir a …?". Entiende una dirección escrita, la ubicación que manda WhatsApp,
 * un link de Google Maps (también los cortos, maps.app.goo.gl) y coordenadas sueltas.
 * El nombre del canal es del autor: GitHub Secret NTFY_TOPIC, entra al compilar (BuildConfig).
 */

/** Lo que llegó del celular, ya leído (pura, para los tests). */
sealed class SharedDest {
    data class Coords(val lat: Double, val lon: Double, val label: String?) : SharedDest()
    data class Link(val url: String) : SharedDest()
    data class Query(val text: String) : SharedDest()
}

object PhoneShare {
    /** Solo se toman mensajes de los últimos 15 min: la radio pudo estar apagada horas. */
    const val MAX_AGE_S = 15 * 60L
    private const val SERVER = "https://ntfy.sh"

    val topic: String get() = BuildConfig.NTFY_TOPIC
    /** Destino listo para confirmar en pantalla ("¿Ir a …?"). */
    val offer = mutableStateOf<Place?>(null)
    /** Aviso corto cuando no se encontró lo que llegó. */
    val notFound = mutableStateOf<String?>(null)
    /** Para Diagnóstico y el informe: "sin canal", "conectado", "sin conexión, reintenta…". */
    val status = mutableStateOf("sin iniciar")
    @Volatile private var started = false

    private val URL_RE = Regex("""https?://\S+|geo:\S+""")
    private val COORDS_RE = Regex("""(-?\d{1,3}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")

    private fun decode(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }
    private fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim().trim(',', ' ')

    private fun valid(lat: Double, lon: Double) = lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

    /** Coordenadas de un link de Maps o geo: (lugar exacto !3d!4d, q=, query=, destination=, @). Pura. */
    fun coordsFromUrl(url: String): Pair<Double, Double>? {
        val u = decode(url)
        val patterns = listOf(
            Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
            Regex("""[?&](?:q|query|ll|destination|daddr|center)=(?:loc:)?(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)"""),
            Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)"""),
            Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
        )
        for (p in patterns) {
            val m = p.find(u) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lon = m.groupValues[2].toDoubleOrNull() ?: continue
            if (valid(lat, lon)) return lat to lon
        }
        return null
    }

    /** Dirección escrita dentro de un link de Maps (q=, query=, /place/Nombre/). Pura. */
    private fun queryFromUrl(url: String): String? {
        Regex("""[?&](?:q|query|destination|daddr)=([^&#]+)""").find(url)?.let { return clean(decode(it.groupValues[1].replace("+", " "))) }
        Regex("""/maps/(?:place|search)/([^/@?]+)""").find(url)?.let { return clean(decode(it.groupValues[1].replace("+", " "))) }
        return null
    }

    /** Lee lo que llegó: coordenadas, link de Maps, link corto o una dirección escrita. Pura. */
    fun parse(text: String): SharedDest? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val urls = URL_RE.findAll(t).map { it.value.trimEnd('.', ',', ')') }.toList()
        val rest = clean(URL_RE.replace(t, " "))
        for (u in urls) {
            coordsFromUrl(u)?.let { (lat, lon) -> return SharedDest.Coords(lat, lon, rest.ifEmpty { null }) }
        }
        COORDS_RE.find(t)?.let { m ->
            val lat = m.groupValues[1].toDouble()
            val lon = m.groupValues[2].toDouble()
            if (valid(lat, lon)) return SharedDest.Coords(lat, lon, clean(t.replace(m.value, " ")).ifEmpty { null })
        }
        for (u in urls) {
            queryFromUrl(u)?.takeIf { it.isNotEmpty() }?.let { return SharedDest.Query(it) }
            if (Regex("""https?://(maps\.app\.goo\.gl|goo\.gl/maps|g\.co/kgs)/""").containsMatchIn(u)) return SharedDest.Link(u)
        }
        return rest.takeIf { it.isNotEmpty() }?.let { SharedDest.Query(it.take(150)) }
    }

    /** Un renglón del canal de ntfy: (id, hora en s, texto) si es un mensaje; null si no. Pura. */
    fun parseEvent(line: String): Triple<String, Long, String>? = try {
        val o = JSONObject(line)
        if (o.optString("event") != "message") null
        else Triple(o.getString("id"), o.optLong("time"), o.optString("message"))
    } catch (e: Exception) {
        null
    }

    fun isFresh(sentS: Long, nowS: Long): Boolean = nowS - sentS <= MAX_AGE_S

    /** Enlace del canal; con [since] sigue desde el último mensaje leído (AUD-085, v0.79.7: pura, para los tests). */
    internal fun streamUrl(topic: String, since: String?): String =
        "$SERVER/${URLEncoder.encode(topic, "UTF-8")}/json" + (since?.let { "?since=$it" } ?: "")

    /**
     * Lee los renglones del canal: guarda el id del último mensaje (para seguir desde ahí al reconectar,
     * también si era viejo) y entrega solo los frescos. [now] se mira en cada mensaje: la conexión dura horas.
     */
    internal fun consume(lines: Sequence<String>, prefs: android.content.SharedPreferences, now: () -> Long, onMessage: (String) -> Unit) {
        for (line in lines) {
            val ev = parseEvent(line) ?: continue
            prefs.edit().putString("ultimo", ev.first).apply()
            if (isFresh(ev.second, now())) onMessage(ev.third)
        }
    }

    /** Espera antes de reintentar la conexión: se duplica hasta un minuto. */
    internal fun nextWait(wait: Long): Long = (wait * 2).coerceAtMost(60_000L)

    /** Sigue un link corto (maps.app.goo.gl) hasta un link con coordenadas o dirección. Bloquea. */
    private fun follow(link: String): SharedDest? {
        var url = link
        repeat(6) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.1)")
            }
            try {
                val code = conn.responseCode
                val next = conn.getHeaderField("Location")
                if (code in 300..399 && next != null) {
                    url = URL(URL(url), next).toString()
                    val d = parse(url)
                    if (d is SharedDest.Coords || d is SharedDest.Query) return d
                } else return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /** El lugar al que hay que ir, o null si no se encontró. Bloquea. */
    fun resolve(context: Context, dest: SharedDest): Place? = when (dest) {
        is SharedDest.Coords -> Place(dest.label ?: NavApi.reverse(dest.lat, dest.lon) ?: "Ubicación compartida", dest.lat, dest.lon)
        is SharedDest.Query -> {
            // Cerca de donde está el auto; sin ubicación, la casa del autor (DEFAULT_LAT/LON)
            val near = try { lastLocation(context) } catch (e: Exception) { null }
            NavApi.search(dest.text, near?.latitude ?: DEFAULT_LAT, near?.longitude ?: DEFAULT_LON)?.firstOrNull()
        }
        is SharedDest.Link -> follow(dest.url)?.let { resolve(context, it) }
    }

    /** v0.81.22: destino que llegó sin internet; se busca de nuevo cuando vuelve la red. */
    @Volatile private var waiting: String? = null

    /** Qué se le dice al usuario cuando el destino no se pudo ubicar. Pura. */
    fun failureText(text: String, offline: Boolean): String =
        if (offline) "Sin internet: busco \"${text.take(60)}\" cuando vuelva" else "No encontré \"${text.take(60)}\""

    private fun handle(app: Context, text: String) {
        val dest = parse(text) ?: return
        AppLog.info("Celular: llegó un destino (${dest.javaClass.simpleName})")
        val place = try {
            resolve(app, dest)
        } catch (e: Exception) {
            AppLog.warn("Celular: no se pudo leer el destino", e)
            WifiRescue.failed(e)
            null
        }
        // Antes se daba por no encontrado y el mensaje del canal ya quedaba leído: se perdía
        val offline = place == null && WifiRescue.down
        waiting = if (offline) text else null
        if (offline) AppLog.info("Celular: sin internet; el destino queda en espera")
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            if (place != null) { notFound.value = null; offer.value = place }
            else notFound.value = failureText(text, offline)
        }
    }

    /** La red volvió: se busca el destino que había quedado en espera. */
    fun retryWaiting(app: Context) {
        val text = waiting ?: return
        waiting = null
        kotlin.concurrent.thread(isDaemon = true, name = "onix-destino") { handle(app, text) }
    }

    /** Escucha el canal mientras la app esté abierta; se reconecta solo. */
    fun start(context: Context) {
        if (started) return
        started = true
        if (topic.isBlank()) { status.value = "sin canal (falta NTFY_TOPIC)"; return }
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("celular", Context.MODE_PRIVATE)
        Thread({
            var wait = 5_000L
            while (true) {
                var conn: HttpURLConnection? = null
                try {
                    val since = prefs.getString("ultimo", null)
                    val url = streamUrl(topic, since)
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 100_000          // ntfy manda un "keepalive" cada 45 s
                        setRequestProperty("User-Agent", "OnixLauncher/${BuildConfig.VERSION_NAME}")
                    }
                    if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
                    status.value = "conectado"
                    wait = 5_000L
                    conn.inputStream.bufferedReader().useLines { lines ->
                        consume(lines, prefs, now = { System.currentTimeMillis() / 1000 }) { handle(app, it) }
                    }
                } catch (e: Exception) {
                    status.value = "sin conexión (${e.javaClass.simpleName}); reintenta en ${wait / 1000} s"
                } finally {
                    conn?.disconnect()
                }
                Thread.sleep(wait)
                wait = nextWait(wait)
            }
        }, "onix-celular").apply { isDaemon = true }.start()
    }
}

/** Cartel "¿Ir a …?" cuando llega un destino del celular (v0.76.0), arriba de cualquier pantalla. */
@androidx.compose.runtime.Composable
internal fun PhoneShareOverlay(onGo: (Place) -> Unit) {
    val place = PhoneShare.offer.value
    val miss = PhoneShare.notFound.value
    if (place == null && miss == null) return
    // El aviso de "no encontré" se va solo a los 8 s
    if (place == null) androidx.compose.runtime.LaunchedEffect(miss) {
        kotlinx.coroutines.delay(8_000)
        PhoneShare.notFound.value = null
    }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.size(1024.dp, 600.dp),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(top = 70.dp).width(560.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Color(0xF2111634))
                .border(1.dp, CardBorder, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                "Destino desde el celular",
                color = Muted, fontSize = 15.sp
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(6.dp))
            androidx.compose.material3.Text(
                place?.let { "¿Ir a ${it.name}?" } ?: miss.orEmpty(),
                color = TextMain, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                if (place != null) {
                    PillButton("Ir", Accent, OnAccent, androidx.compose.ui.Modifier.width(180.dp)) {
                        PhoneShare.offer.value = null
                        onGo(place)
                    }
                    PillButton("Ahora no", Track, TextMain, androidx.compose.ui.Modifier.width(150.dp)) { PhoneShare.offer.value = null }
                } else {
                    PillButton("Cerrar", Track, TextMain, androidx.compose.ui.Modifier.width(150.dp)) { PhoneShare.notFound.value = null }
                }
            }
        }
    }
}
