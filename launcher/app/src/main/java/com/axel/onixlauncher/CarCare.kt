package com.axel.onixlauncher

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Mi auto (v0.52.0; rehecho en v0.79.0 con el autor): tres pestañas.
 *
 * - Mantenimiento: cada ítem con "lo hice a los ___ km" (y la fecha), "cada ___ km" y/o "cada ___
 *   meses"; dice cuánto falta con colores y avisa en Inicio una vez por día. Hecho pide el km.
 * - Viajes: se arman solos con la velocidad (OBD2 o GPS) y terminan al apagar el auto (aviso de
 *   contacto de la radio NWD) o a los 5 min parado. Litros por el consumo del OBD2 o, si el lector no
 *   lo da, por lo que bajó el tanque (54 L). Últimos 30 y resumen por mes.
 * - Motor: datos en vivo, luz del motor, códigos y salud por sistema; borrar códigos con el auto
 *   encendido, también andando (como Torque).
 * Kilometraje: el que informa el auto por OBD2 (si lo informa) o el que se carga, más los km que Onix ve.
 * Idea tomada de LiteDash (GPL-3.0) y del cuentakilómetros de aceite de CleanLauncher; código propio.
 */

/** Un viaje terminado (km, km/h, °C, litros). */
class TripSummary(
    val start: Long,
    val seconds: Int,
    val km: Float,
    val maxKmh: Int,
    /** Velocidad promedio en movimiento (sin contar lo parado). */
    val avgKmh: Int,
    val maxCoolant: Int?,
    /** Litros gastados (con el consumo del OBD2); null sin OBD2. */
    val liters: Float?,
    /** v0.79.0: litros por lo que bajó el nivel del tanque (54 L), si el lector no da el consumo. */
    val levelLiters: Float? = null
) {
    /** Los litros que se muestran: los del consumo si están; si no, los del tanque. */
    val shownLiters: Float? get() = liters ?: levelLiters
    val fromLevel: Boolean get() = liters == null && levelLiters != null
    val kmPerLiter: Float? get() = shownLiters?.takeIf { it > 0.05f }?.let { km / it }

    fun toJson(): JSONObject = JSONObject().put("inicio", start).put("seg", seconds).put("km", km.toDouble())
        .put("max", maxKmh).put("prom", avgKmh).put("temp", maxCoolant ?: JSONObject.NULL)
        .put("litros", liters?.toDouble() ?: JSONObject.NULL)
        .put("nivel", levelLiters?.toDouble() ?: JSONObject.NULL)

    /** "14,0 km · 22 min · 1,1 l" (con "≈" si los litros salen del tanque). */
    fun short(): String = listOfNotNull(
        String.format(Locale("es", "AR"), "%.1f km", km), "${seconds / 60} min",
        shownLiters?.let { String.format(Locale("es", "AR"), (if (fromLevel) "≈ " else "") + "%.1f l", it) }
    ).joinToString(" · ")

    companion object {
        fun fromJson(o: JSONObject) = TripSummary(
            o.optLong("inicio"), o.optInt("seg"), o.optDouble("km").toFloat(), o.optInt("max"), o.optInt("prom"),
            if (o.isNull("temp")) null else o.optInt("temp"),
            if (o.isNull("litros")) null else o.optDouble("litros").toFloat(),
            if (!o.has("nivel") || o.isNull("nivel")) null else o.optDouble("nivel").toFloat()
        )
    }
}

/** Arma los viajes con la velocidad de cada segundo (pura, para los tests). */
class TripTracker {
    private var start = -1L
    private var lastTick = -1L
    private var lastMoving = -1L
    private var movingSeconds = 0f
    private var maxKmh = 0
    private var maxCoolant: Int? = null
    private var litersUsed = 0f
    private var litersKnown = false
    private var fuelStart: Int? = null
    private var fuelLast: Int? = null

    /** km del viaje en curso (0 si no hay viaje). */
    var km = 0f
        private set
    val active: Boolean get() = start >= 0

