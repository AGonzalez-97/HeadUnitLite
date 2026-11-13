package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import java.net.URLEncoder
import kotlin.concurrent.thread

/** Una emisora: nombre, enlace de transmisión y detalle (frecuencia, formato). */
/**
 * Una emisora: nombre, enlace de transmisión, detalle (frecuencia, formato) y, si se conoce,
 * el enlace de su logo (el ícono que la propia emisora publica en el directorio Radio Browser).
 */
class Station(
    val name: String,
    val url: String,
    val detail: String,
    val logo: String? = null,
    /** Página web de la emisora (de ahí también se puede sacar su ícono). */
    val homepage: String? = null,
    /** Etiquetas del directorio ("news,talk", "pop", "religious"...), para la categoría. */
    val tags: String = "",
    /** Segundo logo para probar si el primero no carga (el del directorio, cuando el dial ya trae uno). */
    val altLogo: String? = null
) {
    /** Frecuencia FM sacada del nombre o del detalle (ej. "95.1"), o null si no la tiene. */
    val freq: Float? by lazy {
        Regex("""(\d{2,3})[.,](\d)""").findAll("$name $detail")
            .mapNotNull { "${it.groupValues[1]}.${it.groupValues[2]}".toFloatOrNull() }
            .firstOrNull { it in 87.5f..108f }
    }
}

/** Estado compartido entre el servicio de radio y la pantalla. */
object RadioState {
    val current = mutableStateOf<String?>(null)
    val playing = mutableStateOf(false)
    val buffering = mutableStateOf(false)
    /** Última emisora sin transmisión online (para mostrarlo en el display). */
    val offline = mutableStateOf<String?>(null)
    /** Aviso extra para el display, por ejemplo "SEÑAL NACIONAL". */
    val note = mutableStateOf<String?>(null)
    /** Interruptor LOCAL / NACIONAL (false = local, el modo por defecto). */
    val national = mutableStateOf(false)
    /** La emisora elegida (para mostrar su logo y frecuencia en la pantalla de inicio). */
    val station = mutableStateOf<Station?>(null)
    /** Por qué se pausó sola (otra app tomó el audio, se desconectó el audio). Null si no aplica. */
    val pauseReason = mutableStateOf<String?>(null)
}

/** Cuántas veces se reintenta la misma señal cuando se corta, y cuánto se espera antes de cada intento. */
const val RADIO_RECONNECTS = 4
fun reconnectDelayMs(attempt: Int): Long = when (attempt) {
    1 -> 1_000L
    2 -> 3_000L
    3 -> 6_000L
    else -> 10_000L
}

