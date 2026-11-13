package com.axel.onixlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.mutableStateOf
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/*
 * Mapa 3D (v0.47.0, primera etapa del mapa propio inspirado en la idea de Horizon-Maps, sin usar su
 * código): MapLibre 10.x, el último que funciona con el OpenGL ES 2.0 de la radio, con mapas
 * vectoriales gratis y sin clave de OpenFreeMap (esquema OpenMapTiles, datos de OpenStreetMap).
 * Estilo oscuro propio con edificios en 3D, inclinación y giro con el auto. Necesita internet; la
 * etapa sin internet usará el mismo esquema desde un paquete de Tucumán (repo público aparte).
 *
 * Si MapLibre no arranca o el estilo no carga, se vuelve solo al mapa clásico (osmdroid). Las dos
 * implementaciones comparten la interfaz [NavMap], que es lo único que usa la pantalla del mapa.
 */

/** Lo que la pantalla de navegación le pide al mapa, sea 3D (MapLibre) o clásico (osmdroid). */
interface NavMap {
    val view: View
    /** Centra en un punto (botón "mi ubicación"). */
    fun center(lat: Double, lon: Double)
    /** Durante la navegación: sigue al auto y gira con su rumbo (null = sin rumbo confiable). */
    fun follow(lat: Double, lon: Double, bearing: Float?)
    /** Al terminar la navegación: norte arriba y zoom de ciudad. */
    fun resetView()
    /** Dibuja (o borra) el destino y la ruta; [fit] encuadra la ruta. */
    fun showRoute(dest: Place?, route: Route?, fit: Boolean)
    /** Cambia entre vista inclinada (3D) y plana. Solo el mapa 3D. */
    fun setTilted(tilted: Boolean) {}
    fun release()
}

/** Preferencias del mapa (SharedPreferences `mapa`): motor y vista inclinada. */
object MapPrefs {
    /** "3d" (MapLibre, por defecto) o "clasico" (osmdroid). */
    val engine = mutableStateOf("3d")
    val tilted = mutableStateOf(true)
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences("mapa", Context.MODE_PRIVATE)
        // Modo liviano (v0.58.0): si nunca se eligió, arranca en el mapa clásico (el 3D es lo más pesado)
        LiteMode.load(c)
        val default = if (LiteMode.isOn) "clasico" else "3d"
        engine.value = p.getString("motor", default) ?: default
        tilted.value = p.getBoolean("inclinado", true)
    }

    fun save(c: Context) {
        c.getSharedPreferences("mapa", Context.MODE_PRIVATE).edit()
            .putString("motor", engine.value).putBoolean("inclinado", tilted.value).apply()
    }
}

/** Estilo oscuro del mapa 3D (funciones puras, para los tests). */
object MapStyle {
    const val TILES = "https://tiles.openfreemap.org/planet"
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles · datos © colaboradores de OpenStreetMap"

    /** Inclinación de la cámara: 3D en la vista inclinada, más todavía navegando. */
    fun tilt(tilted: Boolean, navigating: Boolean): Double = when {
        !tilted -> 0.0
        navigating -> 60.0
        else -> 45.0
    }