    /**
     * Una lectura. Devuelve el viaje si terminó con esta lectura. Los huecos de más de 5 s (la app
     * no corrió) no suman km, para no inventar distancia.
     */
    fun tick(nowMs: Long, speedKmh: Int?, litersPerHour: Float?, coolantC: Int?, fuelPct: Int? = null): TripSummary? {
        val dt = if (lastTick < 0) 0f else ((nowMs - lastTick) / 1000f).coerceIn(0f, 5f)
        lastTick = nowMs
        val moving = speedKmh != null && speedKmh >= MOVING_KMH
        if (!active) {
            // AUD-038 (v0.59.0): para arrancar un viaje hace falta más velocidad que para seguirlo;
            // el ruido del GPS con el auto estacionado (5–8 km/h) inventaba viajes y km
            if (speedKmh == null || speedKmh < START_KMH) return null
            start = nowMs
            lastMoving = nowMs
        }
        if (speedKmh != null) km += speedKmh * dt / 3600f
        if (moving) {
            lastMoving = nowMs
            movingSeconds += dt
            maxKmh = max(maxKmh, speedKmh!!)
        }
        if (litersPerHour != null) {
            litersUsed += litersPerHour * dt / 3600f
            litersKnown = true
        }
        if (coolantC != null) maxCoolant = max(maxCoolant ?: coolantC, coolantC)
        if (fuelPct != null) { if (fuelStart == null) fuelStart = fuelPct; fuelLast = fuelPct }
        return if (nowMs - lastMoving >= END_AFTER_MS) finish() else null
    }

    /** Cierra el viaje en curso. Null si fue muy corto (maniobras). */
    fun finish(): TripSummary? {
        if (!active) return null
        val trip = TripSummary(
            start, ((lastMoving - start) / 1000).toInt(), km, maxKmh,
            if (movingSeconds > 0f) (km / (movingSeconds / 3600f)).roundToInt() else 0,
            maxCoolant, if (litersKnown) litersUsed else null,
            levelLiters(fuelStart, fuelLast)
        ).takeIf { it.km >= MIN_KM }
        start = -1L; lastMoving = -1L; movingSeconds = 0f; maxKmh = 0; maxCoolant = null
        litersUsed = 0f; litersKnown = false; km = 0f; fuelStart = null; fuelLast = null
        return trip
    }

    companion object {
        /** Tanque del Onix del autor (54 L, confirmado por él). */
        const val TANK_LITERS = 54f
        /** Litros por lo que bajó el tanque; null si no bajó (o subió: se cargó nafta). */
        fun levelLiters(startPct: Int?, endPct: Int?): Float? {
            if (startPct == null || endPct == null || endPct >= startPct) return null
            return (startPct - endPct) * TANK_LITERS / 100f
        }
        const val MOVING_KMH = 5
        const val START_KMH = 10
        const val END_AFTER_MS = 5 * 60_000L
        const val MIN_KM = 0.3f
    }
}

/**
 * Un ítem de mantenimiento (v0.79.0): cada cuánto se hace (km y/o meses) y cuándo se hizo por última
 * vez (km del auto y fecha, en días desde 1970). Lo que no se completó queda en null.
 */
data class MaintItem(
    val id: Long,
    val name: String,
    val everyKm: Int?,
    val everyMonths: Int?,
    val lastKm: Int?,
    val lastDay: Long?
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("nombre", name)
        .put("cada_km", everyKm ?: JSONObject.NULL).put("cada_meses", everyMonths ?: JSONObject.NULL)
        .put("km", lastKm ?: JSONObject.NULL).put("dia", lastDay ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): MaintItem? {
            val name = o.optString("nombre").ifBlank { return null }
            fun int(k: String) = if (!o.has(k) || o.isNull(k)) null else o.optInt(k)
            return MaintItem(o.optLong("id"), name, int("cada_km"), int("cada_meses"), int("km"),
                if (!o.has("dia") || o.isNull("dia")) null else o.optLong("dia"))
        }
    }
}

enum class Urgency { UNSET, OK, SOON, DUE }

/** Cuánto falta para cada ítem (funciones puras, para los tests). */
object Maintenance {
    const val SOON_KM = 1_000
    const val SOON_DAYS = 30