object RadioDirectory {
    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("\\b(radio|fm|lr[a-z]\\d+|tucuman|san miguel)\\b"), "")
        .replace(Regex("[^a-z0-9]"), "")

    /**
     * Junta el dial de Tucumán con las emisoras de Radio Browser: si una coincide por frecuencia
     * o por nombre, el dial toma su enlace; las que no tienen frecuencia quedan como "WEB".
     */
    fun merge(dial: List<Station>, extra: List<Station>): List<Station> {
        val used = HashSet<Station>()
        val out = dial.map { d ->
            val m = extra.firstOrNull { e ->
                // Por frecuencia solo si las dos la tienen (antes d.freq!! cerraba la app, v0.38.1)
                (e.freq != null && d.freq != null && kotlin.math.abs(e.freq!! - d.freq!!) < 0.06f) ||
                    (norm(e.name).isNotEmpty() && (norm(e.name).contains(norm(d.name)) || norm(d.name).contains(norm(e.name))) && norm(d.name).length >= 3)
            } ?: return@map d
            used.add(m)
            // Si el dial ya tiene su enlace, del directorio solo se toma el logo
            Station(
                d.name, d.url.ifEmpty { m.url }, d.detail, d.logo ?: m.logo, d.homepage ?: m.homepage,
                d.tags.ifEmpty { m.tags }, altLogo = if (d.logo != null) m.logo else null
            )
        }
        return out + extra.filter { it !in used && it.freq == null }
    }

    // Radio Browser: directorio abierto y comunitario de radios por internet
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    private fun query(path: String): JSONArray? {
        for (s in servers) {
            val body = Net.get(s + path, timeoutMs = 7000) ?: continue // probar el siguiente servidor
            try {
                return JSONArray(body)
            } catch (e: Exception) {
                AppLog.warn("Radio Browser: respuesta inesperada de ${Net.host(s)}", e)
            }
        }
        return null
    }

    private fun toStations(arr: JSONArray?): List<Station> {
        if (arr == null) return emptyList()
        val out = mutableListOf<Station>()
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
            if (name.isEmpty() || url.isEmpty() || !seen.add(name.lowercase())) continue
            val codec = o.optString("codec")
            val kbps = o.optInt("bitrate", 0)
            val detail = listOf(
                o.optString("state").ifEmpty { "Argentina" },
                listOf(codec, if (kbps > 0) "$kbps kbps" else "").filter { it.isNotEmpty() }.joinToString(" ")
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            fun link(key: String) = o.optString(key).trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            out.add(Station(name, url, detail, link("favicon"), link("homepage"), o.optString("tags").lowercase()))
        }
        return out
    }

    /** Radios de Tucumán. Llamar fuera del hilo principal. */
    fun tucuman(): List<Station> = toStations(
        query("/json/stations/search?state=Tucum&countrycode=AR&hidebroken=true&order=clickcount&reverse=true&limit=100")
    )

    /**
     * Enlace de la señal de Tucumán de una radio, buscado por nombre. A diferencia de [findUrl],
     * no devuelve señales de afuera: sirve para saber si la radio local funciona.
     * Llamar fuera del hilo principal.
     */
    fun findLocalUrl(name: String): String? {
        val clean = name.replace(Regex("""\bLR[A-Z]\d+\b"""), "").replace("(", "").replace(")", "")
            .replace(Regex("""\d{2,3}[.,]\d"""), " ").replace(Regex("(?i)tucum[aá]n"), " ").trim()
        if (clean.length < 2) return null
        val q = URLEncoder.encode(clean, "UTF-8")
        return toStations(query("/json/stations/search?name=$q&countrycode=AR&hidebroken=true&order=clickcount&reverse=true&limit=15"))
            .firstOrNull { "tucum" in (it.name + " " + it.detail).lowercase() && sameStation(it.name, name) }?.url
    }

    /** Busca el enlace vigente de una radio por nombre. Llamar fuera del hilo principal. */
    fun findUrl(name: String, preferLocal: Boolean = true): String? {
        val clean = name.replace(Regex("""\bLR[A-Z]\d+\b"""), "").replace("(", "").replace(")", "").trim()
        val q = URLEncoder.encode(clean, "UTF-8")
        val found = toStations(
            query("/json/stations/search?name=$q&countrycode=AR&hidebroken=true&order=clickcount&reverse=true&limit=15")
        )
        fun local(s: Station) = "tucum" in (s.name + " " + s.detail).lowercase()
        // Primero las entradas con el mismo nombre (evita tomar otra radio parecida)
        val similar = found.filter { sameStation(it.name, name) }.ifEmpty { found }
        // Modo LOCAL: prioridad absoluta a Tucumán. Modo NACIONAL: la señal que no es de Tucumán.
        val pick = if (preferLocal) similar.firstOrNull { local(it) } else similar.firstOrNull { !local(it) }
        return (pick ?: similar.firstOrNull())?.url
    }

    /** Cadenas nacionales: su logo sirve aunque la entrada del directorio no sea de Tucumán. */
    private val chains = listOf(
        "la100", "mitre", "los40", "nacional", "continental", "aspen", "rivadavia", "radio10",
        "vale", "cadena3", "rockpop", "rockandpop", "maria", "delplata", "mega", "mia"
    )

    private fun plain(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")

    fun isChain(name: String): Boolean = plain(name).let { p -> chains.any { it in p } }

    /** true si dos nombres se refieren a la misma emisora (uno contiene al otro, sin "radio", "fm", etc.). */
    fun sameStation(a: String, b: String): Boolean {
        val na = norm(a)
        val nb = norm(b)
        return na.length >= 3 && nb.length >= 3 && (na.contains(nb) || nb.contains(na))
    }

    /**
     * Busca en el directorio una entrada de la emisora que tenga logo o página web, para radios
     * del dial que no lo trajeron. Prefiere la de Tucumán; una de afuera solo se acepta si es una
     * cadena nacional (así no aparece el logo de otra radio con el mismo nombre).
     * Llamar fuera del hilo principal.
     */
    fun findLogoSource(name: String): Station? {
        val clean = name.replace(Regex("""\d{2,3}[.,]\d"""), " ")
            .replace(Regex("(?i)tucum[aá]n"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.length < 2) return null
        val q = URLEncoder.encode(clean, "UTF-8")
        val found = toStations(query("/json/stations/search?name=$q&countrycode=AR&order=clickcount&reverse=true&limit=25"))
            .filter { (it.logo != null || it.homepage != null) && sameStation(it.name, name) }
        fun local(s: Station) = "tucum" in (s.name + " " + s.detail).lowercase()
        return found.firstOrNull { local(it) } ?: if (isChain(name)) found.firstOrNull() else null
    }

    /** Dominios de reproductores oficiales cuyas páginas se pueden leer. */
    private val trustedPages = listOf("cienradios.com", "cienradios.com.ar")

    /**
     * Dominios de audio aceptados desde esas páginas. Cienradios (La 100, Mitre) transmite
     * por StreamTheWorld (Triton Digital) desde 2026; los enlaces viejos de cienradios.com.ar
     * quedaron como alternativa.
     */
    private val trustedStreams = trustedPages + "streamtheworld.com"

    private fun hostIn(url: String, hosts: List<String>): Boolean {
        val h = Net.host(url)
        return h.isNotEmpty() && hosts.any { h == it || h.endsWith(".$it") }
    }

    /** true si la página es de un reproductor oficial de confianza. */
    fun isTrusted(url: String): Boolean = hostIn(url, trustedPages)

    /** true si el enlace de audio es de un dominio aceptado. */
    fun isTrustedStream(url: String): Boolean = hostIn(url, trustedStreams)

    /** Enlace de StreamTheWorld para un "mount" (identificador de la señal, ej. FM999_56). */
    fun tritonUrl(mount: String) = "https://playerservices.streamtheworld.com/api/livestream-redirect/$mount"

    /**
     * Saca de una página el enlace de audio (prefiere uno que diga "tucuman" o "tuc").
     * Acepta enlaces directos (.aac, .mp3, .m3u8, .ogg), enlaces de StreamTheWorld sin extensión
     * y "mounts" de StreamTheWorld declarados en la configuración del reproductor.
     * Solo de dominios de confianza.
     */
    fun extractStream(html: String): String? = pickStream(streamLinks(html))

    /** Todos los enlaces de audio de una página, sin filtrar (para elegir y para el diagnóstico). */
    fun streamLinks(html: String): List<String> {
        val text = html.replace("\\/", "/")
        val direct = Regex("""https?://[^"'\s<>]+?\.(?:aac|mp3|m3u8|ogg)(?:\?[^"'\s<>]*)?""")
            .findAll(text).map { it.value }
        val triton = Regex("""https?://[a-z0-9.-]*streamtheworld\.com/[^"'\s<>]+""", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.value }
        // "mount" de StreamTheWorld en JSON o JavaScript: "mount":"X", mount: 'X', tritonMount = "X"
        val mounts = Regex("""(?<![A-Za-z])(?:[a-z]+M|m)ount(?:Name|Point|point|_name)?['"]?\s*[:=]\s*['"]([A-Z][A-Z0-9_]{3,})['"]""")
            .findAll(text).map { tritonUrl(it.groupValues[1]) }
        return (direct + triton + mounts).distinct().toList()
    }

    /** Elige la señal de Tucumán (dice "tucuman", "tuc" o la frecuencia 955) entre los enlaces aceptados. */
    fun pickStream(links: List<String>): String? {
        val urls = links.filter { isTrustedStream(it) }
        fun local(u: String) = u.lowercase().let {
            "tucuman" in it || "955" in it || Regex("""(?<![a-z])tuc""").containsMatchIn(it)
        }
        return urls.firstOrNull { local(it) } ?: urls.firstOrNull()
    }

    /** Qué pasó la última vez que se leyó la página de un reproductor (se ve en Diagnóstico). */
    @Volatile
    var lastScrape: String? = null

    /**
     * Abre la página del reproductor oficial de una radio y saca el enlace de audio.
     * Solo se aceptan páginas de los dominios de confianza. Llamar fuera del hilo principal.
     */
    fun scrapeStream(page: String): String? {
        if (!isTrusted(page)) {
            AppLog.warn("Página de radio no permitida: ${Net.host(page)}")
            return null
        }
        val html = Net.get(
            page, userAgent = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        )
        if (html == null) {
            lastScrape = "${Net.host(page)}: la página no respondió"
            return null
        }
        val links = streamLinks(html)
        val chosen = pickStream(links)
        val hosts = links.map { Net.host(it) }.distinct().joinToString(", ").ifEmpty { "ninguno" }
        lastScrape = "${Net.host(page)}: ${links.size} enlaces ($hosts) → " +
            (chosen?.let { it.substringAfter("://").take(60) } ?: "ninguno aceptado")
        if (chosen == null) AppLog.warn("Sin enlace de audio en ${Net.host(page)} (${links.size} encontrados)")
        return chosen
    }
}

/** Si el enlace es una lista (.m3u / .pls), devuelve el primer enlace de audio que contiene. */
fun resolvePlaylist(url: String): String {
    val lower = url.lowercase().substringBefore("?")
    if (!(lower.endsWith(".m3u") || lower.endsWith(".pls"))) return url
    val body = Net.get(url, timeoutMs = 7000) ?: return url
    return firstStreamInPlaylist(body) ?: url
}

/** Primer enlace http(s) de una lista .m3u o .pls (en .pls, las líneas son "File1=http..."). */
fun firstStreamInPlaylist(body: String): String? =
    body.lines().map { it.trim() }.filter { !it.startsWith("#") }
        .map { if (it.startsWith("http")) it else it.substringAfter("=", it) }
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

/**
 * Orden en que se prueban los enlaces de una emisora. `url` puede traer varios separados por "|".
 * LOCAL: primero los locales y la señal nacional ("nac:") al final. NACIONAL: al revés.
 * Siempre termina con "lookup:" (buscar por nombre en el directorio).
 */
fun radioCandidates(url: String, national: Boolean): List<String> {
    val saved = url.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val (nac, loc) = saved.partition { it.startsWith("nac:") }
    return (if (national) nac + loc else loc + nac) + "lookup:"
}

/**
 * Radios favoritas, guardadas en el equipo. La primera vez arranca con La 100.
 * El estado es observable: la pantalla se actualiza al marcar o desmarcar.
 */
object RadioFavorites {
    private const val KEY = "favoritos"
    val names = mutableStateOf<Set<String>>(emptySet())

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
        names.value = if (prefs.contains(KEY)) prefs.getStringSet(KEY, emptySet()).orEmpty().toSet() else setOf("La 100")
    }

    fun toggle(context: Context, name: String) {
        names.value = toggled(names.value, name)
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, HashSet(names.value)).apply()
    }

    /** Agrega o quita una radio (función pura, para los tests). */
    fun toggled(current: Set<String>, name: String): Set<String> = if (name in current) current - name else current + name
}

