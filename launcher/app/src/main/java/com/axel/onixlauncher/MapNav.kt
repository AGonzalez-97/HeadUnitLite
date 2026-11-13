package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/*
 * Navegación dentro del launcher (v0.34.0): mapa en modo oscuro con tu ubicación, buscador de
 * destinos y la ruta dibujada, con distancia y tiempo. Desde v0.75.0 búsqueda y rutas son de TomTom.
 * Desde v0.37.0 la navegación giro a giro con voz es propia (Navigation.kt): no usa Google Maps
 * ni Waze. Mantener apretado un punto del mapa lo elige como destino.
 */

/** Un lugar encontrado por el buscador. */
data class Place(val name: String, val lat: Double, val lon: Double)

/** Una ruta: distancia en metros, duración en segundos, los puntos (lat, lon) y las maniobras. */
class Route(
    val distanceM: Double,
    val durationS: Double,
    val points: List<Pair<Double, Double>>,
    val steps: List<Step> = emptyList()
)

/**
 * Búsqueda, rutas y nombres de lugares con TomTom (v0.75.0, pedido del autor). OpenStreetMap
 * (Nominatim, Photon, OSRM, FOSSGIS) no encontraba las direcciones con número de Tucumán ("9 de
 * Julio 550, Tafí Viejo"; "Av. Adolfo de la Vega 473") y sus rutas fallaban en la radio: el autor
 * pidió quitarlo, también como respaldo. TomTom las encuentra con la altura exacta y da rutas con
 * tránsito (prueba en la CI del 05/11/2026, `tools/tomtom/prueba.py`). La clave es del autor
 * (GitHub Secret `TOMTOM_API_KEY`, entra al compilar en BuildConfig); sin clave, el buscador lo
 * dice. Plan gratuito: 2.500 pedidos por día. Las funciones parse* y las URL son puras, para los tests.
 */
object NavApi {
    private const val BASE = "https://api.tomtom.com"
    /** Clave de TomTom del autor (vacía si la compilación no tuvo el secreto). */
    val key: String get() = BuildConfig.TOMTOM_KEY

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun ll(v: Double): String = String.format(Locale.US, "%.6f", v)

    /** Búsqueda libre (direcciones con número, lugares, comercios) cerca de [nearLat], [nearLon]. */
    fun tomTomSearchUrl(q: String, nearLat: Double, nearLon: Double, key: String = this.key): String =
        "$BASE/search/2/search/${enc(q.trim())}.json?key=${enc(key)}&countrySet=AR&language=es-419&limit=6" +
            "&lat=${ll(nearLat)}&lon=${ll(nearLon)}&typeahead=false"

    /** Ruta en auto con tránsito en vivo y las indicaciones. */
    fun tomTomRouteUrl(
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, key: String = this.key,
        via: Pair<Double, Double>? = null   // v0.77.0: parada en el camino
    ): String =
        "$BASE/routing/1/calculateRoute/${ll(fromLat)},${ll(fromLon)}:" +
            (via?.let { "${ll(it.first)},${ll(it.second)}:" } ?: "") +
            "${ll(toLat)},${ll(toLon)}/json?key=${enc(key)}" +
            "&traffic=true&travelMode=car&routeType=fastest&instructionsType=coded&language=es-AR"

    /** Dirección de un punto (calle y altura, localidad). */
    fun tomTomReverseUrl(lat: Double, lon: Double, key: String = this.key): String =
        "$BASE/search/2/reverseGeocode/${ll(lat)},${ll(lon)}.json?key=${enc(key)}&language=es-419"

    /** GET con el TLS de la app y, si falla, por TLS 1.3 directo (HU-013). Null si no hubo respuesta 2xx. Bloquea. */
    private fun fetch(url: String): String? {
        Net.get(url)?.let { return it }
        val r = DirectHttps.get(url) ?: return null
        return r.second.takeIf { r.first in 200..299 }
    }

    /** Lugares encontrados; vacío si no hay; null si no hubo conexión o falta la clave. Bloquea. */
    fun search(q: String, lat: Double, lon: Double): List<Place>? {
        if (key.isBlank()) { AppLog.warn("Búsqueda: falta la clave de TomTom (TOMTOM_API_KEY)"); return null }
        return fetch(tomTomSearchUrl(q, lat, lon))?.let { parseTomTomPlaces(it) }
    }

    /** Ruta de TomTom (respuesta cruda) o null. Bloquea. */
    fun routeJson(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, via: Pair<Double, Double>? = null): String? =
        if (key.isBlank()) null else fetch(tomTomRouteUrl(fromLat, fromLon, toLat, toLon, via = via))

    /** Calle de un punto elegido en el mapa. Bloquea. */
    fun reverse(lat: Double, lon: Double): String? =
        if (key.isBlank()) null else fetch(tomTomReverseUrl(lat, lon))?.let { parseTomTomReverse(it) }

