package com.axel.onixlauncher

import java.util.Locale

/*
 * OBD2 avanzado (v0.50.0): códigos de falla, luz del motor, alertas y consumo estimado.
 *
 * Todo lo de este archivo son funciones puras (sin Bluetooth ni interfaz) para poder probarlas
 * en la CI con el lector simulado de los tests (FakeElm327). La idea de sumar códigos, alertas y
 * consumo viene de LiteDash (GPL-3.0, Irving Guerra); el código es propio. Los formatos y las
 * fórmulas son del estándar OBD2 (SAE J1979 / J2012) y de física básica.
 */

/** Códigos de falla leídos del auto. [mil] = luz del motor prendida (PID 01 01). */
class ObdCodes(
    val mil: Boolean?,
    /** Guardados (modo 03): los que prenden la luz. */
    val stored: List<String>,
    /** Pendientes (modo 07): vistos una vez, todavía no confirmados. */
    val pending: List<String>,
    /** Cuándo se leyeron (ms). */
    val readAt: Long
) {
    val total: Int get() = (stored + pending).distinct().size
}

/** Lectura de los códigos de falla (funciones puras, para los tests). */
object Dtc {
    private val FRAME = Regex("^([0-9A-F]):([0-9A-F]*)$")
    private val HEX = Regex("^[0-9A-F]*$")

    /**
     * Códigos de una respuesta del modo [mode] (3 = guardados, 7 = pendientes).
     * Entiende los dos formatos del ELM327:
     *  - CAN (el del Onix): "43 02 01 33 04 20" → la cantidad y después pares de bytes; si no
     *    entra en una línea, viene en varias numeradas ("0:", "1:"…) con una línea de largo antes.
     *  - Protocolos viejos: "43 01 33 00 00 00 00" → siempre tres pares por línea, ceros de relleno.
     * Devuelve lista vacía si el auto contesta que no hay, y null si no hubo respuesta válida.
     */
    fun parse(raw: String, mode: Int): List<String>? {
        val tag = String.format(Locale.US, "%02X", 0x40 + mode)
        val clean = raw.uppercase(Locale.US).replace("SEARCHING...", "").replace(">", "")
        val messages = mutableListOf<StringBuilder>()
        for (rawLine in clean.split('\r', '\n')) {
            val line = rawLine.replace(" ", "").trim()
            if (line.isEmpty()) continue
            if (line.length == 3 && HEX.matches(line)) continue          // largo del mensaje CAN largo
            val frame = FRAME.matchEntire(line)
            when {
                frame != null && frame.groupValues[1] == "0" -> messages += StringBuilder(frame.groupValues[2])
                frame != null -> messages.lastOrNull()?.append(frame.groupValues[2])
                line.startsWith(tag) && HEX.matches(line) -> messages += StringBuilder(line)
            }
        }
        val codes = mutableListOf<String>()
        var answered = false
        for (msg in messages) {
            if (!msg.startsWith(tag)) continue
            val body = msg.substring(2)
            if (body.length % 2 != 0) continue
            answered = true
            val bytes = IntArray(body.length / 2) { body.substring(it * 2, it * 2 + 2).toInt(16) }
            // Cantidad impar de bytes: CAN, el primero es cuántos códigos hay.
            val pairs = if (bytes.size % 2 == 1) {
                val n = bytes[0]
                (0 until n).mapNotNull { i -> if (2 + i * 2 < bytes.size) bytes[1 + i * 2] to bytes[2 + i * 2] else null }
            } else {
                (0 until bytes.size / 2).map { i -> bytes[i * 2] to bytes[i * 2 + 1] }
            }
            for ((a, b) in pairs) if (a != 0 || b != 0) codes += decode(a, b)
        }
        if (answered) return codes.distinct()
        return if ("NODATA" in clean.replace(" ", "")) emptyList() else null
    }

    /** Dos bytes → código: los 2 primeros bits son la letra (P, C, B, U). 0x01 0x33 → "P0133". */
    fun decode(a: Int, b: Int): String =
        "${"PCBU"[a shr 6]}${(a shr 4) and 3}" + String.format(Locale.US, "%X%02X", a and 0xF, b)

    /** Luz del motor (bit 7 del primer byte del PID 01 01); null si no contestó. */
    fun mil(raw: String): Boolean? = ObdParse.bytes(raw, 0x01)?.let { it[0] and 0x80 != 0 }