/** Aviso del display cuando suena por la antena del auto (y marca de ese modo en `RadioState.note`). */
const val ANTENNA_NOTE = "ANTENA"

/** Si suena el FM por la antena (el estado de la radio es del sintonizador, no del reproductor de internet). */
fun antennaActive(): Boolean = RadioState.note.value == ANTENNA_NOTE

/** Atajos para manejar la radio desde la pantalla. */
object Radio {
    private var last: Station? = null

    /** Si suena el FM por la antena (v0.51.0): las órdenes van al sintonizador de la radio NWD. */
    fun onAntenna(): Boolean = NwdTuner.antenna.value && NwdTuner.available.value

    fun play(context: Context, s: Station) {
        last = s
        RadioState.station.value = s
        val mhz = s.freq
        if (onAntenna() && mhz != null) {
            // FM real: corta la radio por internet y sintoniza la frecuencia en la radio del auto
            if (RadioState.current.value != null && RadioState.note.value != ANTENNA_NOTE) stop(context)
            NwdTuner.tune(context, mhz)
            RadioState.current.value = s.name
            RadioState.playing.value = true
            RadioState.buffering.value = false
            RadioState.offline.value = null
            RadioState.pauseReason.value = null
            RadioState.note.value = ANTENNA_NOTE
            return
        }
        if (RadioState.note.value == ANTENNA_NOTE) NwdTuner.release(context)
        context.startService(
            Intent(context, RadioService::class.java)
                .setAction(RadioService.ACTION_PLAY)
                .putExtra(RadioService.EXTRA_TOKEN, RadioService.TOKEN)
                .putExtra(RadioService.EXTRA_NAME, s.name)
                .putExtra(RadioService.EXTRA_URL, s.url)
                .putExtra(RadioService.EXTRA_NATIONAL, RadioState.national.value)
        )
    }