    /** Localidad del encabezado de Inicio. Bloquea. */
    fun locality(lat: Double, lon: Double): String? =
        if (key.isBlank()) null else fetch(tomTomReverseUrl(lat, lon))?.let { parseTomTomLocality(it) }

    /** "Calle 9 de Julio 550, Tafí Viejo, Tucumán" (o "Nombre del lugar, calle, localidad"). */
    fun parseTomTomPlaces(json: String): List<Place> = try {
        val arr = JSONObject(json).getJSONArray("results")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val a = o.optJSONObject("address") ?: return@mapNotNull null
            val p = o.optJSONObject("position") ?: return@mapNotNull null
            if (!p.has("lat") || !p.has("lon")) return@mapNotNull null
            val street = listOf(a.optString("streetName"), a.optString("streetNumber")).filter { it.isNotBlank() }.joinToString(" ")
            val poi = o.optJSONObject("poi")?.optString("name").orEmpty()
            val parts = listOf(poi, street, a.optString("municipality"), if (poi.isEmpty()) a.optString("countrySubdivision") else "")
                .filter { it.isNotBlank() }.distinct()
            if (parts.isEmpty()) null else Place(parts.joinToString(", "), p.getDouble("lat"), p.getDouble("lon"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Maniobra de TomTom (guidance.instructions[].maneuver) como la usa la guía y la voz
     * (tipo, sentido y salida de la rotonda, con los nombres que ya usaba la navegación). Pura.
     */
    fun maneuver(code: String, exit: Int): Triple<String, String, Int> = when {
        code == "DEPART" -> Triple("depart", "", 0)
        code.startsWith("ARRIVE") -> Triple("arrive", "", 0)
        code.startsWith("WAYPOINT") -> Triple("waypoint", "", 0)   // v0.77.0: parada en el camino
        code.startsWith("ROUNDABOUT") -> Triple("roundabout", "", exit)
        code == "TURN_LEFT" -> Triple("turn", "left", 0)
        code == "TURN_RIGHT" -> Triple("turn", "right", 0)
        code == "BEAR_LEFT" -> Triple("turn", "slight left", 0)
        code == "BEAR_RIGHT" -> Triple("turn", "slight right", 0)
        code == "SHARP_LEFT" -> Triple("turn", "sharp left", 0)
        code == "SHARP_RIGHT" -> Triple("turn", "sharp right", 0)
        code.contains("UTURN") -> Triple("turn", "uturn", 0)
        code == "KEEP_LEFT" -> Triple("fork", "slight left", 0)
        code == "KEEP_RIGHT" -> Triple("fork", "slight right", 0)
        code.startsWith("ENTER_") || code == "ENTRANCE_RAMP" -> Triple("merge", "", 0)
        code == "MOTORWAY_EXIT_LEFT" -> Triple("off ramp", "slight left", 0)
        code == "MOTORWAY_EXIT_RIGHT" || code == "TAKE_EXIT" -> Triple("off ramp", "slight right", 0)
        else -> Triple("continue", "straight", 0)
    }

    fun parseTomTomRoute(json: String): Route? = try {
        val r = JSONObject(json).getJSONArray("routes").getJSONObject(0)
        val s = r.getJSONObject("summary")
        val pts = mutableListOf<Pair<Double, Double>>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val p = legs.getJSONObject(l).getJSONArray("points")
            for (i in 0 until p.length()) p.getJSONObject(i).let { pts += it.getDouble("latitude") to it.getDouble("longitude") }
        }
        val ins = r.optJSONObject("guidance")?.optJSONArray("instructions")
        val steps = mutableListOf<Step>()
        if (ins != null) {
            for (i in 0 until ins.length()) {
                val o = ins.getJSONObject(i)
                val pt = o.getJSONObject("point")
                val next = if (i + 1 < ins.length()) ins.getJSONObject(i + 1).optDouble("routeOffsetInMeters", 0.0) else null
                val here = o.optDouble("routeOffsetInMeters", 0.0)
                val (type, modifier, exit) = maneuver(o.optString("maneuver"), o.optInt("roundaboutExitNumber", 0))
                steps += Step(type, modifier, o.optString("street"), exit, pt.getDouble("latitude"), pt.getDouble("longitude"),
                    if (next != null) (next - here).coerceAtLeast(0.0) else 0.0)
            }
        }
        if (pts.size < 2) null else Route(s.getDouble("lengthInMeters"), s.getDouble("travelTimeInSeconds"), pts, steps)
    } catch (e: Exception) {
        null
    }

    fun parseTomTomReverse(json: String): String? = try {
        val a = JSONObject(json).getJSONArray("addresses").getJSONObject(0).getJSONObject("address")
        val street = listOf(a.optString("streetName"), a.optString("streetNumber")).filter { it.isNotBlank() }.joinToString(" ")
        listOf(street, a.optString("municipality")).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    fun parseTomTomLocality(json: String): String? = try {
        JSONObject(json).getJSONArray("addresses").getJSONObject(0).getJSONObject("address")
            .optString("municipality").ifBlank { null }
    } catch (e: Exception) {
        null
    }

    /** "12,4 km · 18 min" (o "850 m · 3 min"). */
    fun summary(r: Route): String {
        val dist = if (r.distanceM >= 1000) String.format(Locale("es", "AR"), "%.1f km", r.distanceM / 1000) else "${r.distanceM.toInt()} m"
        val mins = Math.round(r.durationS / 60).toInt()
        val time = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
        return "$dist · $time"
    }
}

/**
 * Lugares guardados en el equipo (SharedPreferences `lugares`): Casa, Trabajo, favoritos y los
 * últimos 10 destinos. Un lugar se guarda como "nombre|lat|lon" (v0.36.0).
 */
object SavedPlaces {
    val home = mutableStateOf<Place?>(null)
    val work = mutableStateOf<Place?>(null)
    val favorites = mutableStateOf<List<Place>>(emptyList())
    val recent = mutableStateOf<List<Place>>(emptyList())
    private var loaded = false

    fun encode(p: Place): String = String.format(Locale.US, "%s|%.6f|%.6f", p.name.replace("|", "/").replace("\n", " "), p.lat, p.lon)

    fun decode(s: String?): Place? {
        val parts = s?.split("|") ?: return null
        if (parts.size != 3) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        return Place(parts[0], lat, lon)
    }

    fun decodeList(s: String?): List<Place> = s?.split("\n")?.mapNotNull { decode(it) }.orEmpty()

    /** Agrega un destino al principio de los recientes, sin repetir, hasta 10 (pura, para los tests). */
    fun pushRecent(list: List<Place>, p: Place): List<Place> = (listOf(p) + list.filterNot { same(it, p) }).take(10)

    fun same(a: Place, b: Place) = Math.abs(a.lat - b.lat) < 1e-4 && Math.abs(a.lon - b.lon) < 1e-4

    /** Distancia en metros (haversine) entre dos lugares, o Double.MAX_VALUE si alguno es null. */
    fun distance(a: Place?, b: Place?): Double {
        if (a == null || b == null) return Double.MAX_VALUE
        return Guidance.distance(a.lat, a.lon, b.lat, b.lon)
    }

    /** true si son el mismo punto o están a menos de [radiusM] metros (por defecto 100 m). */
    fun isNear(a: Place?, b: Place?, radiusM: Double = 100.0): Boolean {
        if (a == null || b == null) return false
        if (same(a, b)) return true
        return distance(a, b) <= radiusM
    }

    private fun prefs(c: Context) = c.getSharedPreferences("lugares", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        home.value = decode(p.getString("casa", null))
        work.value = decode(p.getString("trabajo", null))
        favorites.value = decodeList(p.getString("favoritos", null))
        recent.value = decodeList(p.getString("recientes", null))
    }

    private fun save(c: Context) {
        prefs(c).edit()
            .putString("casa", home.value?.let { encode(it) })
            .putString("trabajo", work.value?.let { encode(it) })
            .putString("favoritos", favorites.value.joinToString("\n") { encode(it) })
            .putString("recientes", recent.value.joinToString("\n") { encode(it) })
            .apply()
    }

    fun setHome(c: Context, p: Place) { home.value = p.copy(name = "Casa · " + p.name.removePrefix("Casa · ")); save(c) }
    fun setWork(c: Context, p: Place) { work.value = p.copy(name = "Trabajo · " + p.name.removePrefix("Trabajo · ")); save(c) }
    fun isFavorite(p: Place) = favorites.value.any { same(it, p) || isNear(it, p, 80.0) }
    fun toggleFavorite(c: Context, p: Place) {
        favorites.value = if (isFavorite(p)) favorites.value.filterNot { same(it, p) } else favorites.value + p
        save(c)
    }
    fun addRecent(c: Context, p: Place) { recent.value = pushRecent(recent.value, p); save(c) }
    /** v0.77.0: favorito escrito en Mis lugares (reemplaza uno en el mismo punto). */
    fun addFavorite(c: Context, p: Place) { favorites.value = favorites.value.filterNot { same(it, p) } + p; save(c) }
}

/**
 * Panel de mapa con buscador, accesos rápidos, ruta y navegación giro a giro propia.
 * En las capturas automáticas (sin animaciones) el mapa se dibuja como un recuadro.
 */
@Composable
internal fun MapPanel(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    // v0.81.23: última búsqueda hecha, para sugerir mientras se escribe sin repetir pedidos
    var lastSearched by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf<Place?>(null) }
    var route by remember { mutableStateOf<Route?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // v0.81.22: rutas que fallaron por la red (se reintentan) y mapa 3D que pasó al clásico solo
    var routeFailures by remember { mutableStateOf(0) }
    var mapFellBack by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<NavMap?>(null) }
    /** Título de la lista que se muestra debajo del buscador ("Favoritos", "Recientes" o null = resultados). */
    var listTitle by remember { mutableStateOf<String?>(null) }
    var navigating by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<GuidanceState?>(null) }
    // v0.77.0: parada en el camino, lugares cerca / en el camino y Mis lugares
    var stop by remember { mutableStateOf<Place?>(null) }
    var alongOpen by remember { mutableStateOf(false) }
    var alongTitle by remember { mutableStateOf<String?>(null) }
    var alongResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SavedPlaces.load(context)
        AlertPrefs.load(context)
        MapPrefs.load(context)
        Voice.init(context)
    }