    /**
     * Estilo MapLibre (JSON) con el esquema OpenMapTiles: agua, parques, calles por categoría,
     * edificios en 3D (altura real de OpenStreetMap) y nombres de calles y localidades.
     */
    fun json(tiles: String = TILES): String = """
{
  "version": 8,
  "name": "Onix oscuro",
  "glyphs": "$GLYPHS",
  "sources": { "omt": { "type": "vector", "url": "$tiles" } },
  "layers": [
    { "id": "fondo", "type": "background", "paint": { "background-color": "#0B1322" } },
    { "id": "parques", "type": "fill", "source": "omt", "source-layer": "park",
      "paint": { "fill-color": "#10241C", "fill-opacity": 0.8 } },
    { "id": "verde", "type": "fill", "source": "omt", "source-layer": "landcover",
      "filter": ["in", "class", "grass", "wood", "farmland"],
      "paint": { "fill-color": "#0F1F1A", "fill-opacity": 0.6 } },
    { "id": "agua", "type": "fill", "source": "omt", "source-layer": "water",
      "paint": { "fill-color": "#0A2A44" } },
    { "id": "rios", "type": "line", "source": "omt", "source-layer": "waterway",
      "paint": { "line-color": "#0A2A44", "line-width": 1.5 } },
    { "id": "calles-chicas", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "minor", "service", "track"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#24324A", "line-width": ["interpolate", ["linear"], ["zoom"], 13, 0.5, 16, 4, 18, 10] } },
    { "id": "calles", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "tertiary", "secondary"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#33466A", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 16, 7, 18, 14] } },
    { "id": "avenidas", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "primary", "trunk", "motorway"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#4A5F8C", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1, 16, 9, 18, 18] } },
    { "id": "edificios-3d", "type": "fill-extrusion", "source": "omt", "source-layer": "building", "minzoom": 14,
      "paint": {
        "fill-extrusion-color": "#1C2A44",
        "fill-extrusion-height": ["coalesce", ["get", "render_height"], 6],
        "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
        "fill-extrusion-opacity": 0.85
      } },
    { "id": "nombres-calles", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 14,
      "layout": { "symbol-placement": "line", "text-field": ["coalesce", ["get", "name:es"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 12 },
      "paint": { "text-color": "#9FB0CC", "text-halo-color": "#0B1322", "text-halo-width": 1.5 } },
    { "id": "localidades", "type": "symbol", "source": "omt", "source-layer": "place",
      "filter": ["in", "class", "city", "town", "village", "suburb", "neighbourhood"],
      "layout": { "text-field": ["coalesce", ["get", "name:es"], ["get", "name"]],
                  "text-font": ["Noto Sans Bold"], "text-size": ["match", ["get", "class"], "city", 18, "town", 15, 12] },
      "paint": { "text-color": "#E4EAF5", "text-halo-color": "#0B1322", "text-halo-width": 2 } }
  ]
}
""".trimIndent()
}

/**
 * Ubicación propia por GPS, para el punto azul del mapa 3D. v0.79.7: cada 1 s y sin mínimo de metros, igual que el
 * velocímetro y la guía (antes, como mucho cada 2 s o 5 m: a 40 km/h el punto saltaba unos 22 m entre cada una).
 */
@SuppressLint("MissingPermission")
private class GpsDot(context: Context, private val onFix: (Location) -> Unit) : LocationListener {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    fun start() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
        } catch (e: Exception) {
            AppLog.warn("Mapa: sin GPS", e)
        }
    }
    fun stop() = try { lm.removeUpdates(this) } catch (_: Exception) {}
    override fun onLocationChanged(location: Location) = onFix(location)
    @Deprecated("Requerido en Android 7")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