    /** La lista de arranque, como la pidió el autor (todo editable). */
    fun defaults(): List<MaintItem> = listOf(
        "Aceite y filtro de aceite" to 10_000, "Filtro de aire" to 10_000, "Filtro de habitáculo" to 10_000,
        "Filtro de combustible" to 10_000, "Alineación y balanceo (con rotación)" to 10_000, "Neumáticos" to 60_000,
        "Pastillas de freno" to null, "Líquido de frenos" to null, "Bujías" to null, "Correa de distribución" to null
    ).mapIndexed { i, (n, km) -> MaintItem(i + 1L, n, km, null, null, null) } + listOf(
        MaintItem(20, "Batería", null, null, null, null),
        MaintItem(21, "VTV", null, 12, null, null)
    )

    fun dueKm(m: MaintItem): Int? = if (m.everyKm != null && m.lastKm != null) m.lastKm + m.everyKm else null
    fun dueDay(m: MaintItem): Long? = if (m.everyMonths != null && m.lastDay != null) Reminders.addMonths(m.lastDay, m.everyMonths) else null

    private fun kmUrgency(m: MaintItem, carKm: Int?): Urgency? {
        val due = dueKm(m) ?: return null
        val left = due - (carKm ?: return null)
        return when { left <= 0 -> Urgency.DUE; left <= SOON_KM -> Urgency.SOON; else -> Urgency.OK }
    }

    private fun dayUrgency(m: MaintItem, today: Long): Urgency? {
        val left = (dueDay(m) ?: return null) - today
        return when { left <= 0 -> Urgency.DUE; left <= SOON_DAYS -> Urgency.SOON; else -> Urgency.OK }
    }

    /** Lo primero que llegue (km o fecha); UNSET si falta completar el ítem. */
    fun urgency(m: MaintItem, carKm: Int?, today: Long): Urgency {
        val k = kmUrgency(m, carKm)
        val d = dayUrgency(m, today)
        if (k == null && d == null) return if (dueKm(m) != null) Urgency.OK else Urgency.UNSET
        return listOfNotNull(k, d).maxBy { it.ordinal }
    }

    /** "Faltan 3.200 km", "Pasado por 300 km", "Faltan 40 días", "A los 90.000 km" o qué falta completar. */
    fun describe(m: MaintItem, carKm: Int?, today: Long): String {
        if (m.everyKm == null && m.everyMonths == null) return "Completá cada cuánto se hace"
        val kmText = dueKm(m)?.let { due ->
            if (carKm == null) "A los ${Reminders.km(due)} km" else {
                val left = due - carKm
                if (left >= 0) "Faltan ${Reminders.km(left)} km" else "Pasado por ${Reminders.km(-left)} km"
            }
        }
        val dayText = dueDay(m)?.let { due ->
            val left = due - today
            when {
                left > 1 -> "Faltan $left días"
                left == 1L -> "Mañana"
                left == 0L -> "Hoy"
                else -> "Venció hace ${-left} días"
            }
        }
        if (kmText == null && dayText == null) {
            return if (m.everyKm != null) "Completá a qué km lo hiciste" else "Completá cuándo lo hiciste"
        }
        // Se muestra lo más urgente de los dos
        val k = kmUrgency(m, carKm)
        val d = dayUrgency(m, today)
        return if (dayText != null && (kmText == null || (d != null && k != null && d.ordinal > k.ordinal))) dayText else kmText!!
    }

    /** Hecho a los [km] (y hoy). */
    fun done(m: MaintItem, km: Int?, today: Long): MaintItem = m.copy(lastKm = km ?: m.lastKm, lastDay = today)
}

/** Totales por mes de los viajes (v0.79.0). [kmWithLiters] = km de los viajes con litros, para los km/l. */
data class MonthTotal(val km: Float, val liters: Float, val kmWithLiters: Float)

