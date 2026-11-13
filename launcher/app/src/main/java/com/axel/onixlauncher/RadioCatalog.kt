package com.axel.onixlauncher

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Categorías para ordenar las radios. */
enum class RadioCategory(val label: String) {
    NOTICIAS("Noticias"),
    DEPORTES("Deportes"),
    MUSICA("Música"),
    RELIGIOSA("Religiosa"),
    CULTURA("Cultura"),
    OTRAS("Otras")
}

/**
 * Categoría de cada radio del dial que se conoce con certeza (cadenas nacionales y emisoras
 * con un perfil claro). El resto sale de las etiquetas del directorio o queda en "Otras".
 */
private val KnownCategories: Map<String, RadioCategory> = mapOf(
    "Radio Mitre Tucumán" to RadioCategory.NOTICIAS,
    "Radio Nacional Tucumán" to RadioCategory.NOTICIAS,
    "LV7 Radio Tucumán" to RadioCategory.NOTICIAS,
    "LV12" to RadioCategory.NOTICIAS,
    "Continental Tucumán" to RadioCategory.NOTICIAS,
    "Radio Rivadavia" to RadioCategory.NOTICIAS,
    "Radio 10" to RadioCategory.NOTICIAS,
    "Cadena 3" to RadioCategory.NOTICIAS,
    "Radio Del Plata" to RadioCategory.NOTICIAS,
    "Radio Sport FM" to RadioCategory.DEPORTES,
    "La 100" to RadioCategory.MUSICA,
    "Los 40 Tucumán" to RadioCategory.MUSICA,
    "Aspen Tucumán" to RadioCategory.MUSICA,
    "Rock & Pop Tucumán" to RadioCategory.MUSICA,
    "Vale Radio" to RadioCategory.MUSICA,
    "Radio Mega" to RadioCategory.MUSICA,
    "Mía 101.1" to RadioCategory.MUSICA,
    "FM Romántica" to RadioCategory.MUSICA,
    "Bésame FM" to RadioCategory.MUSICA,
    "Radio Latina Tropical" to RadioCategory.MUSICA,
    "Radio Uno La Folk" to RadioCategory.MUSICA,
    "Radio María" to RadioCategory.RELIGIOSA,
    "Radio Nuevo Tiempo" to RadioCategory.RELIGIOSA,
    "Radio Salvación" to RadioCategory.RELIGIOSA,
    "Radio Universidad" to RadioCategory.CULTURA
)

/** Categoría de una radio: la conocida, o la que indican sus etiquetas del directorio. */
fun categoryOf(s: Station): RadioCategory {
    KnownCategories[s.name]?.let { return it }
    val t = s.tags
    fun any(vararg words: String) = words.any { it in t }
    return when {
        any("religi", "christian", "cristian", "catolic", "catholic", "gospel", "evangel", "iglesia", "biblia") -> RadioCategory.RELIGIOSA
        any("sport", "deporte", "futbol", "fútbol") -> RadioCategory.DEPORTES
        any("news", "noticia", "talk", "informa", "actualidad", "politic") -> RadioCategory.NOTICIAS
        any("universi", "cultur", "educa", "communit", "comunit") -> RadioCategory.CULTURA
        any("music", "música", "musica", "pop", "rock", "cumbia", "folk", "tropical", "latin", "hits", "romant",
            "dance", "electr", "reggaeton", "cuarteto", "jazz", "classic", "clásic", "oldies", "retro") -> RadioCategory.MUSICA
        else -> RadioCategory.OTRAS
    }
}

/**
 * Catálogo de radios de la app: el dial de Tucumán más las del directorio, y cuáles funcionan.
 *
 * "Funciona" quiere decir que la señal de Tucumán (no la nacional) respondió con audio en la
 * última revisión. Las que no funcionan se ocultan, salvo las favoritas. La revisión se hace
 * en el equipo, se guarda y se repite cada 3 días o cuando el usuario toca "Revisar".
 */
object RadioCatalog {
    private const val RECHECK_MS = 3L * 24 * 60 * 60 * 1000