/** Mapa 3D con MapLibre. [onFail] se llama si el estilo no carga (se vuelve al mapa clásico). */
class LibreNavMap(
    context: Context,
    start: Pair<Double, Double>,
    private val onLongPress: (Double, Double) -> Unit,
    private val onFail: (String) -> Unit
) : NavMap {
    private val mapView: com.mapbox.mapboxsdk.maps.MapView
    private var map: com.mapbox.mapboxsdk.maps.MapboxMap? = null
    private var style: com.mapbox.mapboxsdk.maps.Style? = null
    private var tilted = MapPrefs.tilted.value
    private var navigating = false
    private var centered = false
    /** Ruta o destino pedidos antes de que cargue el estilo: se dibujan al cargar. */
    private var pendingRoute: Triple<Place?, Route?, Boolean>? = null
    private val gps: GpsDot
    override val view: View get() = mapView

    init {
        com.mapbox.mapboxsdk.Mapbox.getInstance(context.applicationContext)
        val options = com.mapbox.mapboxsdk.maps.MapboxMapOptions.createFromAttributes(context)
            .attributionEnabled(false).logoEnabled(false).compassEnabled(false)
            .camera(camera(start.first, start.second, 14.5, 0.0))
        mapView = com.mapbox.mapboxsdk.maps.MapView(context, options)
        mapView.addOnDidFailLoadingMapListener { msg -> onFail(msg ?: "el mapa no cargó") }
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            m.uiSettings.isTiltGesturesEnabled = true
            m.addOnMapLongClickListener { p -> onLongPress(p.latitude, p.longitude); true }
            m.setStyle(com.mapbox.mapboxsdk.maps.Style.Builder().fromJson(MapStyle.json())) { s ->
                style = s
                addOverlays(s)
            }
        }
        gps = GpsDot(context) { loc ->
            setPoint("yo", loc.longitude, loc.latitude)
            if (!centered && !navigating) {
                centered = true
                center(loc.latitude, loc.longitude)
            }
        }
        gps.start()
    }

    private fun camera(lat: Double, lon: Double, zoom: Double, bearing: Double) =
        com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
            .target(com.mapbox.mapboxsdk.geometry.LatLng(lat, lon))
            .zoom(zoom).bearing(bearing).tilt(MapStyle.tilt(tilted, navigating)).build()

    private fun addOverlays(s: com.mapbox.mapboxsdk.maps.Style) {
        s.addSource(com.mapbox.mapboxsdk.style.sources.GeoJsonSource("ruta"))
        s.addSource(com.mapbox.mapboxsdk.style.sources.GeoJsonSource("destino"))
        s.addSource(com.mapbox.mapboxsdk.style.sources.GeoJsonSource("yo"))
        s.addLayer(
            com.mapbox.mapboxsdk.style.layers.LineLayer("ruta-borde", "ruta").withProperties(
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor("#0B1322"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth(11f),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
            )
        )
        s.addLayer(
            com.mapbox.mapboxsdk.style.layers.LineLayer("ruta-linea", "ruta").withProperties(
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor("#4CA6FF"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth(7f),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
            )
        )
        s.addLayer(
            com.mapbox.mapboxsdk.style.layers.CircleLayer("destino-punto", "destino").withProperties(
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor("#FF5A5F"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius(9f),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeWidth(3f)
            )
        )
        s.addLayer(
            com.mapbox.mapboxsdk.style.layers.CircleLayer("yo-punto", "yo").withProperties(
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor("#4C8DFF"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius(8f),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeWidth(3f),
                com.mapbox.mapboxsdk.style.layers.PropertyFactory.circlePitchAlignment(com.mapbox.mapboxsdk.style.layers.Property.CIRCLE_PITCH_ALIGNMENT_MAP)
            )
        )
        // Si la ruta o el destino llegaron antes que el estilo, se dibujan ahora
        pendingRoute?.let { (d, r, fit) -> showRoute(d, r, fit) }
    }

    private fun setPoint(source: String, lon: Double, lat: Double) {
        style?.getSourceAs<com.mapbox.mapboxsdk.style.sources.GeoJsonSource>(source)
            ?.setGeoJson(com.mapbox.geojson.Point.fromLngLat(lon, lat))
    }

    override fun center(lat: Double, lon: Double) {
        val m = map ?: return
        m.animateCamera(
            com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(camera(lat, lon, 15.5, 0.0)), 400
        )
    }

    override fun follow(lat: Double, lon: Double, bearing: Float?) {
        navigating = true
        val m = map ?: return
        val b = bearing?.toDouble() ?: m.cameraPosition.bearing
        m.animateCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(camera(lat, lon, 17.0, b)), 350)
        setPoint("yo", lon, lat)
    }

    override fun resetView() {
        navigating = false
        val m = map ?: return
        val t = m.cameraPosition.target ?: return
        m.animateCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(camera(t.latitude, t.longitude, 15.0, 0.0)), 900)
    }

    override fun showRoute(dest: Place?, route: Route?, fit: Boolean) {
        val s = style
        if (s == null) {
            pendingRoute = Triple(dest, route, fit)
            return
        }
        pendingRoute = null
        val line = route?.points?.takeIf { it.size >= 2 }?.map { com.mapbox.geojson.Point.fromLngLat(it.second, it.first) }
        s.getSourceAs<com.mapbox.mapboxsdk.style.sources.GeoJsonSource>("ruta")?.setGeoJson(
            com.mapbox.geojson.FeatureCollection.fromFeatures(
                listOfNotNull(line?.let { com.mapbox.geojson.Feature.fromGeometry(com.mapbox.geojson.LineString.fromLngLats(it)) })
            )
        )
        s.getSourceAs<com.mapbox.mapboxsdk.style.sources.GeoJsonSource>("destino")?.setGeoJson(
            com.mapbox.geojson.FeatureCollection.fromFeatures(
                listOfNotNull(dest?.let { com.mapbox.geojson.Feature.fromGeometry(com.mapbox.geojson.Point.fromLngLat(it.lon, it.lat)) })
            )
        )
        val m = map ?: return
        if (fit && line != null && route != null) {
            val b = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
                .includes(route.points.map { com.mapbox.mapboxsdk.geometry.LatLng(it.first, it.second) }).build()
            m.easeCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(b, 60), 900)
        } else if (fit && dest != null) {
            center(dest.lat, dest.lon)
        }
    }

    override fun setTilted(tilted: Boolean) {
        this.tilted = tilted
        val m = map ?: return
        val p = m.cameraPosition
        val t = p.target ?: return
        m.animateCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(camera(t.latitude, t.longitude, p.zoom, p.bearing)), 600)
    }

    override fun release() {
        gps.stop()
        try {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        } catch (e: Exception) {
            AppLog.warn("Mapa 3D: error al cerrar", e)
        }
    }
}

