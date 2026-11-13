package com.axel.onixlauncher

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/** Etiqueta común de los logs (`adb logcat -s OnixLauncher`). */
const val LOG_TAG = "OnixLauncher"

/**
 * Registro de la app: va a logcat, guarda los últimos 80 eventos (para el informe que se copia
 * desde Diagnóstico) y el último error se muestra en Diagnóstico.
 */
object AppLog {
    @Volatile
    var lastError: String? = null
        private set

    private const val MAX = 80
    private val entries = ArrayDeque<String>()

    /**
     * v0.79.16: las líneas del encendido ("Contacto", "Al encender", "Red al encender") se guardan
     * también aparte: con la red caída, los errores repetidos llenaban los 80 eventos y el informe
     * sacado una hora después ya no mostraba el encendido (informe del 07/11, 22:30).
     */
    private const val MAX_KEY = 40
    private val keyEntries = ArrayDeque<String>()

    /** Si la línea es del encendido y va también al registro aparte. Pura. */
    fun isKeyEvent(msg: String): Boolean =
        msg.startsWith("Contacto") || msg.startsWith("Al encender") || msg.startsWith("Red al encender")

    /** Tapa claves de servicios en direcciones (key=…, token=…) antes de anotarlas (v0.75.0, TomTom). Pura. */
    fun redact(s: String): String = s.replace(Regex("(?i)\\b(key|token|access_token)=[^&\\s\"')]+"), "$1=***")

    private fun add(kind: String, raw: String) {
        val msg = redact(raw)
        val c = java.util.Calendar.getInstance()
        val t = String.format(java.util.Locale.US, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
        synchronized(entries) {
            entries.addLast("$t $kind $msg")
            while (entries.size > MAX) entries.removeFirst()
            if (isKeyEvent(msg)) {
                keyEntries.addLast("$t $kind $msg")
                while (keyEntries.size > MAX_KEY) keyEntries.removeFirst()
            }
        }
    }

    fun warn(what: String, e: Throwable? = null) {
        val msg = if (e != null) "$what (${e.javaClass.simpleName}: ${e.message?.take(80)})" else what
        lastError = redact(if (e != null) "$what (${e.javaClass.simpleName})" else what)
        add("!", msg)
        try {
            // Sin el error completo: su texto puede traer la dirección con la clave
            Log.w(LOG_TAG, redact(msg))
        } catch (_: RuntimeException) {
            // tests de JVM sin Android: alcanza con lastError
        }
    }

    /** Evento que no es un error (por ejemplo, qué enlace de radio se está probando). */
    fun info(what: String) {
        add("·", what)
        try {
            Log.i(LOG_TAG, redact(what))   // AUD-104 (v0.79.5): igual que warn, sin claves en logcat
        } catch (_: RuntimeException) {
        }
    }

    /** Los últimos eventos, del más viejo al más nuevo. */
    fun recent(): List<String> = synchronized(entries) { entries.toList() }

    /** Las líneas del encendido guardadas aparte, del más viejo al más nuevo (v0.79.16). */
    fun recentKey(): List<String> = synchronized(entries) { keyEntries.toList() }
}

/** Pedidos HTTP con cierre garantizado de la conexión y control del código de respuesta. */
object Net {
    private const val MAX_CHARS = 1_000_000

    /** Host de un enlace, para logs y validaciones. Vacío si el enlace no es válido. */
    fun host(url: String): String = try {
        URL(url).host.orEmpty().lowercase()
    } catch (_: Exception) {
        ""
    }

