package com.axel.onixlauncher

import android.content.Context

/*
 * Luces del auto en la app (faros del Onix, haces sobre el asfalto e indicador de la franja).
 *
 * Por defecto siguen a la perilla de luces del auto: las radios Android genéricas tienen un cable
 * de iluminación (ILL, casi siempre naranja) conectado a las luces; al prenderlas, la radio pasa a
 * modo noche y la app lo lee (`Configuration.UI_MODE_NIGHT_YES`). Si la radio no lo informa, se
 * puede elegir a mano o volver al cálculo por hora tocando "Luces" en la franja inferior.
 */

enum class LightsMode(val label: String) {
    RADIO("según la radio"),
    ON("encendidas"),
    OFF("apagadas"),
    HORA("por hora");

    fun next(): LightsMode = entries[(ordinal + 1) % entries.size]
}

/** Si las luces están prendidas según el modo elegido (función pura, para los tests). */
fun lightsOn(mode: LightsMode, systemNight: Boolean, ambient: Ambient): Boolean = when (mode) {
    LightsMode.RADIO -> systemNight
    LightsMode.ON -> true
    LightsMode.OFF -> false
    LightsMode.HORA -> ambient != Ambient.DIA
}

/**
 * Señal de luces de la radio NWD (v0.45.0). Con el "sensor de iluminación" de la radio prendido
 * (Ajustes de la radio), el ajuste del sistema `high_beam_state` pasa a 1 al prender las luces del
 * auto y vuelve a 0 al apagarlas (Prueba completa del 28/10 11:42: 11:41:29 → 1, 11:41:42 → 0; a
 * la vez la radio baja el brillo). El nombre dice "luz alta" pero es el cable de luces (ILL).
 */
object RadioIll {
    const val KEY = "high_beam_state"
    /** v0.49.0: el estado de las luces también está en el bit 2 de este ajuste (visto en CleanLauncher). */
    const val KEY_STATE = "mcu_no_source_device_state"
    /** Aviso de la radio NWD al cambiar las luces, con `extra_ill_state` (byte, 0 = apagadas). */
    const val ACTION_ILL = "com.nwd.ACTION_ILL_STATE_CHANGE"
    val on = androidx.compose.runtime.mutableStateOf(false)
    /** Si la radio informa las luces por alguna de las tres vías. */
    val present = androidx.compose.runtime.mutableStateOf(false)
    /** Por dónde llegó el último dato (para Diagnóstico y el informe). */
    val via = androidx.compose.runtime.mutableStateOf("ninguna")
    private var registered = false

    /** Valor de `high_beam_state` a prendido/apagado (función pura, para los tests). */
    fun parse(value: String?): Boolean = value?.trim() == "1"

    /** Luces según el bit 2 de `mcu_no_source_device_state` (función pura, para los tests). */
    fun fromState(value: String?): Boolean = (value?.trim()?.toIntOrNull() ?: 0) and 2 != 0

    private fun set(now: Boolean, from: String) {
        present.value = true
        via.value = from
        if (now != on.value) AppLog.info("Luces del auto: ${if (now) "prendidas" else "apagadas"} ($from)")
        on.value = now
    }

    private fun read(c: Context) {
        val beam = try { android.provider.Settings.System.getString(c.contentResolver, KEY) } catch (e: Exception) { null }
        val state = try { android.provider.Settings.System.getString(c.contentResolver, KEY_STATE) } catch (e: Exception) { null }
        when {
            beam != null || state != null -> set(parse(beam) || fromState(state), if (beam != null) "ajuste $KEY / $KEY_STATE" else "ajuste $KEY_STATE")
            else -> present.value = false
        }
    }

    fun register(context: Context) {
        if (registered) return
        registered = true
        val c = context.applicationContext
        read(c)
        try {
            val obs = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = read(c)
            }
            c.contentResolver.registerContentObserver(android.provider.Settings.System.getUriFor(KEY), false, obs)
            c.contentResolver.registerContentObserver(android.provider.Settings.System.getUriFor(KEY_STATE), false, obs)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: android.content.Intent) {
                    set(i.getByteExtra("extra_ill_state", 0.toByte()).toInt() != 0, "aviso de la radio")
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                c.registerReceiver(receiver, android.content.IntentFilter(ACTION_ILL), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                c.registerReceiver(receiver, android.content.IntentFilter(ACTION_ILL))
            }
        } catch (e: Exception) {
            AppLog.warn("Luces: no se pudo leer la señal de la radio", e)
        }
    }
}

/**
 * Brillo de la pantalla siempre al máximo mientras se ve el launcher (v0.48.0). Con el "sensor de
 * iluminación" prendido, la radio baja el brillo al prender las luces (240 → 120). La app no
 * puede cambiar los ajustes propios de la radio, pero sí pedir brillo máximo para su propia
 * ventana (`screenBrightness = 1`), sin permisos. Si la radio baja la luz de fondo por su
 * microcontrolador y no por Android, esto puede no alcanzar: se confirma mirando la pantalla.
 * Preferencia en SharedPreferences `pantalla` (`brillo_max`, por defecto sí).
 */