/** Mapa clásico (osmdroid, mosaicos de OpenStreetMap invertidos a oscuro): respaldo del 3D. */
class OsmNavMap(context: Context, start: Pair<Double, Double>, onLongPress: (Double, Double) -> Unit) : NavMap {
    private val mapView: org.osmdroid.views.MapView
    private val me: MyLocationNewOverlay
    override val view: View get() = mapView

    init {
        Configuration.getInstance().apply {
            userAgentValue = "OnixLauncher/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            // Caché permanente (Android no la borra) de hasta 500 MB: lo que ya se vio del
            // mapa queda disponible sin conexión (v0.36.0)
            osmdroidTileCache = File(context.filesDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 500L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 450L * 1024 * 1024
        }
        mapView = org.osmdroid.views.MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)   // mapa oscuro
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(start.first, start.second))
            // Mantener apretado un punto: elegirlo como destino
            overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p?.let { onLongPress(it.latitude, it.longitude) }
                    return p != null
                }
            }))
        }
        // Solo GPS: sin esto pedía ubicación por red sin parar y cargaba los servicios de Google (log del
        // head unit, v0.35.2). Eso lo resuelve "solo GPS"; v0.79.7: cada 1 s y sin mínimo de metros (antes 2 s o
        // 5 m), como el otro mapa, el velocímetro y la guía.
        val provider = GpsMyLocationProvider(context).apply {
            clearLocationSources()
            addLocationSource(LocationManager.GPS_PROVIDER)
            locationUpdateMinTime = 1000
            locationUpdateMinDistance = 0f
        }
        me = MyLocationNewOverlay(provider, mapView)
        me.enableMyLocation()
        me.enableFollowLocation()
        mapView.overlays.add(me)
    }

    override fun center(lat: Double, lon: Double) = mapView.controller.animateTo(GeoPoint(lat, lon))

    override fun follow(lat: Double, lon: Double, bearing: Float?) {
        if (mapView.zoomLevelDouble < 17.0) mapView.controller.setZoom(17.5)
        mapView.controller.animateTo(GeoPoint(lat, lon))
        if (bearing != null) mapView.mapOrientation = -bearing
    }

    override fun resetView() {
        mapView.mapOrientation = 0f
        mapView.controller.setZoom(15.0)
    }

    override fun showRoute(dest: Place?, route: Route?, fit: Boolean) {
        mapView.overlays.removeAll { it is Polyline || it is Marker }
        if (route != null) {
            val line = Polyline(mapView).apply {
                setPoints(route.points.map { GeoPoint(it.first, it.second) })
                outlinePaint.color = android.graphics.Color.parseColor("#4CA6FF")
                outlinePaint.strokeWidth = 12f
            }
            mapView.overlays.add(line)
            if (fit) line.bounds?.let { mapView.zoomToBoundingBox(it.increaseByScale(1.3f), true) }
        }
        if (dest != null) {
            val m = Marker(mapView).apply {
                position = GeoPoint(dest.lat, dest.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = dest.name
            }
            mapView.overlays.add(m)
            if (route == null && fit) mapView.controller.animateTo(m.position)
        }
        mapView.invalidate()
    }

    override fun release() {
        me.disableMyLocation()
        mapView.onDetach()
    }
}