    fun here(): GeoPoint {
        val loc = lastLocation(context)
        return GeoPoint(loc?.latitude ?: DEFAULT_LAT, loc?.longitude ?: DEFAULT_LON)
    }

    fun search(live: Boolean = false) {
        val q = query.trim()
        if (q.length < 3) return
        // Escribiendo no se cierra el teclado; con "Buscar" sí
        if (!live) focus.clearFocus()
        lastSearched = q
        searching = true
        listTitle = null
        scope.launch {
            val h = here()
            val found = withContext(Dispatchers.IO) { NavApi.search(q, h.latitude, h.longitude) }
            // Si mientras tanto se siguió escribiendo, esta respuesta ya es vieja
            if (live && query.trim() != q) { searching = false; return@launch }
            results = found.orEmpty()
            info = if (found == null) (if (NavApi.key.isBlank()) "Falta la clave de TomTom" else "Sin conexión para buscar") else if (results.isEmpty()) "No se encontró \"$q\"" else null
            searching = false
        }
    }

    /** Calcula la ruta desde donde estoy hasta [p] y la dibuja. */
    fun routeTo(p: Place, from: GeoPoint = here()) {
        val m = map
        // v0.79.7: sin posición del GPS reciente, la ruta sale de la última conocida y se avisa
        val stale = GpsFix.isStale(GpsFix.ageMs(android.os.SystemClock.elapsedRealtime()))
        scope.launch {
            val via = stop?.let { it.lat to it.lon }
            val json = withContext(Dispatchers.IO) { NavApi.routeJson(from.latitude, from.longitude, p.lat, p.lon, via) }
            // AUD-054: si mientras se calculaba se eligió otro destino, esta ruta ya no sirve
            if (dest?.let { it.lat == p.lat && it.lon == p.lon } == false) return@launch
            val r = json?.let { NavApi.parseTomTomRoute(it) }
            // AUD-055: navegando, si el recálculo falla (sin señal) se sigue con la ruta anterior y se
            // reintenta; antes quedaba sin ruta y la guía (voz y cartel) se apagaba para siempre
            if (r == null && navigating && route != null) {
                info = if (json == null) "Sin señal para recalcular: sigo con la ruta anterior" else "No se pudo recalcular"
                return@launch
            }
            route = r
            routeFailures = if (r == null && json == null) routeFailures + 1 else 0
            info = r?.let { NavApi.summary(it) + if (stale) " · desde la última posición conocida" else "" }
                ?: if (json == null) "Sin conexión para calcular la ruta: reintento solo" else "No se pudo calcular la ruta"
            m?.showRoute(p, r, fit = !navigating)
        }
    }