object ScreenBrightness {
    val forceMax = androidx.compose.runtime.mutableStateOf(true)
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        forceMax.value = c.getSharedPreferences("pantalla", Context.MODE_PRIVATE).getBoolean("brillo_max", true)
    }

    fun toggle(c: Context) {
        forceMax.value = !forceMax.value
        c.getSharedPreferences("pantalla", Context.MODE_PRIVATE).edit().putBoolean("brillo_max", forceMax.value).apply()
    }

    /** Valor para la ventana: 1 = máximo; -1 = el del sistema (función pura, para los tests). */
    fun windowValue(forceMax: Boolean): Float = if (forceMax) 1f else -1f
}

/** Modo guardado en el equipo (SharedPreferences `luces`). */
object LightsPrefs {
    private const val KEY = "modo"

    fun load(context: Context): LightsMode {
        val name = context.getSharedPreferences("luces", Context.MODE_PRIVATE).getString(KEY, null)
        return LightsMode.entries.firstOrNull { it.name == name } ?: LightsMode.RADIO
    }

    fun save(context: Context, mode: LightsMode) {
        context.getSharedPreferences("luces", Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
    }
}

/**
 * Avisos propios de la radio (marca NWD) que pueden traer el estado del cable de luces: el control
 * de brillo del sistema escucha `com.nwd.action.ACTION_SYSTEM_VALUE_CHANGE` (visto en el log del
 * head unit, v0.35.2). Se registran sus datos en el informe de Diagnóstico para encontrar cuál
 * cambia al prender las luces; todavía no cambian las luces de la app.
 */
object NwdSignals {
    private val actions = listOf(
        "com.nwd.action.ACTION_SYSTEM_VALUE_CHANGE",
        "com.nwd.action.ACTION_KEY_VALUE",
        "com.nwd.action.ACTION_SET_SYSTEM_PROP",
        // v0.49.0 (vistos en CleanLauncher, para el informe): luces, contacto, marcha atrás, fuente de audio
        "com.nwd.ACTION_ILL_STATE_CHANGE",
        "com.nwd.action.ACTION_MCU_STATE_CHANGE",
        "com.android.action.ACTION_BACKCAR_STATE_CHANGE",
        "com.nwd.action.ACTION_CHANGE_SOURCE"
    )
    private var last = ""
    private var registered = false
    private val lines = ArrayDeque<String>()

    /** Últimos avisos distintos y cambios de ajustes o propiedades (hasta 80), con la hora, para el informe. */
    fun recent(): List<String> = synchronized(lines) { lines.toList() }

    /** Teclas y avisos de teclas de la radio aparte (hasta 60), para que no se pierdan entre otros avisos (v0.62.0). */
    private val keyLines = ArrayDeque<String>()
    fun keys(): List<String> = synchronized(keyLines) { keyLines.toList() }

    /** Última tecla, en vivo para Diagnóstico (v0.72.0): se aprieta un botón y se ve qué manda. */
    val lastKey = androidx.compose.runtime.mutableStateOf("ninguna todavía (apretá un botón)")

    /** "action · clave=valor, …" con los datos del aviso (función pura de texto, sin Android). */
    fun describe(action: String, extras: Map<String, Any?>): String =
        action.substringAfterLast('.') + " · " + extras.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }

