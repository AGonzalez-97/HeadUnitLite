package com.axel.onixlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/*
 * OBD2 con un lector ELM327 por Bluetooth (v0.31.0).
 *
 * El lector tiene que estar emparejado en Ajustes → Bluetooth. La app busca entre los equipos
 * emparejados uno cuyo nombre parezca un lector OBD (OBD, ELM, V-LINK, Vgate…), se conecta por el
 * perfil de puerto serie (SPP), lo inicializa con comandos AT y le pide cada ~1 s temperatura del
 * motor (01 05), RPM (01 0C), velocidad (01 0D), nivel de nafta (01 2F) y voltaje (AT RV).
 * Lo que el auto no informa queda en null ("--"). Si se corta, reintenta a los 5 s y va espaciando
 * hasta 30 s (ver [ObdRecovery]); desde la v0.81.13 no reinicia el Bluetooth de la radio.
 *
 * v0.40.0: a veces, al dar contacto, el lector se conecta antes de que la computadora del auto
 * esté lista y queda contestando solo el voltaje (que mide el propio lector). Ahora, al conectar,
 * se espera a que el lector encuentre el protocolo del auto (0100 con más tiempo) y, si la
 * computadora no contesta [ObdWatch.MAX_ECU_MISSES] lecturas seguidas, se reinicia el lector solo,
 * sin tener que apagar y prender el auto.
 *
 * v0.50.0: la lectura de cada vuelta, los códigos de falla, las alertas y el consumo están en
 * ObdCodes.kt (ObdReader), probados en la CI con un lector simulado.
 */

/** Datos del motor leídos por OBD2. Cada valor es null si el auto no lo informa. */
// AUD-075 (v0.79.5): data class, así una lectura con los mismos valores no cuenta como cambio y la
// pantalla no se vuelve a componer cada 0,7 s con el auto quieto
data class ObdData(
    val coolantC: Int? = null,
    val fuelPct: Int? = null,
    val volts: Float? = null,
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    /** Consumo estimado en km/l (v0.50.0); null parado o si el auto no da MAP ni MAF. */
    val kmPerLiter: Float? = null,
    /** Nafta que se está gastando en litros por hora (v0.52.0), también parado; para los litros del viaje. */
    val litersPerHour: Float? = null
)

/** Estado de la conexión, observable por la interfaz y por el informe de Diagnóstico. */
object ObdState {
    val status = mutableStateOf("sin buscar")
    val device = mutableStateOf<String?>(null)
    val data = mutableStateOf<ObdData?>(null)
    /** Respuestas crudas de la última prueba ("Probar OBD2"), para el informe. */
    val probe = mutableStateOf<List<String>>(emptyList())
    @Volatile var probeRequested = false
    /** Códigos de falla de la última lectura (v0.50.0); null = todavía no se leyeron. */
    val codes = mutableStateOf<ObdCodes?>(null)
    /** Alerta del motor que ya duró lo suficiente para avisar (v0.50.0). */
    val alert = mutableStateOf<EngineAlert?>(null)
    /** Resultado del último borrado de códigos, para mostrarlo en la ventana de códigos. */
    val clearResult = mutableStateOf<String?>(null)
    @Volatile var codesRequested = false
    /** Kilometraje total que informa el auto (PID 01A6), si lo informa (v0.78.1). */
    val odometerKm = mutableStateOf<Int?>(null)
    @Volatile var clearRequested = false
}

/** Cada cuánto se vuelven a leer los códigos de falla con el lector conectado. */
private const val CODES_EVERY_MS = 5 * 60_000L

/** Cuándo reiniciar el lector porque la computadora del auto no contesta (funciones puras, para los tests). */
object ObdWatch {
    const val MAX_ECU_MISSES = 5

    /** Lecturas seguidas sin respuesta del auto: vuelve a cero cuando contesta algún dato del motor. */
    fun misses(previous: Int, ecuAnswered: Boolean): Int = if (ecuAnswered) 0 else previous + 1

    fun mustReinit(misses: Int): Boolean = misses >= MAX_ECU_MISSES

    /**
     * v0.81.13: lecturas seguidas en las que no contestó ni el lector (ni el voltaje, que lo da el
     * propio ELM327). Una sola ya no corta el Bluetooth: cada reconexión es otra ocasión de fallar.
     */
    const val MAX_SILENT_POLLS = 3

    fun silent(previous: Int, readerAnswered: Boolean): Int = if (readerAnswered) 0 else previous + 1

    fun mustReconnect(silent: Int): Boolean = silent >= MAX_SILENT_POLLS
}