    /** GET de texto. Devuelve null si falla, si el código no es 2xx o si no hay red. Fuera del hilo principal. */
    fun get(url: String, timeoutMs: Int = 8000, userAgent: String = "OnixLauncher/${BuildConfig.VERSION_NAME}"): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "text/html,application/json,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "es-AR,es;q=0.9")
            val code = conn.responseCode
            WifiRescue.answered()
            if (code !in 200..299) {
                AppLog.warn("HTTP $code en ${host(url)}")
                return null
            }
            return conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < MAX_CHARS) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            AppLog.warn("Sin respuesta de ${host(url)}", e)
            WifiRescue.failed(e)
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pedido HTTP con método, cuerpo y encabezados (APIs con token, como Spotify). Devuelve el código
     * y el texto de la respuesta (también si es un error), o null si no hubo conexión. Nunca registra
     * los encabezados: pueden llevar tokens.
     */
    fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 10_000
    ): Pair<Int, String>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "OnixLauncher/${BuildConfig.VERSION_NAME}")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray()) }
            } else if (method == "PUT" || method == "POST") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
            }
            val code = conn.responseCode
            WifiRescue.answered()
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText().take(MAX_CHARS) }.orEmpty()
            code to text
        } catch (e: IllegalArgumentException) {
            // Pedido mal armado (por ejemplo un encabezado con saltos de línea): no es la red, y el texto del
            // error trae el valor del encabezado (puede ser un token), así que no se registra.
            AppLog.warn("Pedido inválido para ${host(url)} (${e.javaClass.simpleName})")
            null
        } catch (e: Exception) {
            AppLog.warn("Sin respuesta de ${host(url)}", e)
            WifiRescue.failed(e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * GET de bytes (imágenes) con tope de tamaño. Sigue hasta 4 redirecciones, también entre
     * http y https (muchos sitios de radios redirigen así). Null si falla o si supera [maxBytes].
     */
    fun getBytes(url: String, maxBytes: Int = 1_000_000, timeoutMs: Int = 8000): ByteArray? {
        // AUD-118 (v0.79.6): un enlace que Java no puede leer (los logos vienen de directorios de
        // terceros) devolvía una excepción y cerraba la app; ahora es "null si falla", como dice arriba
        var target = try { URL(url) } catch (e: Exception) { return null }
        repeat(5) {
            var conn: HttpURLConnection? = null
            try {
                conn = target.openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) OnixLauncher/${BuildConfig.VERSION_NAME}")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    target = URL(target, loc)
                    return@repeat
                }
                if (code !in 200..299) return null
                conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > maxBytes) return null
                    }
                    return out.toByteArray()
                }
            } catch (e: Exception) {
                WifiRescue.failed(e)
                return null // un logo que no carga no es un error para mostrar
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * Describe qué devuelve un enlace, para el informe de Diagnóstico: código HTTP, tipo, tamaño,
     * redirecciones y, si es una página, su título y cuántos enlaces de audio tiene. No lanza errores.
     */
    fun inspect(url: String, userAgent: String = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"): String {
        var target = try { URL(url) } catch (e: Exception) { return "enlace inválido" }
        val hops = mutableListOf<String>()
        repeat(5) {
            var conn: HttpURLConnection? = null
            try {
                conn = target.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept-Language", "es-AR,es;q=0.9")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return "HTTP $code sin destino"
                    target = URL(target, loc)
                    hops += "$code→${target.host}"
                    return@repeat
                }
                val type = conn.contentType.orEmpty().substringBefore(";")
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val buf = ByteArray(65_536)
                var n = 0
                stream?.use { input ->
                    while (n < buf.size) {
                        val r = input.read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                        if (type.startsWith("audio/")) break
                    }
                }
                val text = String(buf, 0, n, Charsets.UTF_8)
                val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(text)?.groupValues?.get(1)?.trim()?.take(60)
                val extra = when {
                    type.contains("html") -> " · título \"${title ?: "-"}\" · ${RadioDirectory.streamLinks(text).size} enlaces de audio"
                    type.contains("xml") -> " · " + text.replace(Regex("\\s+"), " ").take(160)
                    else -> ""
                }
                val via = if (hops.isEmpty()) "" else " (vía ${hops.joinToString(", ")})"
                return "HTTP $code · ${type.ifEmpty { "sin tipo" }} · ${n}B$extra$via"
            } catch (e: Exception) {
                return "sin respuesta: ${e.javaClass.simpleName} ${e.message?.take(60) ?: ""}".trim()
            } finally {
                conn?.disconnect()
            }
        }
        return "demasiadas redirecciones"
    }
}

/**
 * Vuelta de la red (v0.40.0): si el Wi-Fi o los datos se cortan y después vuelven, se avisa a la
 * interfaz ([back] sube) para que pida el clima de nuevo, y se retoma la radio si sonaba y se cortó
 * por la red (no si el usuario la pausó). Spotify se reconecta solo.
 */