    fun choose(p: Place) {
        results = emptyList()
        listTitle = null
        SavedPlaces.addRecent(context, p)
        dest = p
        stop = null
        route = null
        info = "Calculando la ruta…"
        routeTo(p)
    }

    /** Lo que falta de la ruta desde donde estoy (para buscar "en el camino"). */
    fun remainingPoints(): List<Pair<Double, Double>> {
        val pts = route?.points ?: return emptyList()
        val h = here()
        var best = 0
        var bestD = Double.MAX_VALUE
        pts.forEachIndexed { i, (la, lo) ->
            val d = (la - h.latitude) * (la - h.latitude) + (lo - h.longitude) * (lo - h.longitude)
            if (d < bestD) { bestD = d; best = i }
        }
        return pts.drop(best)
    }

    /** v0.77.0: lugares de [c] cerca o, navegando, sobre la ruta que falta. */
    fun searchCategory(c: PlaceCategory) {
        focus.clearFocus()
        val alongRoute = navigating && route != null
        if (alongRoute) { alongOpen = true; alongTitle = "${c.label}: buscando en el camino…"; alongResults = emptyList() }
        else { searching = true; listTitle = null; info = "Buscando ${c.label.substringAfter(' ').lowercase()} cerca…" }
        scope.launch {
            val h = here()
            val found = withContext(Dispatchers.IO) {
                if (alongRoute) Nearby.along(c, remainingPoints()) else Nearby.nearby(c, h.latitude, h.longitude)
            }
            val none = if (found == null) "Sin conexión para buscar" else "No hay ${c.label.substringAfter(' ').lowercase()} cerca"
            if (alongRoute) {
                alongResults = found.orEmpty()
                alongTitle = if (found.isNullOrEmpty()) none else "${c.label} en el camino"
            } else {
                searching = false
                results = found.orEmpty()
                listTitle = if (results.isEmpty()) null else "${c.label} cerca"
                info = if (results.isEmpty()) none else null
            }
        }
    }