/**
 * Recuperación sola del OBD2 (v0.73.0, pedido del autor: "que sea automático"). A veces, al dar
 * contacto, el lector no aparecía nunca (Prueba completa: "conectando…" sin avanzar; con Torque
 * pasaba lo mismo): la conexión Bluetooth no tenía tiempo máximo y los clones del ELM327 a veces
 * dejan el Bluetooth de la radio trabado. Ahora: tiempo máximo para conectar, tres formas de
 * conectar y reintento enseguida al dar contacto o prender la pantalla. El reinicio automático del
 * Bluetooth de la radio se quitó en la v0.81.13 (Torque conecta sin él). Reglas puras, para los tests.
 */
object ObdRecovery {
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val MCU_ACTION = "com.nwd.action.ACTION_MCU_STATE_CHANGE"

    /**
     * v0.79.10: el estado del contacto (`extra_mcu_state`) se lee por su contenido: la radio puede
     * mandarlo como byte o texto y `getIntExtra` devolvía -1, así que el contacto nunca se detectaba.
     */
    fun mcuStateOf(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    }

    @Suppress("DEPRECATION")
    fun mcuState(intent: android.content.Intent): Int? = mcuStateOf(intent.extras?.get("extra_mcu_state"))

    /**
     * Espera antes de reintentar (v0.81.14): 15 s, 30 s, 1, 2, 5 y después cada 10 minutos. En la
     * radio NWD cada socket abierto ocupa un lugar del programa de Bluetooth (`gocsdk`) que no se
     * libera ni al cerrarlo (registro del auto, 2026-11-11: `client_fd` 13, 21 y 22 en 20 s); con los
     * lugares agotados contesta "all clients are connected" y no conecta nadie más, tampoco otra app.
     * Si el lector no contesta (apagado o tomado por otro equipo), insistir seguido solo los gasta.
     * El contacto o la pantalla prendida vuelven a empezar la cuenta.
     */
    fun retryDelayMs(failures: Int): Long = when {
        failures <= 1 -> 15_000L
        failures == 2 -> 30_000L
        failures == 3 -> 60_000L
        failures == 4 -> 120_000L
        failures == 5 -> 300_000L
        else -> 600_000L
    }

    /** La espera en palabras: segundos, o minutos desde un minuto. Pura. */
    fun waitText(ms: Long): String = if (ms >= 60_000L) "${ms / 60_000L} min" else "${ms / 1000L} s"

    /** La manera de conectar que se prueba en la vuelta siguiente a una falla (v0.81.14). Pura. */
    fun nextWay(current: Int, count: Int): Int = if (current in 0 until count) (current + 1) % count else 0

    /** Contacto puesto (aviso de la radio NWD, estado 1) o pantalla prendida: reintentar ya. */
    fun isWakeSignal(action: String?, mcuState: Int?): Boolean = when (action) {
        MCU_ACTION -> mcuState == 1
        "android.intent.action.SCREEN_ON" -> true
        else -> false
    }

    /**
     * v0.81.13: al llegar el aviso de contacto puesto, si hay que esperar [WAKE_SETTLE_MS] a que la
     * radio levante su Bluetooth. Solo si venía de contacto apagado o no hay conexión abierta: el
     * aviso ya no corta una conexión que anda (tampoco el corte `ACL_DISCONNECTED`, que podía llegar
     * tarde, de un intento anterior). Una conexión muerta la detecta la lectura. Pura.
     */
    fun settlesAfterWake(mcuWasOff: Boolean, hasLink: Boolean): Boolean = mcuWasOff || !hasLink

    /**
     * v0.81.10: qué avisos ponen en cero la cuenta de fallas y cortan la espera del reintento: el
     * contacto (puesto o sacado) y la pantalla prendida. El corte del lector (`ACL_DISCONNECTED`)
     * no: cerrar un intento fallido también lo desconecta, y desde la v0.79.18 eso reiniciaba la
     * cuenta en cada vuelta, así que el reintento salía sin esperar. Pura.
     */
    fun restartsRetries(action: String?, mcuState: Int?): Boolean = when (action) {
        MCU_ACTION -> mcuState == 0 || mcuState == 1
        "android.intent.action.SCREEN_ON" -> true
        else -> false
    }

    /**
     * v0.81.0: en el auto (informes 18:21 y 19:02) este lector ELM327 siempre conecta por la
     * manera 3 (`createRfcommSocket(1)`, índice 2) y el estéreo NWD reinicia su chip Bluetooth
     * ~12 s después de `ACTION_MCU_STATE_CHANGE` `estado=1` (`18:57:45 rw.zlink.bt.name=Chevrolet`).
     * Esperar [WAKE_SETTLE_MS] tras el contacto y empezar por [DEFAULT_PREFERRED_WAY] evita abrir
     * sockets en medio del reinicio del Bluetooth de la radio.
     */
    const val DEFAULT_PREFERRED_WAY = 2
    const val WAKE_SETTLE_MS = 15_000L

    fun isMcuOff(mcuState: Int?): Boolean = mcuState == 0