object NetWatch {
    /** Sube cada vez que la red vuelve después de un corte. */
    val back = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var lost = false
    private var registered = false

    /**
     * La red volvió (aviso de Android o nombres que resuelven de nuevo, v0.81.21): la interfaz pide
     * otra vez ([back]) y se retoma lo que había quedado esperando. En el hilo principal.
     */
    fun announce(app: android.content.Context) {
        back.intValue++
        LastAudio.resumeRadioAfterNetwork(app)
        // v0.81.22: lo que falló por la red y no tenía quién lo volviera a pedir
        RadioCatalog.retry(app)
        PhoneShare.retryWaiting(app)
        ReportUpload.retry(app)
    }

    fun register(context: android.content.Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        WifiRescue.app = app
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onLost(network: android.net.Network) {
                    lost = true
                    AppLog.info("Red: se cortó")
                }

                override fun onAvailable(network: android.net.Network) {
                    if (!lost) return          // al registrarse avisa la red actual: no es una vuelta
                    lost = false
                    main.postDelayed({
                        AppLog.info("Red: volvió")
                        announce(app)
                    }, 3000)                    // margen para que el Wi-Fi termine de conectarse
                }
            })
        } catch (e: Exception) {
            AppLog.warn("Red: no se pudo vigilar la conexión", e)
        }
    }
}

/**
 * Wi-Fi conectado pero sin nombres (v0.81.19). Informe del 12/11/2026: el celular que da la red se
 * quedó sin señal y la recuperó; el Wi-Fi de la radio siguió "conectado" pero ningún nombre volvió
 * a resolver (`UnknownHostException` en Spotify, TomTom, clima y radios) hasta el final del viaje.
 * Si las fallas de nombre siguen un minuto, se apaga y prende el Wi-Fi para que pida la red de
 * nuevo. Si no alcanza, se reintenta cada vez más espaciado. No se toca con Android Auto o CarPlay.
 */
object WifiRescue {
    const val MIN_FAILURES = 3
    const val MIN_STUCK_MS = 60_000L
    const val RECOVER_MIN = 2
    private const val OFF_MS = 3_000L
    private const val PROBE_MS = 30_000L
    private const val PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204"
    private val probing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var isDown = false

    @Volatile var app: android.content.Context? = null
    @Volatile private var failures = 0
    @Volatile private var firstFailureAt = 0L
    @Volatile private var restarts = 0
    @Volatile private var lastRestartAt = 0L

    /** True mientras los nombres no resuelven (para decir "sin conexión" en la interfaz). */
    val down: Boolean get() = isDown

    /**
     * v0.81.21: si después de [failuresBefore] fallas de nombre un servidor contesta, la red volvió
     * aunque Android nunca la haya dado por cortada (el Wi-Fi siguió "conectado"). Pura.
     */
    fun recovered(failuresBefore: Int): Boolean = failuresBefore >= RECOVER_MIN

    private fun now(): Long = System.nanoTime() / 1_000_000L

    /** Espera mínima antes del reinicio número [restarts] + 1. Pura. */
    fun gapMs(restarts: Int): Long = when {
        restarts <= 0 -> 0L
        restarts == 1 -> 5 * 60_000L
        restarts == 2 -> 10 * 60_000L
        else -> 20 * 60_000L
    }

    fun isNameFailure(e: Throwable): Boolean = e is java.net.UnknownHostException

    /** Si corresponde reiniciar el Wi-Fi. Pura. */
    fun shouldRestart(failures: Int, stuckMs: Long, wifi: Boolean, projection: Boolean, restarts: Int, sinceRestartMs: Long): Boolean =
        failures >= MIN_FAILURES && stuckMs >= MIN_STUCK_MS && wifi && !projection && sinceRestartMs >= gapMs(restarts)