    /** Lee el modo guardado (LOCAL / NACIONAL). */
    fun loadMode(context: Context) {
        RadioState.national.value = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
            .getBoolean("nacional", false)
    }

    /** Cambia el modo, lo guarda y, si hay una radio sonando, la vuelve a sintonizar. */
    fun setNational(context: Context, national: Boolean) {
        RadioState.national.value = national
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit().putBoolean("nacional", national).apply()
        val s = last
        if (s != null && RadioState.current.value == s.name) play(context, s)
    }

    fun stop(context: Context) {
        if (RadioState.note.value == ANTENNA_NOTE) {
            NwdTuner.release(context)
            RadioState.playing.value = false
            RadioState.current.value = null
            RadioState.note.value = null
            return
        }
        context.startService(Intent(context, RadioService::class.java).setAction(RadioService.ACTION_STOP)
            .putExtra(RadioService.EXTRA_TOKEN, RadioService.TOKEN))
    }

    /** Pausa o sigue la radio que está sintonizada. */
    fun toggle(context: Context) {
        if (RadioState.note.value == ANTENNA_NOTE) {
            if (RadioState.playing.value) {
                NwdTuner.release(context)
                RadioState.playing.value = false
            } else {
                NwdTuner.select(context)
                RadioState.playing.value = true
            }
            return
        }
        context.startService(Intent(context, RadioService::class.java).setAction(RadioService.ACTION_TOGGLE)
            .putExtra(RadioService.EXTRA_TOKEN, RadioService.TOKEN))
    }

