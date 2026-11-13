package com.axel.onixlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/*
 * Lugares cerca y paradas en el camino (v0.77.0, pedido del autor; idea como la de Horizon Nav,
 * sin su código). Con TomTom: categorías cerca de la ubicación (nearbySearch) y, navegando,
 * lugares sobre la ruta que falta con el desvío en minutos (searchAlongRoute). También "Mis lugares"
 * (Casa, Trabajo y favoritos escribiendo la dirección) y el aviso de nafta baja con el OBD2.
 */

/** Categorías de TomTom (códigos de categoría de POI). */
enum class PlaceCategory(val label: String, val codes: String, val along: String) {
    FUEL("⛽ Combustible", "7311", "estación de servicio"),
    FOOD("🍔 Comida", "7315", "restaurante"),
    CAFE("☕ Café", "9376", "café"),
    PARKING("🅿 Estacionar", "7369,7313", "estacionamiento"),
    PHARMACY("💊 Farmacia", "9565", "farmacia"),
    ATM("🏧 Cajero", "7397", "cajero"),
    REPAIR("🔧 Taller", "7310", "taller")
}

object Nearby {
    private const val BASE = "https://api.tomtom.com/search/2"
    private fun ll(v: Double) = String.format(Locale.US, "%.6f", v)
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Los más cercanos de [c] a [lat], [lon] (hasta 10 km), ordenados por distancia. */
    fun nearbyUrl(c: PlaceCategory, lat: Double, lon: Double, key: String = NavApi.key): String =
        "$BASE/nearbySearch/.json?key=${enc(key)}&lat=${ll(lat)}&lon=${ll(lon)}&radius=10000&limit=10" +
            "&categorySet=${c.codes}&language=es-419"

    /** Sobre la ruta que falta, con hasta 15 min de desvío (pedido POST con los puntos de la ruta). */
    fun alongUrl(c: PlaceCategory, key: String = NavApi.key): String =
        "$BASE/searchAlongRoute/${enc(c.along)}.json?key=${enc(key)}&maxDetourTime=900&limit=10" +
            "&categorySet=${c.codes}&spreadingMode=auto&language=es-419"

    /** Cuerpo del pedido "en el camino": hasta 150 puntos parejos de la ruta (TomTom pide pocos). */
    fun alongBody(points: List<Pair<Double, Double>>): String {
        val n = points.size
        val picked = if (n <= 150) points else (0 until 150).map { points[(it.toLong() * (n - 1) / 149).toInt()] }
        val arr = JSONArray()
        for ((lat, lon) in picked) arr.put(JSONObject().put("lat", lat).put("lon", lon))
        return JSONObject().put("route", JSONObject().put("points", arr)).toString()
    }

    private fun distanceLabel(m: Double): String =
        if (m >= 1000) String.format(Locale("es", "AR"), "%.1f km", m / 1000) else "${m.toInt()} m"

    /** "Marca · calle y altura · 1,2 km" (o "· desvío 3 min"), del más cercano al más lejano. */
    fun parse(json: String): List<Place> = try {
        val arr = JSONObject(json).getJSONArray("results")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONObject("position") ?: return@mapNotNull null
            val a = o.optJSONObject("address") ?: JSONObject()
            val name = o.optJSONObject("poi")?.optString("name").orEmpty()
            val street = listOf(a.optString("streetName"), a.optString("streetNumber")).filter { it.isNotBlank() }.joinToString(" ")
            val sort: Double
            val extra = when {
                o.has("detourTime") -> { sort = o.optDouble("detourTime"); "desvío ${Math.round(sort / 60.0).coerceAtLeast(1)} min" }
                o.has("dist") -> { sort = o.optDouble("dist"); distanceLabel(sort) }
                else -> { sort = Double.MAX_VALUE; "" }
            }
            val label = listOf(name, street, extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (label.isEmpty()) null else sort to Place(label, p.getDouble("lat"), p.getDouble("lon"))
        }.sortedBy { it.first }.map { it.second }
    } catch (e: Exception) {
        emptyList()
    }

    /** Cerca de la ubicación. Null sin conexión o sin clave. Bloquea. */
    fun nearby(c: PlaceCategory, lat: Double, lon: Double): List<Place>? {
        if (NavApi.key.isBlank()) return null
        val r = Net.request("GET", nearbyUrl(c, lat, lon)) ?: DirectHttps.get(nearbyUrl(c, lat, lon)) ?: return null
        return if (r.first in 200..299) parse(r.second) else null
    }

    /** Sobre los puntos de la ruta que falta. Null sin conexión o sin clave. Bloquea. */
    fun along(c: PlaceCategory, remaining: List<Pair<Double, Double>>): List<Place>? {
        if (NavApi.key.isBlank() || remaining.size < 2) return null
        val r = Net.request("POST", alongUrl(c), alongBody(remaining)) ?: return null
        return if (r.first in 200..299) parse(r.second) else null
    }
}

