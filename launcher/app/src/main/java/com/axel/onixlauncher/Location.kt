package com.axel.onixlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Ubicación por defecto mientras no haya GPS (AUD-026, AUD-105): la casa del autor, "Calle 9 de Julio 550, Tafí Viejo",
 * con la posición que dio TomTom. v0.79.7: un solo punto para el clima, el mapa, la búsqueda y "Mis lugares"
 * (antes eran dos puntos distintos de San Miguel de Tucumán, a unos 12 km de la casa).
 */
const val DEFAULT_LAT = -26.7368
const val DEFAULT_LON = -65.258
const val DEFAULT_PLACE_NAME = "Tafí Viejo"

/**
 * v0.79.8: el clima y el nombre del lugar se piden de nuevo cuando el auto se alejó más de
 * [THRESHOLD_M] del punto donde se pidieron la última vez (además del turno de 15 minutos).
 */
object PlaceRefresh {
    const val THRESHOLD_M = 3_000.0
    const val CHECK_MS = 30_000L

    /** true si no hay punto anterior o si la distancia al actual supera el umbral. */
    fun movedFar(lastLat: Double?, lastLon: Double?, lat: Double, lon: Double): Boolean {
        if (lastLat == null || lastLon == null) return true
        return Guidance.distance(lastLat, lastLon, lat, lon) > THRESHOLD_M
    }
}

/** Última ubicación conocida (GPS o red), o null sin permiso o sin datos. */
@SuppressLint("MissingPermission")
internal fun lastLocation(context: Context): Location? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        AppLog.warn("Ubicación: no disponible", e)
        null
    }
}

/**
 * Nombre del lugar para el clima: con ubicación, la localidad que da el geocodificador del
 * equipo (null si no hay); sin ubicación, el clima se pide para Tucumán y se dice eso.
 * Bloquea: llamar fuera del hilo principal.
 */
@Suppress("DEPRECATION") // getFromLocation sincrónico: la única opción en Android 7
internal fun placeName(context: Context, loc: Location?): String? {
    if (loc == null) return DEFAULT_PLACE_NAME
    // El Geocoder de la radio no tiene servicio (sin Google): se usa OpenStreetMap (v0.42.0)
    if (android.location.Geocoder.isPresent()) {
        try {
            val a = android.location.Geocoder(context, java.util.Locale("es", "AR"))
                .getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            (a?.locality ?: a?.subAdminArea ?: a?.adminArea)?.let { return it }
        } catch (_: Exception) {
            // "Service not Available": sigue con OpenStreetMap, sin llenar el registro de avisos
        }
    }
    return NavApi.locality(loc.latitude, loc.longitude).also {
        if (it == null) AppLog.warn("Ubicación: sin nombre del lugar (ni el equipo ni OpenStreetMap contestaron)")
    }
}

/** Velocidad del GPS en km/h, o null sin señal o sin permiso. Pide el permiso la primera vez. */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberGpsSpeed(): Int? {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var speed by remember { mutableStateOf<Int?>(null) }
    var lastFixAt by remember { mutableStateOf(0L) }
    // AUD-056: sin señal (túnel, cochera) el GPS deja de mandar datos y la última velocidad quedaba
    // fija: el velocímetro marcaba 60 parado y Mi auto sumaba km. A los 5 s sin datos se olvida.
    LaunchedEffect(granted) {
        while (true) {
            kotlinx.coroutines.delay(2_000)
            if (speed != null && GpsSpeed.stale(lastFixAt, android.os.SystemClock.elapsedRealtime())) speed = null
        }
    }
    DisposableEffect(granted) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastFixAt = android.os.SystemClock.elapsedRealtime()
                // v0.79.7: cada posición queda anotada (mapa: aviso de "sin señal"; Prueba completa: frecuencia)
                GpsFix.record(
                    lastFixAt, GpsFix.lagMs(location.elapsedRealtimeNanos, lastFixAt),
                    if (location.hasAccuracy()) location.accuracy else null,
                    location.extras?.getInt("satellites")?.takeIf { it > 0 }
                )
                val kmh = if (location.hasSpeed()) (location.speed * 3.6f).roundToInt() else 0
                speed = kmh
                GpsSpeed.record(kmh, lastFixAt)
            }
            @Deprecated("Requerido en Android 7")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { speed = null }
        }
        if (granted) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) {
                AppLog.warn("GPS: no se pudo iniciar", e)
                speed = null
            }
        }
        onDispose { lm.removeUpdates(listener) }
    }
    return speed
}

/**
 * Registro de las posiciones que entrega el GPS (v0.79.7). Sirve para dos cosas: avisar en el mapa cuando
 * no hay señal (hasta que llega la primera posición el mapa se queda en la última conocida, que puede ser
 * la de donde quedó estacionado el auto) y medir en la Prueba completa cada cuánto llegan y con cuánto
 * retraso. Todo con el reloj del equipo (`SystemClock.elapsedRealtime`), nunca con la hora, que en estas
 * radios puede estar mal. El tiempo siempre entra como parámetro (para los tests).
 */