object Monthly {
    private val MONTHS = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
        "Septiembre", "Octubre", "Noviembre", "Diciembre")

    fun key(ms: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return String.format(Locale.US, "%d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun add(map: Map<String, MonthTotal>, t: TripSummary, tz: TimeZone = TimeZone.getDefault()): Map<String, MonthTotal> {
        val k = key(t.start, tz)
        val old = map[k] ?: MonthTotal(0f, 0f, 0f)
        val l = t.shownLiters
        return map + (k to MonthTotal(old.km + t.km, old.liters + (l ?: 0f), old.kmWithLiters + if (l != null) t.km else 0f))
    }

    /** "Noviembre 2025: 30 km · 2,0 l · 15,0 km/l". */
    fun label(key: String, m: MonthTotal): String {
        val (y, mo) = key.split("-").let { it[0] to it[1].toInt() }
        val ar = Locale("es", "AR")
        return "${MONTHS[mo - 1]} $y: " + listOfNotNull(
            String.format(ar, "%.0f km", m.km),
            m.liters.takeIf { it > 0f }?.let { String.format(ar, "%.1f l", it) },
            m.liters.takeIf { it > 0.05f }?.let { String.format(ar, "%.1f km/l", m.kmWithLiters / it) }
        ).joinToString(" · ")
    }

    fun toJson(map: Map<String, MonthTotal>): String = JSONObject().apply {
        map.forEach { (k, v) -> put(k, JSONObject().put("km", v.km.toDouble()).put("l", v.liters.toDouble()).put("kml", v.kmWithLiters.toDouble())) }
    }.toString()

    fun fromJson(raw: String?): Map<String, MonthTotal> = try {
        parse(raw ?: "{}")
    } catch (e: Exception) {
        emptyMap()
    }

    /** Como [fromJson], pero tira una excepción si el texto está dañado (AUD-101: para respaldarlo). */
    fun parse(raw: String): Map<String, MonthTotal> {
        val o = JSONObject(raw)
        return o.keys().asSequence().associateWith { k ->
            o.getJSONObject(k).let { MonthTotal(it.optDouble("km").toFloat(), it.optDouble("l").toFloat(), it.optDouble("kml").toFloat()) }
        }
    }
}

/** Fechas y números de Mi auto (funciones puras, para los tests). */
object Reminders {
    private const val DAY_MS = 86_400_000L

    /** Hoy en días desde 1970, en la hora local. */
    fun today(nowMs: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long =
        (nowMs + tz.getOffset(nowMs)) / DAY_MS

    /** [day] + [months] meses (respeta el largo de cada mes). */
    fun addMonths(day: Long, months: Int): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * DAY_MS }
        c.add(Calendar.MONTH, months)
        return c.timeInMillis / DAY_MS
    }

    /** "12.345" con punto de miles. */
    fun km(value: Int): String = String.format(Locale.GERMANY, "%,d", value)

    /** "05/11/2026". */
    fun date(day: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * DAY_MS }
        return String.format(Locale.US, "%02d/%02d/%d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
    }
}

/** Lo guardado de Mi auto: mantenimiento, viajes, totales por mes y kilometraje. */
object CarCare {
    private const val PREFS = "mi_auto"
    private const val MAX_TRIPS = 30