    /**
     * Pasa a la emisora siguiente o anterior del dial (entre las favoritas, si hay; si no,
     * entre las que funcionan). Para los botones ⏮ ⏭ de la pantalla de inicio y del tablero.
     */
    fun step(context: Context, up: Boolean) {
        // Por la antena andan todas las del dial; por internet, solo las que funcionan
        val all = if (onAntenna()) TucumanFm.stations else RadioCatalog.visible(RadioFavorites.names.value)
        val dial = all.filter { it.freq != null }.sortedBy { it.freq }
        val favs = dial.filter { it.name in RadioFavorites.names.value }
        val list = if (favs.size >= 2) favs else dial
        if (list.isEmpty()) return
        val f = RadioState.station.value?.freq ?: return play(context, list.first())
        val next = if (up) list.firstOrNull { it.freq!! > f + 0.05f } ?: list.first()
        else list.lastOrNull { it.freq!! < f - 0.05f } ?: list.last()
        play(context, next)
    }
}

/**
 * Reproduce la radio en segundo plano: sigue sonando aunque abras Maps o Waze.
 * Publica una sesión de medios, así la tarjeta de música y los controles del volante la ven.
 */
class RadioService : MediaSessionService() {
    companion object {
        const val ACTION_PLAY = "com.axel.onixlauncher.radio.PLAY"
        const val ACTION_STOP = "com.axel.onixlauncher.radio.STOP"
        const val ACTION_TOGGLE = "com.axel.onixlauncher.radio.TOGGLE"
        const val EXTRA_NAME = "name"
        const val EXTRA_URL = "url"
        const val EXTRA_NATIONAL = "nacional"
        const val EXTRA_TOKEN = "clave"

        /**
         * Clave al azar de este proceso (v0.38.1). El servicio está exportado porque los controles
         * del sistema (MediaSession) lo necesitan, pero las órdenes propias (reproducir un enlace,
         * pausar, parar) solo se aceptan si traen esta clave: otra app no la conoce.
         */
        val TOKEN: String = java.util.UUID.randomUUID().toString()

        /** Si una orden se acepta: las que no son propias pasan a Media3; las propias, solo con la clave. */
        fun accepts(action: String?, token: String?, expected: String = TOKEN): Boolean =
            action !in setOf(ACTION_PLAY, ACTION_STOP, ACTION_TOGGLE) || token == expected
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var currentName = ""
    private var currentUrl = ""
    private var candidates: List<String> = emptyList()
    private var idx = 0
    private var national = false
    /** Sube con cada orden nueva: las respuestas de pedidos viejos se descartan (AUD-007). */
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    /** La señal actual ya sonó: si se corta, se reconecta a la misma en vez de pasar a otra. */
    private var playedOk = false
    private var reconnects = 0
    private var playingSince = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        Tls.install()
        super.onCreate()
        // Las radios redirigen entre https y http (StreamTheWorld, por ejemplo); ExoPlayer no
        // sigue esos saltos salvo que se lo permita. Sin esto, La 100 y Mitre no suenan.
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("OnixLauncher/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Mantiene la CPU y el Wi-Fi activos mientras suena (con la pantalla apagada se cortaba)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // AUD-048: si ya suena el FM por la antena, el reproductor de internet que se apaga no pisa su estado
                if (antennaActive()) return
                RadioState.playing.value = isPlaying
                if (isPlaying) {
                    playedOk = true
                    playingSince = SystemClock.elapsedRealtime()
                    RadioState.pauseReason.value = null
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (antennaActive()) return
                RadioState.buffering.value = state == Player.STATE_BUFFERING
                // Una radio en vivo no "termina": si el servidor cerró la conexión, reconectar
                if (state == Player.STATE_ENDED && currentUrl.isNotEmpty()) {
                    reconnectOrNext("la transmisión se cortó")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) return
                val why = when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "OTRA APP"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO DESCONECTADO"
                    else -> null // pausa pedida por el usuario (pantalla, notificación, volante)
                }
                RadioState.pauseReason.value = why
                if (why != null) AppLog.warn("Radio en pausa sola: ${if (why == "OTRA APP") "otra app tomó el audio" else "se desconectó la salida de audio (Bluetooth o auriculares)"}")
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.warn("Radio: falló $currentName en ${Net.host(currentUrl)} (${error.errorCodeName})")
                // Si ya sonaba, es un corte: reintentar la misma señal. Si nunca sonó, probar la siguiente.
                if (playedOk) reconnectOrNext(error.errorCodeName) else tryNext(currentName, generation)
            }
        })
        session = MediaSession.Builder(this, player).build().also {
            // Sin esto, el servicio no se registra la sesión hasta que se conecta un control
            // externo: no aparecía la notificación ni se mantenía en segundo plano.
            addSession(it)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!accepts(intent?.action, intent?.getStringExtra(EXTRA_TOKEN))) {
            AppLog.warn("Radio: orden rechazada (no viene de la app)")
            return super.onStartCommand(null, flags, startId)
        }
        when (intent?.action) {
            ACTION_PLAY -> {
                RadioState.offline.value = null
                RadioState.note.value = null
                val name = intent.getStringExtra(EXTRA_NAME) ?: ""
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                national = intent.getBooleanExtra(EXTRA_NATIONAL, false)
                candidates = radioCandidates(url, national)
                idx = 0
                playedOk = false
                reconnects = 0
                RadioState.pauseReason.value = null
                tryNext(name, ++generation)
            }
            ACTION_TOGGLE -> {
                if (player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) {
                    player.pause()
                } else if (currentUrl.isNotEmpty()) {
                    RadioState.pauseReason.value = null
                    // En vivo: al seguir, volver al momento actual y no a donde quedó
                    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                        startDirect(currentName, currentUrl)
                    } else {
                        player.seekToDefaultPosition()
                        player.play()
                    }
                }
            }
            ACTION_STOP -> {
                generation++
                currentUrl = ""
                RadioState.pauseReason.value = null
                player.stop()
                player.clearMediaItems()
                // AUD-048: al pasar a la antena, el corte de internet llega después: no borra la emisora del FM
                if (!antennaActive()) {
                    RadioState.current.value = null
                    RadioState.note.value = null
                }
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Prueba el siguiente enlace candidato. Prefijos: "page:" = página del reproductor oficial,
     * "nac:" = señal nacional (se avisa en el display), "lookup:" = buscar por nombre.
     */
    private fun tryNext(name: String, gen: Int) {
        if (gen != generation) return
        if (idx >= candidates.size) {
            fail(name, sinOnline = true)
            return
        }
        val c = candidates[idx++]
        AppLog.info("Radio: $name, enlace ${idx}/${candidates.size}: ${c.take(90)}")
        currentName = name
        RadioState.current.value = name
        RadioState.buffering.value = true
        thread {
            val nacLink = c.startsWith("nac:")
            val raw = c.removePrefix("nac:")
            // "lookup:" respeta el interruptor: en NACIONAL busca primero la señal de afuera
            val nationalSignal = nacLink || (raw == "lookup:" && national)
            val real: String? = when {
                raw == "lookup:" -> RadioDirectory.findUrl(name, preferLocal = !nationalSignal)
                raw.startsWith("page:") -> RadioDirectory.scrapeStream(raw.removePrefix("page:"))
                else -> resolvePlaylist(raw)
            }
            main.post {
                // Mientras se resolvía, el usuario eligió otra radio o apagó: no hacer nada
                if (gen != generation) return@post
                if (real.isNullOrBlank()) {
                    tryNext(name, gen)
                } else {
                    RadioState.note.value = if (nationalSignal) "SEÑAL NACIONAL" else null
                    startDirect(name, real)
                }
            }
        }
    }

    /**
     * La señal que sonaba se cortó (red, servidor). Se reintenta la misma hasta [RADIO_RECONNECTS]
     * veces con esperas crecientes; si sigue sin andar, se pasa al siguiente enlace.
     * El contador vuelve a cero después de un minuto sonando bien.
     */
    private fun reconnectOrNext(why: String) {
        if (SystemClock.elapsedRealtime() - playingSince > 60_000L) reconnects = 0
        val gen = generation
        if (reconnects < RADIO_RECONNECTS && currentUrl.isNotEmpty()) {
            reconnects++
            AppLog.warn("Radio: $why en $currentName; reconectando (intento $reconnects)")
            RadioState.buffering.value = true
            val name = currentName
            val url = currentUrl
            main.postDelayed({ if (gen == generation) startDirect(name, url) }, reconnectDelayMs(reconnects))
        } else {
            playedOk = false
            reconnects = 0
            tryNext(currentName, gen)
        }
    }

    private fun startDirect(name: String, url: String) {
        AppLog.info("Radio: reproduciendo $name desde ${url.take(90)}")
        currentName = name
        currentUrl = url
        RadioState.current.value = name
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setArtist("Radio en vivo").build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private fun fail(name: String, sinOnline: Boolean = false) {
        AppLog.warn("Radio: $name sin enlace que funcione")
        // v0.81.21: si fue por la red, se vigila hasta que vuelva y ahí se retoma la emisora
        WifiRescue.suspect()
        RadioState.current.value = null
        RadioState.playing.value = false
        RadioState.buffering.value = false
        RadioState.offline.value = name
        Toast.makeText(
            this,
            if (sinOnline) "$name no tiene transmisión online" else "No se pudo conectar con $name",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        // AUD-100 (v0.79.6): si el servicio muere sin pasar por STOP (lo para el sistema o Media3), una
        // reconexión o una búsqueda pendiente no tiene que arrancar el reproductor ya liberado
        generation++
        main.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        player.release()
        if (!antennaActive()) RadioState.playing.value = false
        super.onDestroy()
    }
}