object GpsFix {
    /** Hasta acá la posición "está viva"; pasado esto el mapa avisa. */
    const val FRESH_MS = 10_000L
    /** Pasado esto, la posición que se usa es de otro momento: el auto pudo haberse movido. */
    const val STALE_MS = 120_000L
    private const val KEEP = 30

    private val received = ArrayList<Long>()
    private val lags = ArrayList<Long?>()
    private var accuracy: Float? = null
    private var sats: Int? = null

    /** Anota una posición recién llegada ([lagMs]: cuánto tardó en llegar desde que el GPS la calculó, si se sabe). */
    @Synchronized
    fun record(receivedAtMs: Long, lagMs: Long?, accuracyM: Float?, satellites: Int?) {
        received += receivedAtMs
        lags += lagMs
        while (received.size > KEEP) { received.removeAt(0); lags.removeAt(0) }
        accuracy = accuracyM
        sats = satellites
    }

    @Synchronized
    internal fun clear() { received.clear(); lags.clear(); accuracy = null; sats = null }

    /** Hace cuánto llegó la última posición, o null si todavía no llegó ninguna. */
    @Synchronized
    fun ageMs(now: Long): Long? = received.lastOrNull()?.let { now - it }

    /** Cuánto tardó una posición en llegar a la app: null si la radio no informa su hora o el dato no es creíble. */
    fun lagMs(elapsedRealtimeNanos: Long, nowMs: Long): Long? {
        if (elapsedRealtimeNanos <= 0) return null
        return (nowMs - elapsedRealtimeNanos / 1_000_000).takeIf { it in 0..3_600_000 }
    }

    /** Lo que dice el mapa sobre el GPS: null si anda; si no, que busca o hace cuánto que no hay señal. */
    fun status(ageMs: Long?): String? = when {
        ageMs == null -> "Buscando GPS…"
        ageMs <= FRESH_MS -> null
        else -> "Sin señal de GPS hace " + span(ageMs)
    }

    /** Si la posición que se usa es vieja (o nunca hubo una): las rutas salen "desde la última posición conocida". */
    fun isStale(ageMs: Long?): Boolean = ageMs == null || ageMs > STALE_MS

    private fun span(ms: Long) = when {
        ms < 60_000 -> "${ms / 1000} s"
        ms < 3_600_000 -> "${ms / 60_000} min"
        else -> "${ms / 3_600_000} h"
    }

    /** Resumen de las últimas posiciones, o null si no llegó ninguna. */
    @Synchronized
    fun report(now: Long): Report? {
        if (received.isEmpty()) return null
        val gaps = received.zipWithNext { a, b -> b - a }
        val known = lags.filterNotNull()
        return Report(
            count = received.size,
            ageMs = now - received.last(),
            avgIntervalMs = gaps.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            maxIntervalMs = gaps.maxOrNull(),
            avgLagMs = known.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
            accuracyM = accuracy,
            satellites = sats
        )
    }

    class Report(
        val count: Int,
        val ageMs: Long,
        val avgIntervalMs: Long?,
        val maxIntervalMs: Long?,
        val avgLagMs: Long?,
        val accuracyM: Float?,
        val satellites: Int?
    ) {
        fun text(): String {
            fun s(ms: Long) = String.format(java.util.Locale("es", "AR"), "%.1f s", ms / 1000.0)
            return listOfNotNull(
                "última hace ${s(ageMs)}",
                avgIntervalMs?.let { "intervalo medio ${s(it)}" + (maxIntervalMs?.let { m -> " (máximo ${s(m)})" } ?: "") },
                avgLagMs?.let { "retraso de entrega ${s(it)}" },
                accuracyM?.let { "precisión ${it.roundToInt()} m" },
                satellites?.let { "$it satélites" },
                "$count posiciones"
            ).joinToString(" · ")
        }
    }
}

/** AUD-056: cuándo la última velocidad del GPS ya no vale (función pura, para los tests). */
object GpsSpeed {
    const val STALE_MS = 5_000L

    fun stale(lastFixAt: Long, now: Long): Boolean = lastFixAt == 0L || now - lastFixAt > STALE_MS

    /*
     * AUD-058 (v0.64.1): con Android Auto o CarPlay encima, Compose no redibuja Onix y la velocidad
     * que se calcula en la pantalla queda congelada. Mi auto y los avisos leen esta, que se actualiza
     * desde el GPS y el OBD2 aunque la pantalla esté tapada.
     */
    @Volatile private var lastKmh: Int? = null
    @Volatile private var lastAt = 0L

    fun record(kmh: Int, at: Long) {
        lastKmh = kmh
        lastAt = at
    }

    /** OBD2 primero; si no, el GPS si es reciente (pura, para los tests). */
    fun pick(obdKmh: Int?, gpsKmh: Int?, gpsAt: Long, now: Long): Int? =
        obdKmh ?: gpsKmh?.takeIf { !stale(gpsAt, now) }

    /** La velocidad de ahora, sin depender de la pantalla. */
    fun live(): Int? = pick(ObdState.data.value?.speedKmh, lastKmh, lastAt, android.os.SystemClock.elapsedRealtime())
}