    /** Si el borrado (modo 04) fue aceptado: el auto contesta "44". */
    fun cleared(raw: String): Boolean =
        raw.uppercase(Locale.US).split('\r', '\n').any { it.replace(" ", "").trim().startsWith("44") }

    /** Qué significa un código, en castellano. Los genéricos más comunes; del resto, el sistema. */
    fun describe(code: String): String {
        COMMON[code]?.let { return it }
        Regex("P030([1-8])").matchEntire(code)?.let { return "Falla de encendido en el cilindro ${it.groupValues[1]}" }
        val maker = code.getOrNull(1) == '1'
        val system = when (code.firstOrNull()) {
            'B' -> "Carrocería"
            'C' -> "Chasis (frenos, ABS, dirección)"
            'U' -> "Comunicación entre módulos"
            else -> when (code.getOrNull(2)) {
                '0', '1', '2' -> "Mezcla de aire y nafta"
                '3' -> "Encendido o falla de cilindros"
                '4' -> "Control de emisiones"
                '5' -> "Velocidad, ralentí o entradas auxiliares"
                '6' -> "Computadora del motor o salidas"
                '7', '8', '9' -> "Caja de cambios"
                else -> "Motor"
            }
        }
        return if (maker) "$system (código de la marca)" else system
    }

    /** Descripciones de los códigos genéricos (SAE J2012) que más aparecen. */
    private val COMMON = mapOf(
        "P0010" to "Actuador del árbol de levas (circuito, banco 1)",
        "P0011" to "Árbol de levas demasiado adelantado (banco 1)",
        "P0016" to "Cigüeñal y árbol de levas no coinciden (distribución)",
        "P0030" to "Calefactor de la sonda lambda 1 (circuito)",
        "P0101" to "Sensor de flujo de aire (MAF): rango o funcionamiento",
        "P0102" to "Sensor de flujo de aire (MAF): señal baja",
        "P0105" to "Sensor de presión de admisión (MAP): circuito",
        "P0106" to "Sensor de presión de admisión (MAP): rango o funcionamiento",
        "P0107" to "Sensor de presión de admisión (MAP): señal baja",
        "P0108" to "Sensor de presión de admisión (MAP): señal alta",
        "P0112" to "Sensor de temperatura del aire de admisión: señal baja",
        "P0113" to "Sensor de temperatura del aire de admisión: señal alta",
        "P0117" to "Sensor de temperatura del motor: señal baja",
        "P0118" to "Sensor de temperatura del motor: señal alta",
        "P0121" to "Sensor de posición del acelerador: rango o funcionamiento",
        "P0122" to "Sensor de posición del acelerador: señal baja",
        "P0128" to "El motor tarda en calentar (termostato)",
        "P0130" to "Sonda lambda 1 (antes del catalizador): circuito",
        "P0131" to "Sonda lambda 1: señal baja",
        "P0133" to "Sonda lambda 1: respuesta lenta",
        "P0134" to "Sonda lambda 1: sin actividad",
        "P0135" to "Calefactor de la sonda lambda 1",
        "P0136" to "Sonda lambda 2 (después del catalizador): circuito",
        "P0141" to "Calefactor de la sonda lambda 2",
        "P0171" to "Mezcla pobre (banco 1): entra aire o falta nafta",
        "P0172" to "Mezcla rica (banco 1)",
        "P0201" to "Inyector del cilindro 1: circuito",
        "P0202" to "Inyector del cilindro 2: circuito",
        "P0203" to "Inyector del cilindro 3: circuito",
        "P0204" to "Inyector del cilindro 4: circuito",
        "P0230" to "Bomba de nafta: circuito",
        "P0300" to "Fallas de encendido en varios cilindros",
        "P0325" to "Sensor de detonación: circuito",
        "P0335" to "Sensor de posición del cigüeñal: circuito",
        "P0336" to "Sensor de posición del cigüeñal: rango o funcionamiento",
        "P0340" to "Sensor de posición del árbol de levas: circuito",
        "P0351" to "Bobina de encendido A: circuito",
        "P0352" to "Bobina de encendido B: circuito",
        "P0353" to "Bobina de encendido C: circuito",
        "P0354" to "Bobina de encendido D: circuito",
        "P0401" to "Recirculación de gases (EGR): flujo insuficiente",
        "P0420" to "Catalizador con bajo rendimiento (banco 1)",
        "P0440" to "Sistema de vapores de nafta (EVAP)",
        "P0442" to "Pérdida chica en el sistema de vapores (EVAP)",
        "P0443" to "Válvula de purga del canister: circuito",
        "P0455" to "Pérdida grande en el sistema de vapores: revisá la tapa del tanque",
        "P0457" to "Tapa del tanque floja o sin poner",
        "P0480" to "Electroventilador 1: circuito",
        "P0500" to "Sensor de velocidad del vehículo",
        "P0505" to "Control de ralentí",
        "P0506" to "Ralentí más bajo de lo esperado",
        "P0507" to "Ralentí más alto de lo esperado (posible entrada de aire)",
        "P0560" to "Voltaje del sistema",
        "P0562" to "Voltaje del sistema bajo",
        "P0563" to "Voltaje del sistema alto",
        "P0601" to "Memoria de la computadora del motor",
        "P0606" to "Procesador de la computadora del motor",
        "P0627" to "Bomba de nafta: circuito de control",
        "P0700" to "Falla en la caja de cambios (leer su módulo)",
        "P2135" to "Sensores del acelerador no coinciden",
        "U0100" to "Sin comunicación con la computadora del motor",
        "U0121" to "Sin comunicación con el módulo del ABS",
        "U0140" to "Sin comunicación con el módulo de carrocería (BCM)"
    )
}