    /** Abrir la pantalla Mi auto (desde el dock). */
    val open = mutableStateOf(false)
    val trips = mutableStateOf<List<TripSummary>>(emptyList())
    val maintenance = mutableStateOf<List<MaintItem>>(emptyList())
    val months = mutableStateOf<Map<String, MonthTotal>>(emptyMap())
    /** km que Onix vio en viajes terminados. */
    val seenKm = mutableStateOf(0f)
    /** Ajuste para que coincida con el tablero (null = sin cargar el kilometraje del auto). */
    val offsetKm = mutableStateOf<Float?>(null)
    val tracker = TripTracker()
    /** km del viaje en curso, para mostrar el kilometraje al día. */
    val liveKm = mutableStateOf(0f)
    /** Viaje que terminó recién (cartel corto en pantalla, v0.79.0). */
    val lastTrip = mutableStateOf<TripSummary?>(null)
    private var loaded = false
    private var registered = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        trips.value = readOrBackup(p, "viajes", emptyList()) { parseTrips(it) }
        maintenance.value = if (p.contains("mantenimiento")) readOrBackup(p, "mantenimiento", emptyList()) { parseList(it) { o -> MaintItem.fromJson(o) } }
            else Maintenance.defaults()
        months.value = readOrBackup(p, "meses", emptyMap()) { Monthly.parse(it) }
        seenKm.value = p.getFloat("km_vistos", 0f)
        offsetKm.value = if (p.contains("km_ajuste")) p.getFloat("km_ajuste", 0f) else null
    }

    /**
     * AUD-101 (v0.79.5): lee [key] con [parse]. Si el texto guardado está dañado, guarda una copia
     * tal cual en `<key>_respaldo_<ms>` antes de devolver [empty]; así el próximo guardado no borra
     * para siempre el historial (antes se mostraba vacío y el viaje siguiente lo pisaba).
     */
    internal fun <R> readOrBackup(
        p: android.content.SharedPreferences, key: String, empty: R,
        nowMs: Long = System.currentTimeMillis(), parse: (String) -> R,
    ): R {
        val raw = p.getString(key, null) ?: return empty
        return try {
            parse(raw)
        } catch (e: Exception) {
            val copy = "${key}_respaldo_$nowMs"
            p.edit().putString(copy, raw).apply()
            AppLog.warn("Mi auto: no se pudo leer \"$key\"; se guardó una copia en \"$copy\"", e)
            empty
        }
    }

    internal fun parseTrips(raw: String): List<TripSummary> = parseList(raw) { TripSummary.fromJson(it) }

    /** Tira una excepción si el texto está dañado (la maneja [readOrBackup]). */
    private fun <T> parseList(raw: String, f: (JSONObject) -> T?): List<T> {
        val a = JSONArray(raw)
        return (0 until a.length()).mapNotNull { f(a.getJSONObject(it)) }
    }

    /** Kilometraje del auto (con el ajuste), o null si nunca se cargó. */
    fun carKm(): Int? = offsetKm.value?.let { (seenKm.value + liveKm.value + it).roundToInt() }

    /** Carga el kilometraje que marca el tablero. */
    fun setCarKm(context: Context, km: Int) {
        offsetKm.value = km - seenKm.value - liveKm.value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("km_ajuste", offsetKm.value!!).apply()
    }

    /**
     * Kilometraje que informa el auto por OBD2 (v0.78.1): ajusta Mi auto solo si nunca se cargó o si
     * difiere en más de 5 km (el del auto manda).
     */
    fun fromCar(context: Context, km: Int) {
        load(context)
        val now = carKm()
        if (now == null || kotlin.math.abs(now - km) > 5) setCarKm(context, km)
    }

    /** Una lectura por segundo, desde LauncherApp. */
    fun tick(context: Context, nowMs: Long, speed: Int?, obd: ObdData?) {
        val trip = tracker.tick(nowMs, speed, obd?.litersPerHour, obd?.coolantC, obd?.fuelPct)
        if (trip != null) {
            save(context, trip)
        } else if (tracker.km - bankedKm >= 1f) {
            // AUD-052: cada km del viaje en curso se guarda ya, por si Android cierra la app a mitad de camino
            val delta = tracker.km - bankedKm
            bankedKm += delta
            seenKm.value += delta
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("km_vistos", seenKm.value).apply()
        }
        liveKm.value = tracker.km - bankedKm
    }

    /** v0.79.0: el viaje termina al apagar el auto (aviso de contacto de la radio NWD, estado 3). */
    fun register(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                if (ObdRecovery.mcuState(intent) != 3) return
                load(app)
                tracker.finish()?.let { save(app, it); liveKm.value = 0f }
            }
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                app, receiver, android.content.IntentFilter(ObdRecovery.MCU_ACTION), androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    /** km del viaje en curso que ya se sumaron al kilometraje (AUD-052). */
    private var bankedKm = 0f

    private fun save(context: Context, trip: TripSummary) {
        trips.value = (listOf(trip) + trips.value).take(MAX_TRIPS)
        months.value = Monthly.add(months.value, trip)
        seenKm.value += (trip.km - bankedKm).coerceAtLeast(0f)
        bankedKm = 0f
        lastTrip.value = trip
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("viajes", JSONArray(trips.value.map { it.toJson() }).toString())
            .putString("meses", Monthly.toJson(months.value))
            .putFloat("km_vistos", seenKm.value).apply()
        AppLog.info("Viaje: " + trip.short())
    }

    fun saveMaintenance(context: Context, list: List<MaintItem>) {
        maintenance.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mantenimiento", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    /** Lo más urgente del mantenimiento para avisar hoy en Inicio, o null si no hay o ya se avisó hoy. */
    fun noticeToday(context: Context): MaintItem? {
        val today = Reminders.today()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getLong("aviso_dia", -1) == today) return null
        val km = carKm()
        return maintenance.value.filter { Maintenance.urgency(it, km, today) >= Urgency.SOON }
            .maxByOrNull { Maintenance.urgency(it, km, today).ordinal }
    }

    fun noticeShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("aviso_dia", Reminders.today()).apply()
    }
}