    /** Última manera de conectar que funcionó (0, 1 o 2), para probarla primero al dar contacto (v0.79.18). */
    @Volatile var preferredWay: Int = DEFAULT_PREFERRED_WAY

    /** La manera guardada entre arranques (v0.81.13); un valor fuera de 0 a 2 vuelve a [DEFAULT_PREFERRED_WAY]. Pura. */
    fun savedWay(stored: Int): Int = if (stored in 0..2) stored else DEFAULT_PREFERRED_WAY

    /** Devuelve los índices de [count] maneras poniendo primero [preferred] si es válido. Pura. */
    fun wayOrder(count: Int, preferred: Int): List<Int> =
        (0 until count).sortedBy { if (it == preferred) -1 else it }

    /**
     * Qué hizo el Bluetooth, en palabras, para "Últimos eventos" (v0.79.1): una búsqueda de equipos
     * o un corte pueden explicar que el lector deje de contestar. Null si no interesa. Pura.
     */
    fun bluetoothEvent(action: String?, state: Int, deviceName: String?): String? = when (action) {
        "android.bluetooth.adapter.action.DISCOVERY_STARTED" -> "Bluetooth: alguien empezó a buscar equipos (puede cortar el lector)"
        "android.bluetooth.adapter.action.STATE_CHANGED" -> when (state) {
            10 -> "Bluetooth: apagado"
            12 -> "Bluetooth: prendido"
            else -> null
        }
        "android.bluetooth.device.action.ACL_DISCONNECTED" -> "Bluetooth: se desconectó ${deviceName ?: "un equipo"}"
        else -> null
    }

    /**
     * v0.79.2: sin avance del lector en este tiempo (conectar, arrancar o leer), la conexión está
     * trabada (una escritura o un connect del Bluetooth que no vuelve): se cierra y cuenta como falla.
     */
    const val STUCK_MS = 20_000L
    /** Tope duro del arranque del lector (8 comandos de hasta 5 s, más margen). */
    const val INIT_GUARD_MS = 45_000L
    /** Tope para conectar (tres maneras de 15 s cada una, más margen). */
    /** v0.79.12: tope de la escritura de prueba de cada manera de conectar. */
    const val PROBE_MS = 8_000L
    const val CONNECT_GUARD_MS = 3 * (CONNECT_TIMEOUT_MS + PROBE_MS) + 5_000L