    /** Anota una línea con la hora (si no es igual a la anterior). */
    fun add(line: String) {
        val c = java.util.Calendar.getInstance()
        val t = String.format(java.util.Locale.US, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
        val isKey = line.startsWith("TECLA") || line.startsWith("ACTION_KEY_VALUE")
        // En vivo aunque se repita la misma tecla (el informe sí las junta)
        if (isKey) lastKey.value = "$t · " + line.substringAfter(" · ")
        if (line == last) return
        last = line
        synchronized(lines) {
            lines.addLast("$t $line")
            while (lines.size > 80) lines.removeFirst()
        }
        if (isKey) synchronized(keyLines) {
            keyLines.addLast("$t $line")
            while (keyLines.size > 60) keyLines.removeFirst()
        }
    }

    /** Teclas que llegan a la app (v0.53.0): para saber qué manda cada botón del volante. Pura, para los tests. */
    fun keyLine(keyCode: Int, name: String, scanCode: Int, source: Int): String =
        "TECLA · $name (código $keyCode, scan $scanCode, fuente $source)"

    fun register(context: Context) {
        if (registered) return
        registered = true
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                val b = intent.extras
                val map = b?.keySet()?.associateWith { k -> @Suppress("DEPRECATION") b.get(k) }.orEmpty()
                add(describe(intent.action.orEmpty(), map))
            }
        }
        val filter = android.content.IntentFilter().apply { actions.forEach { addAction(it) } }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            AppLog.warn("Radio: no se pudieron escuchar los avisos de la marca", e)
        }
    }

    private fun stamp(): String {
        val c = java.util.Calendar.getInstance()
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
    }

    /** Nombres de propiedades del sistema que pueden ser las luces o el modo día/noche de la radio. */
    private val propKeys = Regex("nwd|ill|light|lamp|night|mcu|backlight|dim|day|lux|car|can", RegexOption.IGNORE_CASE)

    /** Cambios entre dos lecturas: "clave: antes → después" (función pura, para los tests). */
    fun diff(old: Map<String, String?>, new: Map<String, String?>): List<String> =
        (old.keys + new.keys).sorted().mapNotNull { k ->
            val a = old[k]
            val b = new[k]
            if (a == b) null else "$k: ${a ?: "(no estaba)"} → ${b ?: "(se borró)"}"
        }

    /** Salida de `getprop` ("[nombre]: [valor]" por línea) a un mapa (función pura, para los tests). */
    fun parseGetprop(text: String): Map<String, String> {
        val re = Regex("^\\[(.+?)\\]: \\[(.*)\\]$")
        return text.lineSequence().mapNotNull { re.find(it.trim())?.destructured?.let { (k, v) -> k to v } }.toMap()
    }

    /** Cambios que pasan todo el tiempo y no sirven para las luces (app en pantalla, hora, procesos). */
    private val noise = Regex("^(system/(activity_starting|key_top_app|key_top_cls|nwd_system_time)|sys\\.nwd\\.(focus\\.app\\.pid|playing\\.pids|valid\\.play\\.pid|cur\\.pkg|cur\\.music\\.stream\\.proc))\\b")

    /** Si un cambio es ruido (función pura, para los tests). */
    fun noisy(change: String): Boolean = noise.containsMatchIn(change)

    private fun note(line: String) {
        synchronized(lines) {
            lines.addLast("${stamp()} $line")
            while (lines.size > 80) lines.removeFirst()
        }
        AppLog.info(line)
    }

    /** Todos los ajustes de system, global y secure ("system/nombre" → valor). */
    private fun readAllSettings(c: Context): Map<String, String?> {
        val out = HashMap<String, String?>()
        for ((table, uri) in listOf(
            "system" to android.provider.Settings.System.CONTENT_URI,
            "global" to android.provider.Settings.Global.CONTENT_URI,
            "secure" to android.provider.Settings.Secure.CONTENT_URI
        )) {
            try {
                c.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { cur ->
                    while (cur.moveToNext()) {
                        val name = cur.getString(0) ?: continue
                        out["$table/$name"] = cur.getString(1)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    /** Propiedades del sistema que coinciden con [propKeys], leídas con `getprop`. */
    fun readProps(): Map<String, String> = try {
        val proc = Runtime.getRuntime().exec("getprop")
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        parseGetprop(text).filterKeys { propKeys.containsMatchIn(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    /**
     * v0.42.0 / v0.44.0: anota en el informe cada cambio de los ajustes del sistema (system, global y
     * secure, todos) y de las propiedades de la radio (`getprop`, cada 3 s), para encontrar qué
     * cambia al prender las luces con el "sensor de iluminación" de la radio activado.
     */
    // ---- Captura de cambios (v0.48.0: solo con Diagnóstico abierto o durante la Prueba completa) ----
    // En v0.44.0 corría siempre: cada cambio de ajuste leía las tres tablas en el hilo principal y
    // `getprop` se ejecutaba cada 3 s. La señal de luces ya se encontró (RadioIll), así que el
    // registro completo queda solo para diagnosticar.
    private var captureThread: android.os.HandlerThread? = null
    private var captureHandler: android.os.Handler? = null
    private var observer: android.database.ContentObserver? = null
    private var captureUsers = 0

    @Synchronized
    fun startCapture(context: Context) {
        captureUsers++
        if (captureUsers > 1) return
        val c = context.applicationContext
        val thread = android.os.HandlerThread("onix-captura").apply { start() }
        val h = android.os.Handler(thread.looper)
        captureThread = thread
        captureHandler = h
        h.post {
            var snapshot = readAllSettings(c)
            var props: Map<String, String?> = readProps()
            val obs = object : android.database.ContentObserver(h) {
                override fun onChange(selfChange: Boolean) {
                    val now = readAllSettings(c)
                    for (change in diff(snapshot, now)) if (!noisy(change)) note("AJUSTE $change")
                    snapshot = now
                }
            }
            observer = obs
            try {
                for (uri in listOf(
                    android.provider.Settings.System.CONTENT_URI,
                    android.provider.Settings.Global.CONTENT_URI,
                    android.provider.Settings.Secure.CONTENT_URI
                )) c.contentResolver.registerContentObserver(uri, true, obs)
            } catch (e: Exception) {
                AppLog.warn("Luces: no se pudieron vigilar los ajustes del sistema", e)
            }
            h.post(object : Runnable {
                override fun run() {
                    val now: Map<String, String?> = readProps()
                    if (now.isNotEmpty()) {
                        for (change in diff(props, now)) if (!noisy(change)) note("PROP $change")
                        props = now
                    }
                    h.postDelayed(this, 3000)
                }
            })
        }
    }

    @Synchronized
    fun stopCapture(context: Context) {
        if (captureUsers == 0) return
        captureUsers--
        if (captureUsers > 0) return
        observer?.let { try { context.applicationContext.contentResolver.unregisterContentObserver(it) } catch (_: Exception) {} }
        observer = null
        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }
}