/** Algo del motor que hay que saber ya. [holdMs] = cuánto tiene que durar para avisar (evita falsas alarmas). */
enum class EngineAlert(val title: String, val advice: String, val holdMs: Long) {
    HOT("Motor recalentado", "Pará en un lugar seguro y apagá el motor", 8_000),
    NOT_CHARGING("La batería no carga", "Revisá el alternador o la correa", 60_000),
    OVERCHARGING("Voltaje demasiado alto", "Revisá el regulador del alternador", 60_000),
    LOW_BATTERY("Batería baja", "Puede costar arrancar el auto", 60_000)
}

/** Umbrales y cálculo de las alertas (funciones puras, para los tests). */
object EngineAlerts {
    /** Normal: 85–105 °C. Desde 110 °C, recalentado. */
    const val HOT_C = 110
    /** Motor en marcha desde estas rpm (en ralentí anda en 700–900). */
    const val RUNNING_RPM = 400
    /** El alternador da 13,5–14,7 V; abajo de 12,3 V con el motor andando no está cargando. */
    const val CHARGING_MIN_V = 12.3f
    const val CHARGING_MAX_V = 15.3f
    /** En reposo, 12,6 V es batería llena; abajo de 11,9 V cuesta arrancar. */
    const val RESTING_MIN_V = 11.9f

    /** La alerta más importante con los datos actuales; null = todo bien o sin datos. */
    fun of(d: ObdData?): EngineAlert? {
        if (d == null) return null
        d.coolantC?.let { if (it >= HOT_C) return EngineAlert.HOT }
        val v = d.volts ?: return null
        val rpm = d.rpm ?: return null       // sin rpm no se sabe si el motor anda
        if (v < 5f) return null              // lectura absurda
        return when {
            rpm > RUNNING_RPM && v < CHARGING_MIN_V -> EngineAlert.NOT_CHARGING
            rpm > RUNNING_RPM && v > CHARGING_MAX_V -> EngineAlert.OVERCHARGING
            rpm <= RUNNING_RPM && v < RESTING_MIN_V -> EngineAlert.LOW_BATTERY
            else -> null
        }
    }
}

/** Avisa solo si la misma alerta dura [EngineAlert.holdMs] seguidos; se borra apenas se normaliza. */
class AlertWatch {
    private var candidate: EngineAlert? = null
    private var since = 0L

    fun update(now: EngineAlert?, nowMs: Long): EngineAlert? {
        if (now != candidate) {
            candidate = now
            since = nowMs
        }
        val c = candidate ?: return null
        return if (nowMs - since >= c.holdMs) c else null
    }
}

/**
 * Consumo estimado en km/l. El Onix Joy (motor 1.0 SPE/4) no tiene sensor de flujo de aire
 * (MAF): se calcula con la presión de admisión (MAP), la temperatura del aire y las rpm
 * ("densidad de aire"), y con el MAF si el auto lo informa. Es una estimación: sirve para
 * comparar (ciudad / ruta, manejo suave / brusco), no como dato exacto.
 */