/** Cuándo ofrecer buscar combustible por nafta baja (OBD2). Pura, para los tests. */
object FuelWatch {
    const val LOW_PCT = 12
    const val EVERY_MS = 30 * 60_000L
    fun shouldOffer(pct: Int?, moving: Boolean, lastOfferMs: Long, nowMs: Long): Boolean =
        pct != null && pct <= LOW_PCT && moving && (lastOfferMs == 0L || nowMs - lastOfferMs >= EVERY_MS)

    /** Aviso en pantalla (con el porcentaje); "Buscar" abre la navegación con combustible. */
    val offer = mutableStateOf<Int?>(null)
}

/** Cartel de nafta baja (v0.77.0), arriba de cualquier pantalla. */
@Composable
internal fun FuelOfferOverlay(onSearch: () -> Unit) {
    val pct = FuelWatch.offer.value ?: return
    Box(Modifier.size(1024.dp, 600.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(top = 70.dp).width(520.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF2C77700))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nafta baja ($pct %)", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("¿Busco una estación de servicio en el camino?", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Buscar", Color.White, Color(0xFF7A4A00), Modifier.width(170.dp)) {
                    FuelWatch.offer.value = null
                    onSearch()
                }
                PillButton("Ahora no", Color(0x33FFFFFF), Color.White, Modifier.width(150.dp)) { FuelWatch.offer.value = null }
            }
        }
    }
}

/**
 * Mis lugares (v0.77.0, pedido del autor): Casa, Trabajo y favoritos escribiendo la dirección; se
 * busca con TomTom y se guarda lo que encuentra (se muestra para confirmar). Los favoritos se borran con ×.
 */
@Composable
internal fun PlacesEditor(onClose: () -> Unit, near: () -> Pair<Double, Double>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf("") }
    var work by remember { mutableStateOf("") }
    var favName by remember { mutableStateOf("") }
    var favAddr by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { SavedPlaces.load(context) }

    fun find(text: String, save: (Place) -> Unit) {
        if (text.trim().length < 3 || busy) return
        busy = true
        msg = "Buscando…"
        scope.launch {
            val (lat, lon) = near()
            val found = withContext(Dispatchers.IO) { NavApi.search(text, lat, lon) }
            busy = false
            val p = found?.firstOrNull()
            msg = when {
                found == null -> if (NavApi.key.isBlank()) "Falta la clave de TomTom" else "Sin conexión para buscar"
                p == null -> "No encontré \"${text.trim()}\""
                else -> { save(p); "Guardado: ${p.name}" }
            }
        }
    }

    Box(
        Modifier.size(1024.dp, 600.dp).background(Color(0xE6040612))
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(720.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111634))
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            Text("Mis lugares", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Escribí la dirección y tocá Guardar", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            EditorRow("Casa", SavedPlaces.home.value?.name?.removePrefix("Casa · "), home, { home = it }) {
                find(home) { SavedPlaces.setHome(context, it); home = "" }
            }
            EditorRow("Trabajo", SavedPlaces.work.value?.name?.removePrefix("Trabajo · "), work, { work = it }) {
                find(work) { SavedPlaces.setWork(context, it); work = "" }
            }
            Spacer(Modifier.height(6.dp))
            Text("Favorito nuevo", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorField(favName, { favName = it }, "Nombre (ej.: Mamá)", Modifier.width(190.dp))
                EditorField(favAddr, { favAddr = it }, "Dirección", Modifier.weight(1f))
                PillButton("Guardar", Accent, OnAccent, Modifier.width(110.dp)) {
                    find(favAddr) { p ->
                        val named = if (favName.isBlank()) p else p.copy(name = favName.trim() + " · " + p.name)
                        SavedPlaces.addFavorite(context, named)
                        favName = ""; favAddr = ""
                    }
                }
            }
            for (f in SavedPlaces.favorites.value.take(5)) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("★ " + f.name, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { SavedPlaces.toggleFavorite(context, f) }.padding(horizontal = 10.dp))
                }
            }
            msg?.let { Text(it, color = if (it.startsWith("Guardado")) Color(0xFF4ADE80) else Muted, fontSize = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(10.dp))
            PillButton("Listo", Track, TextMain, Modifier.width(140.dp).align(Alignment.End), onClose)
        }
    }
}

@Composable
private fun EditorRow(title: String, current: String?, value: String, onValue: (String) -> Unit, onSave: () -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title + (current?.let { ": $it" } ?: ": sin guardar"), color = if (current != null) TextMain else Muted, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(value, onValue, "Escribí la dirección de $title".lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
            PillButton("Guardar", Accent, OnAccent, Modifier.width(110.dp), onSave)
        }
    }
}

@Composable
private fun EditorField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(
        modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xF00B1120))
            .border(1.dp, CardBorder, RoundedCornerShape(21.dp)).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = Muted, fontSize = 15.sp, maxLines = 1)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 15.sp), cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