    /**
     * v0.79.12: en el auto el socket "conecta" pero la primera escritura (ATZ) se traba para siempre, y
     * solo se probaba la primera manera de conectar. Cada manera se verifica con una escritura de
     * prueba ([probeMs]); la que no la acepta o queda muda (v0.79.18) se cierra y se pasa a la siguiente.
     * Prueba primero [preferred] (la última que funcionó) para conectar enseguida al dar contacto.
     * v0.81.14: [maxSockets] limita cuántos sockets se abren por llamada (una manera que no llega a
     * abrir no cuenta); en la radio NWD cada socket abierto ocupa un lugar que no se libera.
     */
    internal fun connectVerified(
        ways: List<() -> ElmLink>,
        probeMs: Long = PROBE_MS,
        preferred: Int = 0,
        wayGapMs: Long = 0L,
        maxSockets: Int = Int.MAX_VALUE,
        onSuccess: (Int) -> Unit = {},
        log: (String) -> Unit = {},
    ): ElmLink {
        var last: Exception? = null
        var sockets = 0
        val order = wayOrder(ways.size, preferred)
        for ((idx, i) in order.withIndex()) {
            if (sockets >= maxSockets) break
            val make = ways[i]
            var link: ElmLink? = null
            try {
                val opened = make()
                link = opened
                sockets++
                withHardTimeout(probeMs, "prueba de escritura", onTimeout = { opened.close() }) {
                    opened.send("ATZ", 5_000)
                    if (!opened.answered) throw java.io.IOException("el lector no contestó")
                }
                onSuccess(i)
                log("OBD2: la manera ${i + 1} de conectar deja escribir")
                return opened
            } catch (e: Exception) {
                link?.close()
                last = e
                val more = idx < order.lastIndex && sockets < maxSockets
                log("OBD2: la manera ${i + 1} de conectar falló (${e.message ?: e.javaClass.simpleName}, paso: ${link?.step ?: "abriendo"})" +
                    if (more) "; pruebo la siguiente" else "")
                if (wayGapMs > 0L && more) {
                    try { Thread.sleep(wayGapMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            }
        }
        throw last ?: java.io.IOException("no se pudo conectar")
    }

    fun isStuck(lastProgressMs: Long, nowMs: Long): Boolean = nowMs - lastProgressMs >= STUCK_MS

    /**
     * Corre [block] en un hilo aparte y espera como mucho [ms]: si no termina, llama a [onTimeout]
     * (cerrar el socket, que destraba la lectura o escritura) y tira una excepción, así la falla se
     * cuenta y se reintenta. Un hilo que siga trabado queda abandonado; si
     * después termina, su resultado va a [onLate] (AUD-080: cerrar la conexión que nadie va a usar).
     */
    fun <T> withHardTimeout(ms: Long, what: String, onTimeout: () -> Unit = {}, onLate: (T) -> Unit = {}, block: () -> T): T {
        val abandoned = java.util.concurrent.atomic.AtomicBoolean(false)
        val handedOff = java.util.concurrent.atomic.AtomicBoolean(false)
        val lateResult: (T) -> Unit = { r -> if (handedOff.compareAndSet(false, true)) runCatching { onLate(r) } }
        val task = java.util.concurrent.FutureTask {
            val r = block()
            if (abandoned.get()) lateResult(r)
            r
        }
        Thread(task, "obd-$what").apply { isDaemon = true; start() }
        return try {
            task.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            abandoned.set(true)
            // Si terminó justo entre el tope y la marca, el resultado ya está: se entrega igual a onLate
            if (task.isDone) runCatching { task.get() }.getOrNull()?.let(lateResult)
            runCatching(onTimeout)
            task.cancel(true)
            throw IllegalStateException("$what: no respondió en ${ms / 1000} s (trabado)")
        } catch (e: java.util.concurrent.ExecutionException) {
            throw (e.cause as? Exception) ?: e
        }
    }

    /**
     * AUD-072 (v0.79.5): una cancelación (se cerró o se recreó la pantalla) no es una falla del
     * lector: no suma como falla y se deja pasar para que el bucle termine.
     */
    fun isReaderFailure(e: Throwable): Boolean =
        e !is kotlinx.coroutines.CancellationException || e is kotlinx.coroutines.TimeoutCancellationException

    /** Sube con cada contacto o pantalla prendida; la conexión lo mira para no esperar. */
    val kick = kotlinx.coroutines.flow.MutableStateFlow(0)
}

/** Lectura de respuestas del ELM327 (funciones puras, para los tests). */
object ObdParse {
    /**
     * Kilometraje total del auto (PID 01A6, norma OBD2 desde 2019; v0.78.1): 4 bytes en décimas de km.
     * Null si el auto no lo informa (lo más probable en un Onix 2018) o el valor no es creíble. Pura.
     */
    fun odometerKm(raw: String): Int? {
        val b = bytes(raw, 0xA6) ?: return null
        if (b.size < 4) return null
        val tenths = (b[0].toLong() shl 24) or (b[1].toLong() shl 16) or (b[2].toLong() shl 8) or b[3].toLong()
        val km = (tenths / 10).toInt()
        return km.takeIf { it in 1..2_000_000 }
    }

    /** Bytes de datos de una respuesta de modo 01 para [pid] ("41 05 7B" → [0x7B]); null si no hay. */
    fun bytes(raw: String, pid: Int): IntArray? {
        val clean = raw.uppercase().replace("SEARCHING...", "").replace(">", "")
        val tag = "41" + String.format("%02X", pid)
        for (line in clean.split('\r', '\n')) {
            val hex = line.replace(" ", "").trim()
            val i = hex.indexOf(tag)
            if (i < 0) continue
            val data = hex.substring(i + 4)
            if (data.length < 2 || data.any { it !in "0123456789ABCDEF" }) continue
            return IntArray(data.length / 2) { data.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    fun coolant(raw: String): Int? = bytes(raw, 0x05)?.let { it[0] - 40 }
    fun rpm(raw: String): Int? = bytes(raw, 0x0C)?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4 }
    fun speed(raw: String): Int? = bytes(raw, 0x0D)?.let { it[0] }
    fun fuel(raw: String): Int? = bytes(raw, 0x2F)?.let { Math.round(it[0] * 100f / 255f) }

    /** Voltaje de AT RV ("12.6V" → 12.6). */
    fun volts(raw: String): Float? =
        Regex("(\\d{1,2}\\.\\d)\\s*V", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toFloatOrNull()

    /**
     * Dirección Bluetooth del lector con solo el último byte visible (AUD-096, v0.79.7): la Prueba completa
     * se copia al portapapeles (que en Android 7 puede leer cualquier app) y a Descargas. Pura.
     */
    fun maskAddress(address: String?): String {
        val a = address?.trim().orEmpty()
        if (a.isEmpty()) return "?"
        return if (Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(a)) "XX:XX:XX:XX:XX:" + a.takeLast(2).uppercase() else "(dirección no reconocida)"
    }

    /** Si el nombre de un equipo Bluetooth parece un lector OBD. */
    fun looksLikeObd(name: String?): Boolean =
        name != null && Regex("OBD|ELM|V-?LINK|VGATE|ICAR|KONNWEI|VEEPEAK|SCAN", RegexOption.IGNORE_CASE).containsMatchIn(name)
}

private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/** Si la app puede usar Bluetooth (en Android 12+ hace falta el permiso "Dispositivos cercanos"). */
internal fun hasBluetoothPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/**
 * Una conexión abierta con el lector. AUD-085 (v0.79.6): recibe los flujos (y cómo cerrarlos) para
 * poder probarla sin Bluetooth; con un socket se arma igual que siempre.
 */
internal class ElmLink(
    private val input: InputStream,
    private val output: OutputStream,
    private val onClose: () -> Unit,
) {
    constructor(socket: BluetoothSocket) : this(socket.inputStream, socket.outputStream, { closeBluetoothSocket(socket) })

    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** v0.79.2: última vez que un comando salió entero (lo mira el vigía de la conexión). */
    val lastIoMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

    /** Manda un comando y lee hasta el prompt ">" o hasta [timeoutMs]. */
    fun send(cmd: String, timeoutMs: Long = 2500): String {
        while (input.available() > 0) input.read()          // descarta restos
        step = "escribiendo $cmd"
        output.write((cmd + "\r").toByteArray())
        output.flush()
        step = "esperando la respuesta a $cmd"
        lastIoMs.set(System.currentTimeMillis())
        val sb = StringBuilder()
        var gotByte = false
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (input.available() > 0) {
                val c = input.read()
                if (c < 0) break
                gotByte = true
                if (c.toChar() == '>') break
                sb.append(c.toChar())
            } else {
                Thread.sleep(10)
            }
        }
        answered = gotByte
        step = "listo $cmd"
        return sb.toString().trim()
    }

    /** v0.79.18: si el último send() recibió al menos un byte del lector (incluyendo el prompt ">"). */
    @Volatile var answered: Boolean = false

    /** v0.79.10: en qué paso está (para decir dónde se trabó el lector). */
    @Volatile var step: String = "sin comandos"

    /**
     * v0.79.9: el Bluetooth de la radio a veces se cuelga dentro de socket.close() y arrastraba al
     * bucle del lector (el OBD2 quedaba "conectando…" para siempre). Se cierra en un hilo aparte y
     * se espera como mucho [CLOSE_WAIT_MS]; si no vuelve, se lo abandona.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val t = Thread({ try { onClose() } catch (_: Exception) {} }, "obd-cerrar").apply { isDaemon = true; start() }
        try { t.join(CLOSE_WAIT_MS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    companion object {
        const val CLOSE_WAIT_MS = 2_000L
    }
}

@SuppressLint("MissingPermission") // se verifica con hasBluetoothPermission antes de usarlo
private fun findReader(context: Context): BluetoothDevice? {
    @Suppress("DEPRECATION")
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
    if (!adapter.isEnabled) return null
    val bonded = adapter.bondedDevices.orEmpty()
    val saved = context.getSharedPreferences("obd", Context.MODE_PRIVATE).getString("direccion", null)
    return bonded.firstOrNull { it.address == saved } ?: bonded.firstOrNull { ObdParse.looksLikeObd(it.name) }
}

/**
 * v0.79.20: en Android 7.1, `BluetoothSocket.close()` no despierta a otro hilo bloqueado en
 * `output.write()` sobre su `LocalSocket` interno (`mSocket`), dejando el canal RFCOMM tomado.
 * Llamar primero a `shutdownInput()` y `shutdownOutput()` sobre `mSocket` fuerza al kernel a
 * destrabar cualquier lectura o escritura bloqueada antes de cerrar el socket.
 */
internal fun closeBluetoothSocket(socket: BluetoothSocket) {
    runCatching {
        val field = BluetoothSocket::class.java.getDeclaredField("mSocket").apply { isAccessible = true }
        val local = field.get(socket) as? android.net.LocalSocket
        runCatching { local?.shutdownInput() }
        runCatching { local?.shutdownOutput() }
    }
    runCatching {
        val pfdField = BluetoothSocket::class.java.getDeclaredField("mPfd").apply { isAccessible = true }
        val pfd = pfdField.get(socket) as? android.os.ParcelFileDescriptor
        runCatching { pfd?.close() }
    }
    runCatching { socket.close() }
}

/** Conecta con tiempo máximo (v0.73.0): si el lector no contesta, se cierra el socket y connect() falla. */
private fun openWithTimeout(socket: BluetoothSocket): BluetoothSocket {
    val timer = java.util.Timer(true)
    timer.schedule(object : java.util.TimerTask() {
        override fun run() { closeBluetoothSocket(socket) }
    }, ObdRecovery.CONNECT_TIMEOUT_MS)
    try {
        socket.connect()
        return socket
    } catch (e: Exception) {
        closeBluetoothSocket(socket)
        throw e
    } finally {
        timer.cancel()
    }
}

@SuppressLint("MissingPermission")
private fun connect(device: BluetoothDevice): ElmLink {
    // Una búsqueda de equipos en curso hace fallar o demorar la conexión
    @Suppress("DEPRECATION")
    runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
    val ways = listOf<() -> BluetoothSocket>(
        { device.createRfcommSocketToServiceRecord(SPP) },
        // Muchos clones del ELM327 solo aceptan la conexión "insegura"
        { device.createInsecureRfcommSocketToServiceRecord(SPP) },
        // v0.73.0: canal 1 directo, lo que usan Torque y otras apps con los clones
        { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket }
    )
    // v0.79.12: cada manera se verifica con una escritura de prueba (ver connectVerified)
    // v0.79.18: prueba primero la última manera que funcionó para no demorar cada contacto
    // v0.81.14: un solo socket por vuelta; si falla, la vuelta siguiente prueba la otra manera
    val tried = ObdRecovery.preferredWay
    try {
        return ObdRecovery.connectVerified(
            ways.map { make -> { ElmLink(openWithTimeout(make())) } },
            preferred = tried,
            wayGapMs = 1_500L,
            maxSockets = 1,
            onSuccess = { ObdRecovery.preferredWay = it },
            log = { AppLog.info(it) },
        )
    } catch (e: Exception) {
        ObdRecovery.preferredWay = ObdRecovery.nextWay(tried, ways.size)
        throw e
    }
}

/** Espera [ms] o hasta que llegue un contacto / pantalla prendida (lo que pase primero). */
private suspend fun waitOrWake(ms: Long) {
    val seen = ObdRecovery.kick.value
    kotlinx.coroutines.withTimeoutOrNull(ms) { ObdRecovery.kick.first { it != seen } }
}

/**
 * Inicializa el lector. El reinicio (ATZ) ya se mandó en [ObdRecovery.connectVerified] con 5 s de
 * margen (v0.79.18) para no reiniciarlo dos veces seguidas y desfasar las respuestas en los clones.
 * El último paso (0100) deja que el lector busque el protocolo del auto con tiempo.
 */
private fun initElm(link: ElmLink): List<String> = listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATE0", "0100").map { cmd ->
    val timeout = when (cmd) {
        "0100" -> 8000L
        else -> 1500L
    }
    "$cmd → " + link.send(cmd, timeout).replace("\r", " ").ifBlank { "(sin respuesta)" }
}

/** Deja pasar un solo arranque; si el arranque falla, el pedido siguiente lo vuelve a intentar. */
internal class StartOnce {
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    fun run(start: () -> Unit): Boolean {
        if (!started.compareAndSet(false, true)) return false
        try {
            start()
        } catch (e: Throwable) {
            started.set(false)
            throw e
        }
        return true
    }
}

/**
 * Motor del auto (v0.81.12, ADR-0018): la conexión con el lector OBD2 y el registro de viajes viven
 * con el proceso de la app, no con la pantalla. Si Android vuelve a crear la pantalla, el lector
 * sigue conectado y el viaje sigue contando. La pantalla solo lee [ObdState].
 */
internal object CarEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val obdGate = StartOnce()
    private val tripsGate = StartOnce()
    private val activeLink = java.util.concurrent.atomic.AtomicReference<ElmLink?>(null)
    private val mcuOff = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastWakeMs = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Viajes de Mi auto (v0.52.0): una lectura por segundo con la velocidad y el consumo, en el hilo
     * principal (AUD-101). AUD-058: la velocidad en vivo (GpsSpeed.live), no la de la pantalla.
     */
    fun startTrips(context: Context): Boolean {
        val app = context.applicationContext
        return tripsGate.run {
            scope.launch(Dispatchers.Main) {
                CarCare.load(app)
                while (true) {
                    CarCare.tick(app, System.currentTimeMillis(), GpsSpeed.live(), ObdState.data.value)
                    delay(1000)
                }
            }
        }
    }

    /** Arranca la conexión con el lector (hace falta el permiso de Bluetooth). Se puede llamar muchas veces. */
    fun startObd(context: Context): Boolean {
        val app = context.applicationContext
        return obdGate.run {
            listen(app)
            scope.launch { loop(app) }
        }
    }

    // v0.73.0: al dar contacto o prender la pantalla, reintentar enseguida
    @SuppressLint("MissingPermission")
    private fun listen(app: Context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                val state = ObdRecovery.mcuState(intent)
                val deviceName = runCatching {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)?.name
                }.getOrNull()
                if (intent.action == ObdRecovery.MCU_ACTION && ObdRecovery.isMcuOff(state)) {
                    mcuOff.set(true)
                    activeLink.getAndSet(null)?.close()
                } else if (intent.action == ObdRecovery.MCU_ACTION && state == 1) {
                    // v0.81.13: el aviso de contacto puesto no corta una conexión que anda
                    if (ObdRecovery.settlesAfterWake(mcuOff.getAndSet(false), activeLink.get() != null)) {
                        lastWakeMs.set(System.currentTimeMillis())
                    }
                }
                // v0.81.10: el corte del lector no reinicia la cuenta de fallas (ver restartsRetries)
                if (ObdRecovery.restartsRetries(intent.action, state)) {
                    ObdRecovery.kick.value = ObdRecovery.kick.value + 1
                }
                // v0.79.1: lo que hace el Bluetooth mientras el lector se corta (para la Prueba completa)
                ObdRecovery.bluetoothEvent(intent.action, intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1), deviceName)?.let { AppLog.info(it) }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(ObdRecovery.MCU_ACTION)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun loop(app: Context) {
        var failures = 0
        val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)
        ObdRecovery.preferredWay = ObdRecovery.savedWay(prefs.getInt("manera", ObdRecovery.DEFAULT_PREFERRED_WAY))
        var seenKick = ObdRecovery.kick.value
        while (true) {
            // Contacto puesto o pantalla prendida: se empieza de cero (v0.73.0)
            if (ObdRecovery.kick.value != seenKick) {
                seenKick = ObdRecovery.kick.value
                failures = 0
            }
            if (mcuOff.get()) {
                ObdState.status.value = "en espera (contacto apagado)"
                ObdState.data.value = null
                waitOrWake(10_000)
                continue
            }
            val wakeElapsed = System.currentTimeMillis() - lastWakeMs.get()
            if (lastWakeMs.get() > 0L && wakeElapsed in 0 until ObdRecovery.WAKE_SETTLE_MS) {
                val waitMs = ObdRecovery.WAKE_SETTLE_MS - wakeElapsed
                AppLog.info("OBD2: espero ${waitMs / 1000} s a que la radio termine de iniciar su Bluetooth tras el contacto")
                ObdState.status.value = "esperando el Bluetooth de la radio…"
                delay(waitMs)
            }
            @Suppress("DEPRECATION")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && !adapter.isEnabled) {
                // Bluetooth apagado: se prende solo (v0.73.0)
                AppLog.info("OBD2: el Bluetooth estaba apagado; lo prendo")
                @Suppress("DEPRECATION")
                runCatching { adapter.enable() }
                waitOrWake(5_000)
            }
            val device = try { findReader(app) } catch (e: Exception) { null }
            if (device == null) {
                ObdState.status.value = "sin lector emparejado (o Bluetooth apagado)"
                ObdState.data.value = null
                waitOrWake(10_000)
                continue
            }
            ObdState.device.value = device.name ?: ObdParse.maskAddress(device.address)
            ObdState.status.value = "conectando…"
            var link: ElmLink? = null
            var retryMs = ObdRecovery.retryDelayMs(1)
            var guard: java.util.Timer? = null
            try {
                // v0.79.2: connect con tope propio (el Bluetooth de la radio a veces no lo corta)
                // AUD-080: si el connect abandonado termina después del tope, se cierra
                val opened = ObdRecovery.withHardTimeout(ObdRecovery.CONNECT_GUARD_MS, "conexión con el lector",
                    onLate = { it.close() }) { connect(device) }
                link = opened
                activeLink.set(opened)
                // Vigía: si ningún comando sale en 20 s (una escritura trabada), se cierra el socket
                opened.lastIoMs.set(System.currentTimeMillis())
                guard = java.util.Timer("obd-vigia", true).apply {
                    schedule(object : java.util.TimerTask() {
                        override fun run() {
                            if (ObdRecovery.isStuck(opened.lastIoMs.get(), System.currentTimeMillis())) {
                                AppLog.info("OBD2: el lector quedó trabado ${ObdRecovery.STUCK_MS / 1000} s (paso: ${opened.step}); cierro la conexión")
                                opened.close()
                                cancel()
                            }
                        }
                    }, 5_000L, 5_000L)
                }
                // v0.79.9: el arranque tiene tope duro; si la escritura se traba y el cierre no la destraba,
                // se abandona ese hilo y la falla se cuenta
                val initLog = ObdRecovery.withHardTimeout(ObdRecovery.INIT_GUARD_MS, "arranque del lector",
                    onTimeout = { AppLog.info("OBD2: el arranque no terminó (paso: ${opened.step})"); opened.close() }) { initElm(opened) }
                prefs.edit().putString("direccion", device.address).putInt("manera", ObdRecovery.preferredWay).apply()
                AppLog.info("OBD2: conectado a ${device.name}")
                ObdState.status.value = "conectado"
                failures = 0
                var misses = 0
                var silent = 0
                // AUD-051: un pedido de borrar hecho con el lector desconectado no se ejecuta al reconectar
                ObdState.clearRequested = false
                val reader = ObdReader { opened.send(it) }
                // v0.78.1: si el auto informa el kilometraje total, Mi auto se carga solo
                runCatching { ObdParse.odometerKm(opened.send("01A6")) }.getOrNull()?.let { km ->
                    ObdState.odometerKm.value = km
                    // AUD-101 (v0.79.5): Mi auto se modifica solo desde el hilo principal (lo usa tick() cada segundo)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { CarCare.fromCar(app, km) }
                    AppLog.info("OBD2: el auto informa el kilometraje total ($km km)")
                }
                val watch = AlertWatch()
                var codesAt = 0L
                while (true) {
                    if (ObdState.probeRequested) {
                        ObdState.probeRequested = false
                        val lines = mutableListOf("Lector: ${device.name} (${ObdParse.maskAddress(device.address)})")
                        lines += initLog
                        // v0.78.1: 01A6 (kilometraje total), 0131 (km desde que se borraron los códigos), 01A0 (PID admitidos A1–C0)
                        for (cmd in listOf("0100", "0101", "0105", "010B", "010C", "010D", "010F", "0110", "012F", "0142", "0131", "01A0", "01A6", "03", "07", "ATRV", "ATDP")) {
                            lines += "$cmd → " + link.send(cmd).replace("\r", " ").ifBlank { "(sin respuesta)" }
                        }
                        ObdState.probe.value = lines
                    }
                    // Borrar códigos (pedido desde la ventana de códigos, con el auto parado)
                    if (ObdState.clearRequested) {
                        ObdState.clearRequested = false
                        val ok = reader.clearCodes()
                        ObdState.clearResult.value = if (ok) "Códigos borrados" else "El auto no aceptó el borrado"
                        AppLog.info("OBD2: borrado de códigos " + if (ok) "aceptado" else "rechazado")
                        codesAt = 0L
                    }
                    // Códigos de falla: al conectar, cada 5 min o cuando se piden
                    val nowMs = System.currentTimeMillis()
                    if (ObdState.codesRequested || codesAt == 0L || nowMs - codesAt > CODES_EVERY_MS) {
                        ObdState.codesRequested = false
                        val codes = reader.readCodes(nowMs)
                        if (codes.total != (ObdState.codes.value?.total ?: 0)) {
                            AppLog.info("OBD2: códigos " + (codes.stored + codes.pending).joinToString().ifEmpty { "ninguno" })
                        }
                        ObdState.codes.value = codes
                        codesAt = nowMs
                    }
                    val d = reader.poll()
                    // v0.81.13: una lectura vacía no corta el Bluetooth; recién a las 3 seguidas se reconecta
                    silent = ObdWatch.silent(silent, d.coolantC != null || d.rpm != null || d.speedKmh != null || d.volts != null)
                    if (ObdWatch.mustReconnect(silent)) throw IllegalStateException("el lector no responde")
                    ObdState.data.value = d
                    ObdState.alert.value = watch.update(EngineAlerts.of(d), System.currentTimeMillis())
                    misses = ObdWatch.misses(misses, d.coolantC != null || d.rpm != null || d.speedKmh != null || d.fuelPct != null)
                    if (ObdWatch.mustReinit(misses)) {
                        // v0.81.13: el auto no contesta pero el lector sí: se reinicia el lector por la
                        // misma conexión (como Torque), sin cortar el Bluetooth
                        AppLog.info("OBD2: el auto no contesta (solo el lector); reinicio el lector sin cortar el Bluetooth")
                        opened.send("ATZ", 5_000)
                        if (!opened.answered) throw java.io.IOException("el lector no contestó al reinicio")
                        initElm(opened)
                        misses = 0
                    }
                    // Con el auto sin contestar (contacto sin motor, por ejemplo) se pregunta más espaciado
                    delay(if (misses > 0) 3_000 else 700)
                }
            } catch (e: Exception) {
                // AUD-072: cerrar la pantalla no es una falla del lector; el bucle termina acá
                if (!ObdRecovery.isReaderFailure(e)) throw e
                failures++
                retryMs = ObdRecovery.retryDelayMs(failures)
                AppLog.warn("OBD2: se cortó la conexión (falla $failures)", e)
                // v0.81.14: el lector atiende a un solo equipo; un celular que lo tenga tomado deja afuera a la radio
                ObdState.status.value = "sin conexión (lector apagado o en uso por otro equipo); reintenta en ${ObdRecovery.waitText(retryMs)}"
                ObdState.data.value = null
                ObdState.alert.value = null
            } finally {
                guard?.cancel()
                activeLink.compareAndSet(link, null)
                link?.close()
            }
            waitOrWake(retryMs)
        }
    }
}

/**
 * Conexión con el lector mientras la app está abierta: busca, conecta, lee y reconecta.
 * Pide el permiso de Bluetooth la primera vez (Android 12+). Devuelve los datos actuales o null.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberObd(): ObdData? {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted && Build.VERSION.SDK_INT >= 31) {
            delay(1500)   // después del pedido de ubicación
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(granted) {
        if (granted) CarEngine.startObd(context) else ObdState.status.value = "sin permiso de Bluetooth"
    }
    return ObdState.data.value
}