object Consumption {
    /** Cilindrada del motor 1.0 del Onix Joy, en litros. */
    const val DISPLACEMENT_L = 0.999f
    /** Eficiencia volumétrica típica de un motor atmosférico. */
    const val VOLUMETRIC_EFFICIENCY = 0.85f
    /** Relación aire/nafta estequiométrica de la nafta argentina (con ~12 % de bioetanol). */
    const val AIR_FUEL = 14.1f
    /** Gramos por litro de nafta. */
    const val FUEL_G_PER_L = 750f
    private const val AIR_G_PER_MOL = 28.97f
    private const val R = 8.314f

    /** Aire que entra al motor (g/s) con MAP (kPa), temperatura del aire (°C) y rpm. */
    fun airFromMap(mapKpa: Float, intakeC: Float, rpm: Float): Float {
        val mols = mapKpa * DISPLACEMENT_L * VOLUMETRIC_EFFICIENCY / (R * (intakeC + 273.15f))
        return rpm / 120f * mols * AIR_G_PER_MOL           // 4 tiempos: una admisión cada 2 vueltas
    }

    /** km/l con el aire (g/s) y la velocidad; null parado o sin aire. */
    fun kmPerLiter(airGs: Float, speedKmh: Float): Float? {
        if (speedKmh < 5f || airGs <= 0f) return null
        val litersPerHour = airGs * 3600f / (AIR_FUEL * FUEL_G_PER_L)
        return speedKmh / litersPerHour
    }

    /** Litros por hora con el aire (g/s). */
    fun litersPerHour(airGs: Float): Float = airGs * 3600f / (AIR_FUEL * FUEL_G_PER_L)

    /** Suaviza el valor (el consumo instantáneo salta mucho al acelerar y soltar). */
    fun smooth(previous: Float?, now: Float?, k: Float = 0.2f): Float? = when {
        now == null -> previous
        previous == null -> now
        else -> previous + (now - previous) * k
    }
}

/** Lectura de las respuestas de los datos de consumo (funciones puras). */
object ObdParseExtra {
    /** Flujo de aire, PID 01 10: (A·256 + B) / 100 g/s. */
    fun maf(raw: String): Float? = ObdParse.bytes(raw, 0x10)?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 100f }

    /** Presión de admisión, PID 01 0B: A kPa. */
    fun map(raw: String): Int? = ObdParse.bytes(raw, 0x0B)?.let { it[0] }

    /** Temperatura del aire de admisión, PID 01 0F: A − 40 °C. */
    fun intake(raw: String): Int? = ObdParse.bytes(raw, 0x0F)?.let { it[0] - 40 }
}

/**
 * Lo que se le pide al lector en cada vuelta y cuando hay que leer o borrar códigos. Recibe la
 * función que manda un comando y devuelve la respuesta, así en los tests se usa un lector simulado.
 */
class ObdReader(private val send: (String) -> String) {
    /** null = todavía no se sabe si el auto informa MAF. */
    var hasMaf: Boolean? = null
        private set
    private var tick = 0
    private var intakeC: Int? = null
    private var kml: Float? = null

    /** Una vuelta de datos del motor. */
    fun poll(): ObdData {
        val coolant = ObdParse.coolant(send("0105"))
        val rpm = ObdParse.rpm(send("010C"))
        val speed = ObdParse.speed(send("010D"))
        val fuel = ObdParse.fuel(send("012F"))
        val volts = ObdParse.volts(send("ATRV"))
        // Aire: MAF si lo tiene; si no, MAP + temperatura del aire (esta cambia lento: cada 10 vueltas).
        var air: Float? = null
        if (hasMaf != false && rpm != null) {
            val maf = ObdParseExtra.maf(send("0110"))
            if (hasMaf == null) hasMaf = maf != null
            air = maf
        }
        if (hasMaf == false && rpm != null) {
            if (intakeC == null || tick % 10 == 0) ObdParseExtra.intake(send("010F"))?.let { intakeC = it }
            val map = ObdParseExtra.map(send("010B"))
            if (map != null) air = Consumption.airFromMap(map.toFloat(), (intakeC ?: 25).toFloat(), rpm.toFloat())
        }
        tick++
        val now = if (air != null && speed != null) Consumption.kmPerLiter(air, speed.toFloat()) else null
        // Parado no hay km/l: se olvida el promedio para no arrastrar uno viejo.
        kml = if (speed != null && speed < 5) null else Consumption.smooth(kml, now)
        return ObdData(coolant, fuel, volts, rpm, speed, kml, air?.let { Consumption.litersPerHour(it) })
    }