    /** v0.77.0: agrega [p] como parada antes del destino y recalcula. */
    fun addStop(p: Place) {
        stop = p
        alongOpen = false
        alongResults = emptyList()
        dest?.let { routeTo(it) }
        Voice.say("Parada agregada")
    }

    /** Punto elegido con el dedo: se busca su nombre y se calcula la ruta. */
    fun pickPoint(lat: Double, lon: Double) {
        if (navigating) return
        val p = Place("Punto elegido", lat, lon)
        choose(p)
        scope.launch {
            val name = withContext(Dispatchers.IO) { NavApi.reverse(lat, lon) }
            if (name != null && dest == p) dest = p.copy(name = name)
        }
    }

    fun stopNavigation() {
        navigating = false
        stop = null
        alongOpen = false
        alongResults = emptyList()
        guide = null
        map?.resetView()
    }

    fun startNavigation() {
        navigating = true
        guide = null
        here().let { map?.follow(it.latitude, it.longitude, null) }
        route?.steps?.firstOrNull()?.let { Voice.say(it.instruction, clips = VoiceClips.step(it.type, it.modifier, it.exit, null)) }
    }

    // Destino pedido desde Inicio (Casa / Trabajo) o desde el celular (v0.76.0: arranca solo)
    var autoStart by remember { mutableStateOf(false) }
    // v0.81.22: a qué destino corresponde el arranque automático (no a una ruta elegida después)
    var autoStartFor by remember { mutableStateOf<Place?>(null) }
    val pending = NavRequest.pending.value
    androidx.compose.runtime.LaunchedEffect(pending, map) {
        if (pending != null && (map != null || !RenderFlags.animate)) {
            NavRequest.pending.value = null
            autoStart = NavRequest.startNow
            autoStartFor = pending
            NavRequest.startNow = false
            if (navigating) stopNavigation()
            choose(pending)
        }
    }
    // v0.77.0: pedidos desde otras pantallas: buscar una categoría (nafta baja) o abrir Mis lugares
    val wantCategory = NavRequest.category.value
    androidx.compose.runtime.LaunchedEffect(wantCategory, map) {
        if (wantCategory != null && (map != null || !RenderFlags.animate)) {
            NavRequest.category.value = null
            searchCategory(wantCategory)
        }
    }
    androidx.compose.runtime.LaunchedEffect(route, autoStart) {
        if (autoStart && route != null && !navigating) {
            autoStart = false
            val wanted = autoStartFor
            if (wanted == null || dest?.let { it.lat == wanted.lat && it.lon == wanted.lon } == true) startNavigation()
        }
    }
    // v0.81.23: sugerencias mientras se escribe (pausa corta entre letras para no gastar consultas)
    androidx.compose.runtime.LaunchedEffect(query) {
        if (LiveSearch.due(query, lastSearched) && !navigating) {
            kotlinx.coroutines.delay(LiveSearch.DELAY_MS)
            search(live = true)
        }
    }
    // v0.81.22: una ruta que no se pudo calcular por la red se pide de nuevo sola
    androidx.compose.runtime.LaunchedEffect(routeFailures, NetWatch.back.intValue) {
        val p = dest
        if (routeFailures in 1..RouteRetry.MAX && p != null && route == null && !navigating) {
            kotlinx.coroutines.delay(RouteRetry.waitMs(routeFailures, WifiRescue.down))
            if (dest == p && route == null && !navigating) routeTo(p)
        }
    }
    // v0.81.22: si el mapa 3D pasó al clásico (suele ser por falta de red), se prueba de nuevo al volver
    androidx.compose.runtime.LaunchedEffect(NetWatch.back.intValue) {
        if (mapFellBack) {
            mapFellBack = false
            AppLog.info("Mapa 3D: volvió la red; pruebo de nuevo")
            MapPrefs.engine.value = "3d"
        }
    }