/** Ícono de llave inglesa (Mi auto). */
internal fun DrawScope.iconWrench(color: Color = Color.White) {
    val s = size.width
    drawLine(color, Offset(s * 0.28f, s * 0.72f), Offset(s * 0.62f, s * 0.38f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
    drawArc(
        color, startAngle = 135f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(s * 0.52f, s * 0.12f), size = androidx.compose.ui.geometry.Size(s * 0.36f, s * 0.36f),
        style = Stroke(width = s * 0.12f, cap = StrokeCap.Round)
    )
    drawCircle(color, s * 0.08f, Offset(s * 0.24f, s * 0.76f))
}

private val Green = Color(0xFF5BD68A)
private val Orange = Color(0xFFFF8A1F)
private val Red = Color(0xFFFF4040)

private fun urgencyColor(u: Urgency) = when (u) {
    Urgency.UNSET -> Muted
    Urgency.OK -> Green
    Urgency.SOON -> Orange
    Urgency.DUE -> Red
}

/** Pantalla Mi auto (v0.79.0): Mantenimiento · Viajes · Motor. */
@Composable
internal fun CarCareOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    remember { CarCare.load(context); true }
    var tab by remember { mutableStateOf(0) }
    var editKm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MaintItem?>(null) }
    var doneFor by remember { mutableStateOf<MaintItem?>(null) }
    var codes by remember { mutableStateOf(false) }
    val today = Reminders.today()
    val carKm = CarCare.carKm()

    Box(
        Modifier.size(1024.dp, 600.dp).background(Color(0xE6040612))
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            Modifier.padding(start = 40.dp, top = 28.dp).size(944.dp, 548.dp).clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF111634)).border(1.dp, CardBorder, RoundedCornerShape(26.dp)).padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mi auto", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(18.dp))
                Text(
                    carKm?.let { "${Reminders.km(it)} km" } ?: "Kilometraje sin cargar",
                    color = if (carKm == null) Muted else Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                if (ObdState.odometerKm.value != null) Text("  (del auto)", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                SmallButton(if (carKm == null) "Cargar km del tablero" else "Corregir") { editKm = true }
                Spacer(Modifier.weight(1f))
                PillButton("Cerrar", Track, TextMain, Modifier.width(120.dp), onClose)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mantenimiento", "Viajes", "Motor").forEachIndexed { i, name ->
                    PillButton(name, if (tab == i) Accent else Track, if (tab == i) OnAccent else TextMain, Modifier.width(170.dp)) { tab = i }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    0 -> MaintenanceTab(carKm, today, onDone = { item ->
                        // Solo por fecha (batería, VTV): hecho hoy; con km, pregunta a qué km
                        if (item.everyKm == null) CarCare.saveMaintenance(context, CarCare.maintenance.value.map { if (it.id == item.id) Maintenance.done(it, null, today) else it })
                        else doneFor = item
                    }, onEdit = { editing = it })
                    1 -> TripsTab()
                    else -> EngineTab(onCodes = { codes = true })
                }
            }
        }
        if (editKm) NumberDialog("Kilometraje del tablero", carKm?.toString() ?: "", onClose = { editKm = false }) { km ->
            CarCare.setCarKm(context, km)
            editKm = false
        }
        doneFor?.let { item ->
            NumberDialog("¿A qué km hiciste \"${item.name}\"?", carKm?.toString() ?: "", onClose = { doneFor = null }) { km ->
                CarCare.saveMaintenance(context, CarCare.maintenance.value.map { if (it.id == item.id) Maintenance.done(it, km, today) else it })
                // El km que se escribe es el del tablero: corrige el total si no se informa por OBD2
                if (ObdState.odometerKm.value == null && (carKm == null || kotlin.math.abs(carKm - km) <= 2_000)) CarCare.setCarKm(context, km)
                doneFor = null
            }
        }
        editing?.let { item ->
            MaintDialog(item, onClose = { editing = null }, onDelete = {
                CarCare.saveMaintenance(context, CarCare.maintenance.value.filter { it.id != item.id })
                editing = null
            }) { updated ->
                val list = CarCare.maintenance.value
                CarCare.saveMaintenance(context, if (list.any { it.id == updated.id }) list.map { if (it.id == updated.id) updated else it } else list + updated)
                editing = null
            }
        }
        if (codes) CodesDialog(ObdState.codes.value, GpsSpeed.live(), onClose = { codes = false })
    }
}

