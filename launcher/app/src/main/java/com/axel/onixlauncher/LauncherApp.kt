package com.axel.onixlauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.Calendar
import com.carlink.core.connection.toLinkState

/**
 * Raíz de la app: junta los datos en vivo (hora, música, GPS, clima) y muestra las dos
 * pantallas (Inicio y Navegación/Música) sobre un lienzo fijo de 1024x600 que se escala a la pantalla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherApp() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    // v0.81.11: Diagnóstico es una categoría de Ajustes
    val openDiag: () -> Unit = { SettingsNav.show(SettingsCategory.DIAG) }
    var showAbout by remember { mutableStateOf(false) }
    var showRadios by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    // Música por eventos, sin consultar cada segundo (AUD-005)
    val media = rememberMedia()

    // Última fuente de audio: se anota cuando suena y se retoma al encender (v0.38.0)
    val spotifyPlaying = media.info?.let { it.pkg == SPOTIFY && it.playing } == true
    LaunchedEffect(spotifyPlaying) { if (spotifyPlaying) LastAudio.markSpotify(context) }
    val radioPlaying = RadioState.playing.value
    LaunchedEffect(radioPlaying) {
        if (radioPlaying) RadioState.current.value?.let { LastAudio.markRadio(context, it) }
    }
    // Sintonizador FM de la radio NWD (v0.51.0): solo existe en la radio del auto
    LaunchedEffect(Unit) { NwdTuner.bind(context) }
    // Mi auto (v0.79.0): el viaje termina al apagar el auto
    LaunchedEffect(Unit) { CarCare.register(context) }
    // Destinos desde el celular por ntfy (v0.76.0)
    LaunchedEffect(Unit) { PhoneShare.start(context) }
    // Modo liviano (v0.58.0): se decide al arrancar según el equipo
    remember { LiteMode.load(context); ThemePrefs.load(context); CrashLog.load(context); true }
    // Android Auto / CarPlay (AUD-036, v0.59.0): el motor no arranca sin micrófono y ubicación.
    // Se piden una vez al abrir (después del pedido de ubicación de la app) y la pantalla
    // Auto/CarPlay se abre sola si el motor se frena por algo que hay que autorizar.
    val askEngine = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("permisos", android.content.Context.MODE_PRIVATE)
        // La ubicación la pide el GPS al abrir; acá va el resto (micrófono), después, para no
        // superponer dos ventanas de permiso (AUD-057)
        val missing = com.carlink.core.connection.DevicePreflight.requiredPermissions()
            .filter { it != android.Manifest.permission.ACCESS_FINE_LOCATION && it != android.Manifest.permission.ACCESS_COARSE_LOCATION }
            .filter { androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !prefs.getBoolean("proyeccion_pedido", false)) {
            delay(6000)
            prefs.edit().putBoolean("proyeccion_pedido", true).apply()
            askEngine.launch(missing.toTypedArray())
        }
    }
    LaunchedEffect(Unit) {
        com.carlink.core.connection.Connection.get(context).state.collect { s ->
            val link = s.toLinkState()
            // v0.79.1: cada cambio del motor (y el motivo exacto si falla) para la Prueba completa
            if (link.stage != com.carlink.core.core.LinkStage.IDLE || EngineTrace.recent().isNotEmpty()) EngineTrace.add("Motor: " + EngineTrace.session(link))
            if (link.stage == com.carlink.core.core.LinkStage.FAILED) AppLog.warn("Auto/CarPlay: " + EngineTrace.session(link))
            if (Projection.needsDriver(link)) Projection.open.value = true
            // AUD-050: Android Auto y CarPlay suenan por el audio de Android; si sonaba el FM por la antena, se corta
            if (link.isActive) leaveAntenna(context)
        }
    }
    LaunchedEffect(Unit) {
        if (!AutoResume.done) {
            AutoResume.done = true
            delay(4000)   // que arranque el resto del equipo (Bluetooth, audio) antes de sonar
            RadioCatalog.load(context)
            // v0.79.9: si el contacto se puso antes de abrir la app, hay que devolver la fuente a Android
            if (!IgnitionAudio.restore(context)) LastAudio.resume(context)
        }
    }

    // Reloj: se actualiza al cambiar el minuto; cada segundo solo con Diagnóstico abierto
    val diagOpen by rememberUpdatedState(SettingsNav.showsDiagnostics())
    LaunchedEffect(Unit) {
        while (true) {
            val c = Calendar.getInstance()
            if (diagOpen || c.get(Calendar.MINUTE) != now.get(Calendar.MINUTE) ||
                c.get(Calendar.HOUR_OF_DAY) != now.get(Calendar.HOUR_OF_DAY)
            ) now = c
            delay(1000L - c.get(Calendar.MILLISECOND) % 1000)
        }
    }

    // Clima real cada 15 minutos (cada 1 minuto mientras no haya datos), y de inmediato si el auto
    // se alejó más de 3 km del punto del último pedido (v0.79.8)
    var weather by remember { mutableStateOf<Weather?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val loc = lastLocation(context)
            val lat = loc?.latitude ?: DEFAULT_LAT
            val lon = loc?.longitude ?: DEFAULT_LON
            val w = withContext(Dispatchers.IO) { fetchWeather(lat, lon) }
            if (w != null) weather = w
            withContext(Dispatchers.IO) { placeName(context, loc) }?.let { place = it }
            // Espera el próximo turno, o menos si la red vuelve después de un corte (v0.40.0)
            // o si se movió lejos (se mira cada 30 s)
            val seen = NetWatch.back.intValue
            withTimeoutOrNull(if (weather == null) 60_000L else 15 * 60_000L) {
                merge(
                    snapshotFlow { NetWatch.back.intValue }.filter { it != seen }.map { },
                    flow {
                        while (true) {
                            delay(PlaceRefresh.CHECK_MS)
                            val now = lastLocation(context)
                            if (now != null && PlaceRefresh.movedFar(lat, lon, now.latitude, now.longitude)) emit(Unit)
                        }
                    },
                ).first()
            }
        }
    }

    // OBD2 (lector ELM327 por Bluetooth): la velocidad del auto tiene prioridad sobre la del GPS
    val obd = rememberObd()
    val gpsSpeed = rememberGpsSpeed()
    val speed = obd?.speedKmh ?: gpsSpeed
    // Viajes de Mi auto (v0.52.0): los lleva el motor del auto, que vive con el proceso y no con
    // esta pantalla (v0.81.12, ADR-0018)
    LaunchedEffect(Unit) { CarEngine.startTrips(context) }
    val hour = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f
    val ambient = ambientFor(hour, weather?.raining == true)
    // Luces: por defecto siguen el modo noche de la radio (cable ILL a la perilla de luces)
    var lightsMode by remember { mutableStateOf(LightsPrefs.load(context)) }
    // Señal de la radio: modo noche del sistema o, en la radio NWD, el ajuste high_beam_state (v0.45.0)
    val systemNight = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES || RadioIll.on.value
    val lightsOn = lightsOn(lightsMode, systemNight, ambient)
    ThemePrefs.syncAuto(lightsOn, ambient)
    // Brillo al máximo para la ventana del launcher (v0.48.0)
    ScreenBrightness.load(context)
    val forceMax = ScreenBrightness.forceMax.value
    LaunchedEffect(forceMax) {
        (context as? android.app.Activity)?.window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = ScreenBrightness.windowValue(forceMax) }
        }
    }
    val cycleLights: () -> Unit = {
        lightsMode = lightsMode.next()
        LightsPrefs.save(context, lightsMode)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val k = minOf(maxWidth.value / 1024f, maxHeight.value / 600f)
        Box(
            Modifier
                .requiredSize(1024.dp, 600.dp)
                .graphicsLayer { scaleX = k; scaleY = k }
        ) {
            // Dos pantallas: inicio y navegación con música, se cambia con los botones
            val pager = rememberPagerState(pageCount = { 2 })
            val scope = rememberCoroutineScope()
            val goHome: () -> Unit = { scope.launch { pager.animateScrollToPage(0) } }
            val goNav: () -> Unit = { scope.launch { pager.animateScrollToPage(1) } }
            // Atrás desde la pantalla de navegación y música: vuelve a Inicio
            androidx.activity.compose.BackHandler(enabled = pager.currentPage == 1 && !showRadios && !showApps && !showAbout && !SettingsNav.open.value) {
                goHome()
            }
            HorizontalPager(
                state = pager, modifier = Modifier.fillMaxSize(),
                // Sin deslizar (v0.48.0): se cambia de pantalla solo con los botones (Navegación, Música, Inicio)
                userScrollEnabled = false
            ) { page ->
                if (page == 0) {
                    HomeScreen(
                        now, ambient, lightsOn, media, speed, weather, obd = obd, place = place,
                        editing = editing, onEditingChange = { editing = it },
                        onRadios = { showRadios = true }, onHome = goHome,
                        onApps = { showApps = true }, onNav = goNav, lightsMode = lightsMode,
                        onDiag = openDiag
                    )
                } else {
                    NavMusicScreen(
                        now, media, weather,
                        onRadios = { showRadios = true }, onHome = goHome,
                        onApps = { showApps = true }, onDiag = openDiag
                    )
                }
            }
            // v0.72.0: sin los puntitos de página; desde v0.48.0 no se desliza (pedido del autor)
            if (showRadios) RadiosOverlay(onClose = { showRadios = false })
            if (showApps) AppsOverlay(onClose = { showApps = false })
            // Selector del dock: el lugar vacío que se tocó recibe la app o el acceso elegido
            DockStore.picking.value?.let { index ->
                AppsOverlay(
                    onClose = { DockStore.picking.value = null },
                    onPick = { slot ->
                        DockStore.set(context, index, slot)
                        DockStore.picking.value = null
                    }
                )
            }
            // Mi auto: viajes, kilometraje y recordatorios (v0.52.0)
            if (CarCare.open.value) CarCareOverlay(onClose = { CarCare.open.value = false })
            // Android Auto / CarPlay (v0.56.0): estado del motor e indicaciones
            if (Projection.open.value) ProjectionOverlay(onClose = { Projection.open.value = false })
            // Nafta baja (v0.77.0): "Buscar" abre el mapa con estaciones (en el camino si se navega)
            FuelOfferOverlay(onSearch = {
                showRadios = false; showApps = false
                NavRequest.category.value = PlaceCategory.FUEL
                goNav()
            })
            // Mis lugares (v0.77.0): Casa, Trabajo y favoritos escribiendo la dirección
            if (NavRequest.editPlaces.value) {
                PlacesEditor(onClose = { NavRequest.editPlaces.value = false }, near = {
                    lastLocation(context)?.let { it.latitude to it.longitude } ?: (DEFAULT_LAT to DEFAULT_LON)
                })
            }
            // Destino que llegó del celular (v0.76.0): "Ir" abre la navegación y arranca la guía
            PhoneShareOverlay(onGo = { p ->
                showRadios = false; showApps = false
                NavRequest.startNow = true
                NavRequest.pending.value = p
                goNav()
            })
            // Avisos de seguridad: luces apagadas andando y alertas del motor (v0.50.0)
            SafetyOverlay(speed)
            // Presentación al abrir y al volver de la suspensión (v0.39.0)
            // Sonido de inicio (v0.65.0)
            LaunchedEffect(Intro.visible.value) { if (Intro.visible.value) IntroSound.onIntro(context) }
            if (Intro.visible.value) IntroOverlay(onDone = { Intro.visible.value = false })
        }
        if (showAbout) AboutOverlay(onClose = { showAbout = false })
        if (SettingsNav.open.value) {
            SettingsOverlay(
                lightsMode = lightsMode,
                onLightsMode = cycleLights,
                onAbout = { SettingsNav.close(); showAbout = true },
                onClose = { SettingsNav.close() },
                diagnostics = { DiagnosticsPanel(now, ambient, lightsOn, media, speed, weather, lightsMode) }
            )
        }
    }
}