    // Navegación: GPS cada 1 s, cartel, voz, mapa que sigue y gira con el auto, y recálculo
    if (navigating && RenderFlags.animate) {
        val r = route
        DisposableEffect(r) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val g = r?.let { Guidance(it) }
            var lastReroute = 0L
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    map?.follow(loc.latitude, loc.longitude, if (loc.hasBearing() && (loc.speed > 0.8f || !loc.hasSpeed())) loc.bearing else null)
                    // v0.77.0: parada pasada (a menos de 80 m): los recálculos ya no la incluyen
                    stop?.let { s ->
                        val d = FloatArray(1)
                        android.location.Location.distanceBetween(loc.latitude, loc.longitude, s.lat, s.lon, d)
                        if (d[0] < 80f) stop = null
                    }
                    val st = g?.update(loc.latitude, loc.longitude) ?: return
                    guide = st
                    st.speak?.let { Voice.say(it, clips = st.speakClips) }
                    if (st.arrived) {
                        info = "Llegaste a destino"
                        stopNavigation()
                    } else if (st.offRoute && android.os.SystemClock.elapsedRealtime() - lastReroute > REROUTE_RETRY_MS) {
                        lastReroute = android.os.SystemClock.elapsedRealtime()
                        Voice.say("Recalculando", clips = listOf("recalculando"))
                        dest?.let { routeTo(it, GeoPoint(loc.latitude, loc.longitude)) }
                    }
                }
                @Deprecated("Requerido en Android 7")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                @Suppress("MissingPermission")
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) {
                AppLog.warn("Navegación: sin GPS", e)
            }
            onDispose { lm.removeUpdates(listener) }
        }
    }

    Box(modifier.card()) {
        if (RenderFlags.animate) {
            // Mapa 3D (MapLibre) o, si no arranca, el clásico (osmdroid) (v0.47.0)
            androidx.compose.runtime.key(MapPrefs.engine.value) {
                AndroidView(
                    factory = { ctx ->
                        val start = here().let { it.latitude to it.longitude }
                        val pick: (Double, Double) -> Unit = { la, lo -> pickPoint(la, lo) }
                        val nm: NavMap = if (MapPrefs.engine.value == "3d") {
                            try {
                                LibreNavMap(ctx, start, pick) { why ->
                                    scope.launch {
                                        AppLog.warn("Mapa 3D: no cargó ($why); uso el mapa clásico")
                                        info = "El mapa 3D no cargó: uso el mapa clásico"
                                        mapFellBack = true
                                        MapPrefs.engine.value = "clasico"
                                    }
                                }
                            } catch (e: Throwable) {
                                AppLog.warn("Mapa 3D: no arrancó; uso el mapa clásico", e)
                                OsmNavMap(ctx, start, pick)
                            }
                        } else OsmNavMap(ctx, start, pick)
                        map = nm
                        if (dest != null) nm.showRoute(dest, route, true)
                        nm.view
                    },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                )
                DisposableEffect(Unit) {
                    onDispose {
                        map?.release()
                        map = null
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0E1626)), contentAlignment = Alignment.Center) {
                Text("Mapa (OpenStreetMap)", color = Muted, fontSize = 16.sp)
            }
        }

        val g = guide
        if (navigating) {
            // Cartel de navegación: flecha, distancia a la maniobra, indicación y llegada
            Row(
                Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF2122A4F))
                    .border(1.dp, Color(0x664C8DFF), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(48.dp)) { maneuverArrow(g?.step?.modifier.orEmpty(), g?.step?.type.orEmpty()) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        g?.let { Guidance.shortDistance(it.toManeuverM) } ?: "Buscando GPS…",
                        color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp
                    )
                    Text(
                        g?.step?.instruction ?: "Esperando la ubicación", color = TextMain, fontSize = 16.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp
                    )
                }
            }
        } else {
            // Buscador
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0xF00B1120))
                        .border(1.dp, CardBorder, RoundedCornerShape(23.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(20.dp)) { iconPin() }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("¿A dónde vamos?", color = Muted, fontSize = 17.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = TextMain, fontSize = 17.sp),
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        if (searching) "Buscando…" else "Buscar", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { search() }.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                // Accesos rápidos: Casa, Trabajo, Favoritos y Recientes
                if (results.isEmpty() && dest == null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val home = SavedPlaces.home.value
                        val work = SavedPlaces.work.value
                        QuickChip("Casa", Modifier.weight(1f)) {
                            if (home != null) choose(home) else NavRequest.editPlaces.value = true
                        }
                        QuickChip("Trabajo", Modifier.weight(1f)) {
                            if (work != null) choose(work) else NavRequest.editPlaces.value = true
                        }
                        QuickChip("★ Favoritos", Modifier.weight(1f)) {
                            results = SavedPlaces.favorites.value
                            listTitle = "Favoritos"
                            if (results.isEmpty()) info = "Todavía no hay favoritos: tocá ☆ en un destino"
                        }
                        QuickChip("Recientes", Modifier.weight(1f)) {
                            results = SavedPlaces.recent.value
                            listTitle = "Recientes"
                            if (results.isEmpty()) info = "Todavía no hay destinos recientes"
                        }
                        QuickChip("✎", Modifier.width(44.dp)) { NavRequest.editPlaces.value = true }
                    }
                    // v0.77.0: lugares cerca por categoría (140 dp: entran 4 enteras sin cortarse al borde)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (c in PlaceCategory.entries) QuickChip(c.label, Modifier.width(140.dp)) { searchCategory(c) }
                    }
                }
                if (results.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xF20B1120))
                    ) {
                        listTitle?.let { t ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(t, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Cerrar", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { results = emptyList(); listTitle = null })
                            }
                        }
                        for (p in results.take(7)) {
                            Text(
                                p.name, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { choose(p) }.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                if (dest == null && results.isEmpty() && info == null) {
                    Text(
                        "Tip: mantené apretado un punto del mapa para ir ahí", color = Color(0xCCFFFFFF), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp, start = 6.dp)
                    )
                }
            }
        }

        // Atribución obligatoria de los datos del mapa (ODbL)
        Text(
            if (MapPrefs.engine.value == "3d") MapStyle.ATTRIBUTION else "© OpenStreetMap",
            color = Color(0xCCFFFFFF), fontSize = 10.sp, maxLines = 2,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).width(150.dp)
        )

        // Vista inclinada (3D) o plana, solo en el mapa 3D
        if (MapPrefs.engine.value == "3d") {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 110.dp).size(44.dp).clip(CircleShape)
                    .background(Color(0xF00B1120)).border(1.dp, CardBorder, CircleShape)
                    .clickable {
                        MapPrefs.tilted.value = !MapPrefs.tilted.value
                        MapPrefs.save(context)
                        map?.setTilted(MapPrefs.tilted.value)
                    },
                contentAlignment = Alignment.Center
            ) { Text(if (MapPrefs.tilted.value) "2D" else "3D", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }

        // v0.79.7: aviso cuando el GPS no da posiciones (el mapa muestra la última conocida, no dónde estás)
        GpsStatusPill(Modifier.align(Alignment.CenterStart).padding(start = 12.dp, top = 64.dp))

        // Centrar en mi ubicación
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(44.dp).clip(CircleShape).background(Color(0xF00B1120))
                .border(1.dp, CardBorder, CircleShape).clickable { here().let { map?.center(it.latitude, it.longitude) } },
            contentAlignment = Alignment.Center
        ) { Canvas(Modifier.size(22.dp)) { iconNav() } }

        // v0.77.0: paradas en el camino (navegando): categorías, resultados con el desvío y quitar la parada
        if (navigating && alongOpen) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 92.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xF20B1120)).border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in PlaceCategory.entries) QuickChip(c.label, Modifier.width(132.dp)) { searchCategory(c) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(alongTitle ?: "", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (stop != null) Text("Quitar parada", color = Color(0xFFFF8A80), fontSize = 13.sp,
                        modifier = Modifier.clickable { stop = null; alongOpen = false; dest?.let { routeTo(it) } }.padding(horizontal = 8.dp))
                    Text("Cerrar", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { alongOpen = false }.padding(horizontal = 8.dp))
                }
                for (p in alongResults.take(5)) {
                    Text(
                        p.name, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { addStop(p) }.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Destino: distancia y tiempo, Iniciar / Terminar, y guardar (★, Casa, Trabajo)
        val d = dest
        if (d != null || info != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF20B1120)).border(1.dp, CardBorder, RoundedCornerShape(16.dp)).padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (d != null) Text(
                            (stop?.let { "Parada: ${it.name.substringBefore(" · ")} → " } ?: "") + d.name,
                            color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        val line = if (navigating && g != null) {
                            val eta = java.util.Calendar.getInstance().apply { add(java.util.Calendar.SECOND, g.remainingS.toInt()) }
                            Guidance.shortDistance(g.remainingM) + " · llegada " + String.format(Locale.US, "%d:%02d",
                                eta.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }, eta.get(java.util.Calendar.MINUTE))
                        } else info
                        line?.let { Text(it, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (d != null && route != null) {
                        if (navigating) {
                            // v0.78.0: como Google Maps y Waze: todo / solo avisos / silencio
                            PillButton(AlertPrefs.mode.value.label, Track, TextMain, Modifier.width(130.dp)) {
                                AlertPrefs.cycleMode(context)
                                Voice.stopTalking()
                            }
                            Spacer(Modifier.width(8.dp))
                            // v0.77.0: paradas en el camino (combustible, comida, café…)
                            PillButton("Parada", Track, TextMain, Modifier.width(96.dp)) {
                                alongOpen = !alongOpen
                                if (alongOpen && alongResults.isEmpty()) searchCategory(PlaceCategory.FUEL)
                            }
                            Spacer(Modifier.width(8.dp))
                            PillButton("Terminar", Color(0xFFE5484D), Color.White, Modifier.width(110.dp)) { stopNavigation() }
                        } else {
                            PillButton("Iniciar", Accent, OnAccent, Modifier.width(110.dp)) { startNavigation() }
                        }
                    }
                    if (!navigating) {
                        Spacer(Modifier.width(8.dp))
                        PillButton("×", Track, TextMain, Modifier.width(44.dp)) {
                            dest = null; route = null; info = null
                            map?.showRoute(null, null, false)
                        }
                    }
                }
                if (d != null && !navigating) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fav = SavedPlaces.isFavorite(d)
                        val isHome = SavedPlaces.isNear(SavedPlaces.home.value, d, 100.0) || d.name.startsWith("Casa · ")
                        val isWork = SavedPlaces.isNear(SavedPlaces.work.value, d, 100.0) || d.name.startsWith("Trabajo · ")
                        val showHome = SavedPlaces.home.value == null && !isHome && !isWork
                        val showWork = SavedPlaces.work.value == null && !isWork && !isHome

                        QuickChip(
                            if (fav) "★ Guardado en Favoritos" else "☆ Guardar como Favorito",
                            Modifier.weight(if (showHome || showWork) 1.2f else 1f)
                        ) {
                            SavedPlaces.toggleFavorite(context, d)
                        }
                        if (showHome) {
                            QuickChip("Guardar como Casa", Modifier.weight(1.3f)) {
                                SavedPlaces.setHome(context, d); info = "Guardado como Casa"
                            }
                        }
                        if (showWork) {
                            QuickChip("Guardar como Trabajo", Modifier.weight(1.4f)) {
                                SavedPlaces.setWork(context, d); info = "Guardado como Trabajo"
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Flecha de la próxima maniobra para el cartel de navegación. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.maneuverArrow(modifier: String, type: String) {
    val angle = when {
        type == "arrive" || type == "waypoint" -> 0f
        modifier == "left" -> -90f
        modifier == "right" -> 90f
        modifier == "slight left" -> -45f
        modifier == "slight right" -> 45f
        modifier == "sharp left" -> -135f
        modifier == "sharp right" -> 135f
        modifier == "uturn" -> 180f
        else -> 0f
    }
    val w = size.width
    rotate(angle) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, w * 0.08f); lineTo(w * 0.86f, w * 0.48f); lineTo(w * 0.62f, w * 0.48f)
            lineTo(w * 0.62f, w * 0.92f); lineTo(w * 0.38f, w * 0.92f); lineTo(w * 0.38f, w * 0.48f)
            lineTo(w * 0.14f, w * 0.48f); close()
        }
        drawPath(p, if (type == "arrive" || type == "waypoint") Color(0xFF4ADE80) else Color.White)
    }
}

/**
 * "Buscando GPS…" / "Sin señal de GPS hace N s" sobre el mapa, solo cuando no llegan posiciones. Tiene su propio
 * estado para que, sin señal, se redibuje solo este cartelito y no todo el mapa.
 */
@Composable
private fun GpsStatusPill(modifier: Modifier) {
    var label by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            label = GpsFix.status(GpsFix.ageMs(android.os.SystemClock.elapsedRealtime()))
            kotlinx.coroutines.delay(1_000)
        }
    }
    label?.let {
        Text(
            it, color = Color(0xFFFFB86B), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE60B1120))
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** Botón chico de los accesos rápidos del mapa. */
@Composable
private fun QuickChip(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xF00B1120))
            .border(1.dp, CardBorder, RoundedCornerShape(17.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

/** AUD-055: cada cuánto se reintenta recalcular si sigue fuera de la ruta (por ejemplo, sin señal). */
private const val REROUTE_RETRY_MS = 30_000L

/** Espera antes de pedir de nuevo una ruta que falló por la red (v0.81.22). Pura. */
object RouteRetry {
    const val MAX = 20

    fun waitMs(failures: Int, netDown: Boolean): Long =
        if (!netDown) 3_000L else (30_000L * failures.coerceAtLeast(1)).coerceAtMost(120_000L)
}

/** Sugerencias del buscador mientras se escribe (v0.81.23). Pura. */
object LiveSearch {
    const val MIN_CHARS = 4
    const val DELAY_MS = 600L

    /** Si [query] amerita buscar: tiene largo suficiente y no es lo último que se buscó. */
    fun due(query: String, last: String): Boolean = query.trim().let { it.length >= MIN_CHARS && it != last.trim() }
}