@Composable
private fun MaintenanceTab(carKm: Int?, today: Long, onDone: (MaintItem) -> Unit, onEdit: (MaintItem) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tocá un ítem para escribir a qué km lo hiciste y cada cuánto se hace", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            SmallButton("+ Agregar") { onEdit(MaintItem(System.currentTimeMillis(), "", null, null, null, null)) }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val items = CarCare.maintenance.value.sortedByDescending { Maintenance.urgency(it, carKm, today).ordinal.let { o -> if (o == 0) -1 else o } }
            for (m in items) {
                val u = Maintenance.urgency(m, carKm, today)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                        .clickable { onEdit(m) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(urgencyColor(u)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                Maintenance.describe(m, carKm, today),
                                m.lastKm?.let { "hecho a los ${Reminders.km(it)} km" } ?: m.lastDay?.let { "hecho el ${Reminders.date(it)}" },
                                listOfNotNull(m.everyKm?.let { "${Reminders.km(it)} km" }, m.everyMonths?.let { "$it meses" })
                                    .takeIf { it.isNotEmpty() }?.joinToString(" o ", prefix = "cada ")
                            ).joinToString(" · "),
                            color = urgencyColor(u), fontSize = 13.sp
                        )
                    }
                    if (m.everyKm != null || m.everyMonths != null) SmallButton("Hecho") { onDone(m) }
                }
            }
        }
    }
}