    val stations = mutableStateOf(TucumanFm.stations)
    val loading = mutableStateOf(false)
    /** Radios que no respondieron en la última revisión. */
    val broken = mutableStateOf<Set<String>>(emptySet())
    /** Radios revisadas que sí funcionan. */
    val working = mutableStateOf<Set<String>>(emptySet())
    /** Progreso de la revisión en curso (hechas, total), o null si no se está revisando. */
    val progress = mutableStateOf<Pair<Int, Int>?>(null)
    val checkedAt = mutableStateOf(0L)
    /** Detalle de la última revisión: una línea por radio, con el motivo si no funciona. */
    val checkLog = mutableStateOf<List<String>>(emptyList())
    /** Radio que se está revisando ahora (para seguir la revisión en vivo). */
    val checkingNow = mutableStateOf<String?>(null)
    private var loaded = false
    /** La revisión sigue aunque se cierre la pantalla de radio. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

    fun startCheck(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        scope.launch { checkIfNeeded(app, force) }
    }

    /** Las que se muestran: todas menos las que no funcionan (las favoritas, siempre). */
    fun visible(favorites: Set<String>): List<Station> =
        stations.value.filter { it.name !in broken.value || it.name in favorites || it.name in PINNED }

    /** Radios que nunca se ocultan, aunque la revisión diga que no responden. */
    val PINNED = setOf("La 100")

    private var savedLoaded = false