    /** Un servidor contestó: los nombres resuelven. */
    fun answered() {
        val before = failures
        val wasDown = isDown
        val restarted = restarts > 0      // el reinicio del Wi-Fi pone la cuenta en cero
        failures = 0
        restarts = 0
        isDown = false
        if (!wasDown || !(recovered(before) || restarted)) return
        val c = app ?: return
        // Mismo aviso que una vuelta de red de Android: clima, listas, letra y radio piden de nuevo
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            AppLog.info("Red: los nombres volvieron a resolver")
            NetWatch.announce(c)
        }
    }

    /**
     * Algo que no pasa por [Net] falló (la radio por internet): se prueba la red una vez. Si los
     * nombres no resuelven, queda la vigilancia de [failed].
     */
    fun suspect() {
        if (app == null) return
        kotlin.concurrent.thread(isDaemon = true, name = "onix-red-prueba") { probe() }
    }

    /** Pedido mínimo sin registro (no llena "Últimos eventos"): solo dice si los nombres resuelven. */
    private fun probe() {
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = java.net.URL(PROBE_URL).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.responseCode
            answered()
        } catch (e: Exception) {
            failed(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Mientras los nombres no resuelven, prueba la red cada 30 s. Sin esto la caída solo se veía
     * cuando otra parte de la app pedía algo (con el clima ya cargado, cada 15 minutos).
     */
    private fun watch() {
        if (!probing.compareAndSet(false, true)) return
        kotlin.concurrent.thread(isDaemon = true, name = "onix-red-vigia") {
            try {
                while (isDown) {
                    Thread.sleep(PROBE_MS)
                    if (isDown) probe()
                }
            } catch (_: Throwable) {
            } finally {
                probing.set(false)
            }
        }
    }

    fun failed(e: Throwable) {
        if (!isNameFailure(e)) return
        val t = now()
        if (failures == 0) firstFailureAt = t
        failures++
        isDown = true
        val c = app ?: return
        watch()
        try {
            val since = if (restarts == 0) Long.MAX_VALUE else t - lastRestartAt
            if (!shouldRestart(failures, t - firstFailureAt, wifiConnected(c), Projection.sessionActive(c), restarts, since)) return
            restarts++
            lastRestartAt = t
            failures = 0
            restart(c)
        } catch (ex: Exception) {
            AppLog.warn("Red: no se pudo reiniciar el Wi-Fi", ex)
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiConnected(c: android.content.Context): Boolean {
        val cm = c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val info = cm.activeNetworkInfo ?: return false
        return info.isConnected && info.type == android.net.ConnectivityManager.TYPE_WIFI
    }

    @Suppress("DEPRECATION")
    private fun restart(c: android.content.Context) {
        val wifi = c.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return
        val off = wifi.setWifiEnabled(false)
        AppLog.info("Red: Wi-Fi conectado pero sin nombres; lo reinicio (vez $restarts, apagado=$off)")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                AppLog.info("Red: Wi-Fi prendido de nuevo (${wifi.setWifiEnabled(true)})")
            } catch (ex: Exception) {
                AppLog.warn("Red: no se pudo prender el Wi-Fi", ex)
            }
        }, OFF_MS)
    }
}

/**
 * Conexiones seguras modernas en Android 7 (v0.43.0). El TLS de Android 7.1 no tiene TLS 1.3 y
 * ofrece pocas curvas; algunos servidores (OSRM y FOSSGIS) cortan la conexión con "Handshake
 * failed". Se usa Conscrypt (el mismo motor TLS de Android, en versión nueva y dentro de la app)
 * para todas las conexiones HTTPS de HttpURLConnection (APIs, radio, mapa).
 * Los certificados de confianza siguen siendo los del sistema más los de
 * network_security_config.xml: el administrador de confianza se toma de Android antes de cambiar.
 * En Android 10 o más no hace falta y no se toca.
 */
object Tls {
    @Volatile var engine: String = "del sistema"
        private set
    /** Fábrica de Conscrypt, para [DirectHttps] (null en Android 10 o más, o si no se pudo activar). */
    @Volatile var factory: javax.net.ssl.SSLSocketFactory? = null
        private set
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        if (android.os.Build.VERSION.SDK_INT >= 29) return
        try {
            val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as java.security.KeyStore?)
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS", org.conscrypt.Conscrypt.newProvider())
            ctx.init(null, tmf.trustManagers, null)
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
            factory = ctx.socketFactory
            engine = "Conscrypt (TLS 1.3)"
            AppLog.info("Red: conexiones seguras con Conscrypt (TLS 1.3)")
        } catch (e: Throwable) {
            AppLog.warn("Red: no se pudo activar Conscrypt; sigue el TLS del sistema", e)
        }
    }
}