@Composable
private fun TripsTab() {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Text("ÚLTIMOS VIAJES", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (CarCare.tracker.active) {
                    Text(String.format(Locale.US, "En curso: %.1f km", CarCare.liveKm.value), color = Green, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                }
                if (CarCare.trips.value.isEmpty()) Text("Todavía no hay viajes. Se anotan solos: empiezan al andar y terminan al apagar el auto.", color = Muted, fontSize = 15.sp)
                for (t in CarCare.trips.value) TripRow(t)
            }
        }
        Column(Modifier.width(360.dp)) {
            Text("POR MES", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            val list = CarCare.months.value.entries.sortedByDescending { it.key }.take(6)
            if (list.isEmpty()) Text("Se completa con los viajes.", color = Muted, fontSize = 15.sp)
            for ((k, m) in list) {
                Text(Monthly.label(k, m), color = TextMain, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF)).padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Litros: por el consumo del lector OBD2; si no lo da, por lo que bajó el tanque (≈, tanque de 54 L).", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EngineTab(onCodes: () -> Unit) {
    val d = ObdState.data.value
    val c = ObdState.codes.value
    Column {
        Text(
            if (d == null) "Lector OBD2: ${ObdState.status.value}" else "Lector OBD2 conectado" + (ObdState.device.value?.let { " ($it)" } ?: ""),
            color = if (d == null) Muted else Green, fontSize = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((label, value) in listOf(
                "Temperatura" to (d?.coolantC?.let { "$it °C" } ?: "--"),
                "Vueltas" to (d?.rpm?.let { "$it rpm" } ?: "--"),
                "Batería" to (d?.volts?.let { String.format(Locale("es", "AR"), "%.1f V", it) } ?: "--"),
                "Nafta" to (d?.fuelPct?.let { "$it %" } ?: "--"),
                "Velocidad" to (d?.speedKmh?.let { "$it km/h" } ?: "--")
            )) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(12.dp)) {
                    Text(label, color = Muted, fontSize = 13.sp)
                    Text(value, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                c == null -> "Códigos de falla: sin leer"
                c.mil == true -> "Luz del motor PRENDIDA · ${c.total} código(s)"
                c.total > 0 -> "Luz del motor apagada · ${c.total} código(s)"
                else -> "✓ Sin códigos de falla"
            },
            color = when { c?.mil == true -> Red; (c?.total ?: 0) > 0 -> Orange; c != null -> Green; else -> Muted },
            fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        c?.let { (it.stored + it.pending).distinct().take(4).forEach { code -> Text("$code · ${Dtc.describe(code)}", color = TextMain, fontSize = 15.sp) } }
        Spacer(Modifier.height(12.dp))
        PillButton("Ver códigos, salud y borrar", Accent, OnAccent, Modifier.width(300.dp), onCodes)
        Spacer(Modifier.height(8.dp))
        Text("Batería: con el auto apagado, menos de 12,2 V es batería floja; andando, 13,8 a 14,7 V es que el alternador carga bien.",
            color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun TripRow(t: TripSummary) {
    val c = Calendar.getInstance().apply { timeInMillis = t.start }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(10.dp)) {
        Text(
            String.format(Locale.US, "%02d/%02d %02d:%02d · ", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) + t.short(),
            color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium
        )
        Text(
            listOfNotNull(
                "máx ${t.maxKmh} km/h", "prom ${t.avgKmh} km/h",
                t.kmPerLiter?.let { String.format(Locale("es", "AR"), "%.1f km/l", it) },
                t.maxCoolant?.let { "motor $it °C" }
            ).joinToString(" · "),
            color = Muted, fontSize = 13.sp
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Ventana para escribir un número (kilometraje). */
@Composable
private fun NumberDialog(title: String, initial: String, onClose: () -> Unit, onOk: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    DialogFrame(onClose) {
        Text(title, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        NumberField(text, "Por ejemplo 85400") { text = it }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SmallButton("Cancelar", onClose)
            SmallButton("Guardar") { text.toIntOrNull()?.let(onOk) }
        }
    }
}

@Composable
private fun NumberField(text: String, hint: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    Box(modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0x22FFFFFF)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp, maxLines = 1)
        BasicTextField(
            value = text, onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(7)) }, singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 16.sp), cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Editar o agregar un ítem: nombre, a qué km lo hiciste (y la fecha), cada cuántos km y/o meses. */
@Composable
private fun MaintDialog(item: MaintItem, onClose: () -> Unit, onDelete: () -> Unit, onSave: (MaintItem) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(item.name) }
    var lastKm by remember { mutableStateOf(item.lastKm?.toString() ?: "") }
    var everyKm by remember { mutableStateOf(item.everyKm?.toString() ?: "") }
    var everyMonths by remember { mutableStateOf(item.everyMonths?.toString() ?: "") }
    var lastDay by remember { mutableStateOf(item.lastDay) }
    DialogFrame(onClose) {
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0x22FFFFFF)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart) {
            if (name.isEmpty()) Text("Nombre (ej.: Amortiguadores)", color = Muted, fontSize = 16.sp)
            BasicTextField(name, { name = it.take(40) }, singleLine = true, textStyle = TextStyle(color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Text("Lo hice a los … km del auto", color = Muted, fontSize = 14.sp)
        NumberField(lastKm, "Por ejemplo 80000") { lastKm = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fecha en que lo hice: ", color = Muted, fontSize = 14.sp)
            SmallButton(lastDay?.let { Reminders.date(it) } ?: "elegir") {
                val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = (lastDay ?: Reminders.today()) * 86_400_000L }
                DatePickerDialog(context, { _, y, m, d ->
                    lastDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m, d) }.timeInMillis / 86_400_000L
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Se hace cada … km", color = Muted, fontSize = 14.sp)
                NumberField(everyKm, "Ej.: 10000") { everyKm = it }
            }
            Column(Modifier.weight(1f)) {
                Text("o cada … meses", color = Muted, fontSize = 14.sp)
                NumberField(everyMonths, "Ej.: 12") { everyMonths = it.take(3) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            if (item.name.isNotEmpty()) SmallButton("Borrar ítem", onDelete)
            Spacer(Modifier.weight(1f))
            SmallButton("Cancelar", onClose)
            SmallButton("Guardar") {
                if (name.isNotBlank()) onSave(item.copy(
                    name = name.trim(), lastKm = lastKm.toIntOrNull(), everyKm = everyKm.toIntOrNull()?.takeIf { it > 0 },
                    everyMonths = everyMonths.toIntOrNull()?.takeIf { it > 0 }, lastDay = lastDay
                ))
            }
        }
    }
}

@Composable
private fun DialogFrame(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(640.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF20B1120))
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp)
        ) { content() }
    }
}