    /** Luz del motor y códigos guardados y pendientes. */
    fun readCodes(nowMs: Long): ObdCodes = ObdCodes(
        mil = Dtc.mil(send("0101")),
        stored = Dtc.parse(send("03"), 3).orEmpty(),
        pending = Dtc.parse(send("07"), 7).orEmpty(),
        readAt = nowMs
    )

    /** Borra los códigos y apaga la luz (modo 04). Devuelve si el auto lo aceptó. */
    fun clearCodes(): Boolean = Dtc.cleared(send("04"))
}

/** Sistemas del auto para la "Salud del auto" (v0.64.0). */
enum class CarSystem(val label: String) {
    ENGINE("Motor y encendido"),
    AIR_FUEL("Aire y combustible"),
    COOLING("Enfriamiento"),
    BATTERY("Batería y carga"),
    GEARBOX("Caja"),
    EMISSIONS("Escape y emisiones"),
    BRAKES("Frenos y ruedas"),
    BODY("Carrocería y red")
}

enum class HealthLevel { GOOD, WARN, FAULT, UNKNOWN }

/**
 * Salud del auto por sistema (v0.64.0). Idea del Taller de LiteDash 1.4.53 (GPL-3.0); código
 * propio, con los grupos de códigos de la norma SAE J2012 (tercer carácter del código P).
 */
object CarHealth {
    private val COOLING = setOf("P0115", "P0116", "P0117", "P0118", "P0119", "P0125", "P0128", "P0217", "P0480", "P0481")
    private val BATTERY = setOf("P0560", "P0561", "P0562", "P0563", "P0620", "P0621", "P0622")

    /** A qué sistema apunta un código de falla. */
    fun systemOf(code: String): CarSystem = when {
        code in COOLING -> CarSystem.COOLING
        code in BATTERY -> CarSystem.BATTERY
        code.startsWith('C') -> CarSystem.BRAKES
        code.startsWith('B') || code.startsWith('U') -> CarSystem.BODY
        else -> when (code.getOrNull(2)) {
            '0', '1', '2' -> CarSystem.AIR_FUEL      // medición de aire y combustible
            '3' -> CarSystem.ENGINE                  // encendido y fallas de cilindro
            '4' -> CarSystem.EMISSIONS               // catalizador, EGR, EVAP, sondas
            '7', '8', '9' -> CarSystem.GEARBOX
            'A' -> CarSystem.BATTERY                 // híbridos
            else -> CarSystem.ENGINE                 // ralentí, computadora y otros
        }
    }

    /** Una línea para la Prueba completa (AUD-065, v0.67.1): solo los sistemas con algo. */
    fun summary(health: List<Pair<CarSystem, HealthLevel>>): String {
        if (health.all { it.second == HealthLevel.UNKNOWN }) return "sin dato (no se leyeron los códigos)"
        val bad = health.filter { it.second == HealthLevel.FAULT || it.second == HealthLevel.WARN }
            .joinToString(" · ") { (s, l) -> s.label + ": " + if (l == HealthLevel.FAULT) "falla" else "atención" }
        return if (bad.isEmpty()) "todo bien" else "$bad · el resto bien"
    }

    /**
     * Estado de cada sistema: guardado → falla; pendiente → atención; recalentado → falla en
     * enfriamiento; alertas de batería → atención; luz prendida sin códigos → atención en el motor.
     * Sin códigos leídos ni alertas, todo "sin dato".
     */
    fun of(codes: ObdCodes?, alert: EngineAlert?): List<Pair<CarSystem, HealthLevel>> = CarSystem.entries.map { s ->
        val stored = codes?.stored.orEmpty().any { systemOf(it) == s }
        val pending = codes?.pending.orEmpty().any { systemOf(it) == s }
        val alertHere = when (alert) {
            null -> null
            EngineAlert.HOT -> CarSystem.COOLING
            else -> CarSystem.BATTERY
        } == s
        val level = when {
            stored || (alertHere && alert == EngineAlert.HOT) -> HealthLevel.FAULT
            pending || alertHere -> HealthLevel.WARN
            s == CarSystem.ENGINE && codes?.mil == true && codes.stored.isEmpty() -> HealthLevel.WARN
            codes != null -> HealthLevel.GOOD
            else -> HealthLevel.UNKNOWN
        }
        s to level
    }
}