/**
 * GET por TLS 1.3 directo (v0.71.0, HU-013). La Prueba completa del 04/11 mostró que con
 * Conscrypt el saludo TLS con OSRM y FOSSGIS anda (TLS 1.3), pero el pedido por HttpURLConnection
 * falla: el HttpURLConnection de Android 7 (su OkHttp interno) limita los protocolos y cifrados del
 * socket a su lista de TLS 1.2, y esos servidores no aceptan el TLS 1.2 que queda. Acá se abre el
 * socket de Conscrypt sin tocarle nada, se verifica el certificado y el nombre del servidor, y se
 * manda un GET HTTP/1.1 simple. Se usa como respaldo de las rutas; [parse] es pura, para los tests.
 */
object DirectHttps {
    private const val MAX_BYTES = 4_000_000

    /** Código y texto de la respuesta, o null si no hubo conexión o no hay Conscrypt. Bloquea. */
    fun get(url: String, timeoutMs: Int = 10_000): Pair<Int, String>? {
        val factory = Tls.factory ?: return null
        return try {
            val u = URL(url)
            val port = if (u.port > 0) u.port else 443
            val plain = java.net.Socket()
            plain.connect(java.net.InetSocketAddress(u.host, port), timeoutMs)
            plain.soTimeout = timeoutMs
            (factory.createSocket(plain, u.host, port, true) as javax.net.ssl.SSLSocket).use { ssl ->
                ssl.startHandshake()
                if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(u.host, ssl.session)) {
                    throw javax.net.ssl.SSLPeerUnverifiedException("el certificado no es de ${u.host}")
                }
                ssl.outputStream.apply { write(requestText(url, "OnixLauncher/${BuildConfig.VERSION_NAME}").toByteArray()); flush() }
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                val input = ssl.inputStream
                while (out.size() < MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                parse(out.toByteArray())
            }
        } catch (e: Exception) {
            AppLog.warn("Sin respuesta (TLS 1.3 directo) de ${Net.host(url)}", e)
            WifiRescue.failed(e)
            null
        }
    }

    fun requestText(url: String, userAgent: String): String {
        val u = URL(url)
        val path = (u.path.ifEmpty { "/" }) + (u.query?.let { "?$it" } ?: "")
        return "GET $path HTTP/1.1\r\nHost: ${u.host}\r\nUser-Agent: $userAgent\r\n" +
            "Accept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
    }

    /** Lee una respuesta HTTP/1.1 completa (largo fijo, en trozos o hasta el cierre). */
    fun parse(raw: ByteArray): Pair<Int, String>? {
        val text = String(raw, Charsets.ISO_8859_1)
        val end = text.indexOf("\r\n\r\n")
        val head = if (end >= 0) text.substring(0, end) else text
        val status = head.lineSequence().firstOrNull()?.split(" ")
        val code = status?.getOrNull(1)?.toIntOrNull()
        if (status?.firstOrNull()?.startsWith("HTTP/") != true || code == null) return null
        if (end < 0) return code to ""
        val headers = head.lines().drop(1).mapNotNull { l ->
            val i = l.indexOf(':'); if (i > 0) l.substring(0, i).trim().lowercase() to l.substring(i + 1).trim() else null
        }.toMap()
        var body = raw.copyOfRange(end + 4, raw.size)
        if (headers["transfer-encoding"]?.lowercase()?.contains("chunked") == true) {
            val out = java.io.ByteArrayOutputStream()
            var pos = 0
            while (pos < body.size) {
                val lineEnd = String(body, pos, body.size - pos, Charsets.ISO_8859_1).indexOf("\r\n")
                if (lineEnd < 0) break
                val size = String(body, pos, lineEnd, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16) ?: break
                if (size == 0) break
                val start = pos + lineEnd + 2
                if (start + size > body.size) { out.write(body, start, body.size - start); break }
                out.write(body, start, size)
                pos = start + size + 2
            }
            body = out.toByteArray()
        } else {
            headers["content-length"]?.toIntOrNull()?.let { if (it in 0..body.size) body = body.copyOf(it) }
        }
        return code to String(body, Charsets.UTF_8)
    }
}