    /** Lee el resultado guardado de la última revisión (también lo usa Diagnóstico). */
    fun loadSaved(context: Context) {
        if (savedLoaded || progress.value != null) return
        savedLoaded = true
        val prefs = context.getSharedPreferences("radio_salud", Context.MODE_PRIVATE)
        broken.value = prefs.getStringSet("mal", emptySet()).orEmpty().toSet()
        working.value = prefs.getStringSet("bien", emptySet()).orEmpty().toSet()
        checkedAt.value = prefs.getLong("fecha", 0L)
        checkLog.value = prefs.getString("detalle", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    }

    /** Carga el directorio (una vez por sesión) y el resultado de la última revisión. */
    suspend fun load(context: Context) {
        if (loaded || loading.value) return
        loadSaved(context)
        loading.value = true
        try {
            val directory = withContext(Dispatchers.IO) { RadioDirectory.tucuman() }
            stations.value = RadioDirectory.merge(TucumanFm.stations, directory)
            // v0.81.22: antes quedaba cargado aunque el directorio no hubiera contestado, y si la
            // pantalla se cerraba en el medio el cartel "buscando emisoras" no se iba más
            loaded = directory.isNotEmpty()
        } finally {
            loading.value = false
        }
    }

    /** La red volvió: si el directorio no se había podido leer, se pide de nuevo. */
    fun retry(context: Context) {
        if (loaded) return
        val app = context.applicationContext
        scope.launch { load(app) }
    }

    /**
     * Si el resultado de una revisión es de fiar. Con la red caída o con menos de la mitad de las que
     * funcionaban, las que no contestaron no se dan por caídas (quedaban ocultas 3 días). Pura.
     */
    fun shouldSaveCheck(ok: Int, previousOk: Int, netDown: Boolean): Boolean =
        ok > 0 && !netDown && (previousOk == 0 || ok * 2 >= previousOk)

    /** Revisa todas las radios si nunca se hizo, si pasaron 3 días o si [force]. */
    suspend fun checkIfNeeded(context: Context, force: Boolean = false) {
        if (progress.value != null) return
        if (!force && System.currentTimeMillis() - checkedAt.value < RECHECK_MS) return
        val list = stations.value
        progress.value = 0 to list.size
        val ok = HashSet<String>()
        val bad = HashSet<String>()
        val detail = mutableListOf<String>()
        checkLog.value = emptyList()
        AppLog.info("Revisión de radios: empieza (${list.size} radios)")
        val gate = Semaphore(4)
        withContext(Dispatchers.IO) {
            coroutineScope {
                list.map { s ->
                    async {
                        gate.withPermit {
                            checkingNow.value = s.name
                            val (alive, why) = try {
                                checkStation(s)
                            } catch (e: Exception) {
                                false to "error ${e.javaClass.simpleName}"
                            }
                            synchronized(ok) {
                                if (alive) ok.add(s.name) else bad.add(s.name)
                                detail += "${if (alive) "OK " else "NO "} ${s.name} (${s.freq ?: "web"}) — $why"
                                checkLog.value = detail.toList()
                                progress.value = (ok.size + bad.size) to list.size
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        checkingNow.value = null
        val sorted = detail.sortedWith(compareBy({ !it.startsWith("OK") }, { it }))
        checkLog.value = sorted
        AppLog.info("Revisión de radios: ${ok.size} funcionan, ${bad.size} no")
        // Sin internet todo daría "no funciona": en ese caso no se guarda nada
        if (!shouldSaveCheck(ok.size, working.value.size, WifiRescue.down)) {
            AppLog.warn("Revisión de radios: resultado poco confiable (${ok.size} funcionan, antes ${working.value.size}; ¿sin internet?); no se guardó")
        } else {
            broken.value = bad
            working.value = ok
            checkedAt.value = System.currentTimeMillis()
            context.getSharedPreferences("radio_salud", Context.MODE_PRIVATE).edit()
                .putStringSet("mal", bad).putStringSet("bien", ok).putLong("fecha", checkedAt.value)
                .putString("detalle", sorted.joinToString("\n")).apply()
        }
        progress.value = null
    }

    /**
     * Revisa la señal de Tucumán de una radio. Devuelve si funciona y por qué (qué enlace anduvo,
     * o el motivo de cada enlace que falló). Fuera del hilo principal.
     */
    private fun checkStation(s: Station): Pair<Boolean, String> {
        val notes = mutableListOf<String>()
        for (c in radioCandidates(s.url, national = false)) {
            if (c.startsWith("nac:")) continue // la señal nacional no cuenta como local
            val real = when {
                c == "lookup:" -> RadioDirectory.findLocalUrl(s.name)
                    .also { if (it == null) notes += "directorio: sin señal de Tucumán" }
                c.startsWith("page:") -> RadioDirectory.scrapeStream(c.removePrefix("page:"))
                    .also { if (it == null) notes += "página: ${RadioDirectory.lastScrape ?: "sin enlace"}" }
                else -> resolvePlaylist(c)
            } ?: continue
            val (alive, how) = streamCheck(real)
            if (alive) return true to "${Net.host(real)}: $how"
            notes += "${Net.host(real)}: $how"
        }
        // Sin señal de Tucumán: las cadenas valen con la señal nacional (como La 100)
        if (s.url.contains("nac:") || RadioDirectory.isChain(s.name)) {
            val nac = radioCandidates(s.url, national = true).filter { it.startsWith("nac:") }.map { it.removePrefix("nac:") }
            val links = nac + listOfNotNull(RadioDirectory.findUrl(s.name, preferLocal = false))
            for (u in links.distinct()) {
                val real = resolvePlaylist(u)
                val (alive, how) = streamCheck(real)
                if (alive) return true to "${Net.host(real)}: $how (señal nacional)"
                notes += "nacional ${Net.host(real)}: $how"
            }
        }
        return false to notes.joinToString(" | ").ifEmpty { "sin enlaces" }
    }
}

/**
 * true si el enlace responde con audio: código 2xx y tipo de audio o lista HLS/M3U
 * (o, si el servidor no dice el tipo, que no sea una página web). Lee solo el comienzo.
 */
fun streamResponds(url: String): Boolean = streamCheck(url).first

/** Como [streamResponds], pero también dice qué respondió (para el detalle de la revisión). */
fun streamCheck(url: String): Pair<Boolean, String> {
    var target = try { URL(url) } catch (e: Exception) { return false to "enlace inválido" }
    repeat(5) {
        var conn: HttpURLConnection? = null
        try {
            conn = target.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "OnixLauncher/${BuildConfig.VERSION_NAME}")
            conn.setRequestProperty("Icy-MetaData", "0")
            val code = conn.responseCode
            if (code in 300..399) {
                target = URL(target, conn.getHeaderField("Location") ?: return false to "redirección sin destino")
                return@repeat
            }
            if (code !in 200..299) return false to "HTTP $code"
            val type = conn.contentType.orEmpty().lowercase()
            val first = ByteArray(512)
            val n = conn.inputStream.read(first)
            if (n <= 0) return false to "HTTP $code sin datos"
            val audio = looksLikeAudio(type, String(first, 0, n, Charsets.ISO_8859_1))
            return audio to (if (audio) "audio ${type.ifEmpty { "sin tipo" }}" else "no es audio (${type.ifEmpty { "sin tipo" }})")
        } catch (e: Exception) {
            return false to "sin respuesta (${e.javaClass.simpleName})"
        } finally {
            conn?.disconnect()
        }
    }
    return false to "demasiadas redirecciones"
}

/** Decide por el tipo de contenido y los primeros bytes si una respuesta es audio (pura, para los tests). */
fun looksLikeAudio(contentType: String, head: String): Boolean = when {
    contentType.startsWith("audio/") -> true
    "mpegurl" in contentType || "ogg" in contentType || "aac" in contentType -> true
    contentType.startsWith("text/html") -> false
    head.trimStart().startsWith("#EXTM3U") -> true
    head.trimStart().startsWith("<") -> false
    else -> contentType.isEmpty() || "octet-stream" in contentType
}
