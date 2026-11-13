# Arquitectura — Onix Launcher

**Versión documentada:** v0.81.25 · **Fecha:** 2026-11-12 (revisada contra el código en la auditoría `docs/audits/AUDIT-2026-11-05.md`, sección L)

## Vista general

```text
MainActivity (pantalla completa, horizontal) ─ Tls.install; registra NwdSignals, AutoResume, Intro, NetWatch y RadioIll
└── LauncherApp ─ estado global: reloj, música (eventos), clima, GPS, modo edición; además es dueño de los
    trabajos de fondo que viven mientras existe la pantalla: PhoneShare (ntfy), NwdTuner.bind, permisos
    del motor y el estado de Connection (CarLink). El bucle del OBD2 y CarCare.tick son de CarEngine,
    que vive con el proceso (v0.81.12, ADR-0018)
    ├── Lienzo fijo 1024x600 dp, escalado a la pantalla real
    │   ├── HorizontalPager (sin deslizar: se cambia con los botones; Atrás vuelve a Inicio)
    │   │   ├── HomeScreen ─ fondo (cielo, cerros, ciudad y piso) + auto + paneles + Dock + franja
    │   │   │   ├── HeaderCard (reloj 12 h, clima, lugar) · VehicleStrip (emblema, saludo, luces)
    │   │   │   ├── ForecastCard · MediaCard/RadioCard · NavCard · EnginePanel   (EditablePanel)
    │   │   │   ├── CodesDialog ─ códigos de falla (al tocar la línea de códigos del panel de motor)
    │   │   │   ├── RainLayer
    │   │   │   └── Dock (8 accesos: Inicio … Apps … Ajustes)
    │   │   └── NavMusicScreen ─ MapPanel (NavMap: 3D MapLibre o clásico osmdroid; buscador, ruta, Ir) + MusicPanel (Spotify con biblioteca / Radio) + Dock
    │   ├── RadiosOverlay → FmRadioPanel (display, dial, categorías, presintonías, favoritas; ANTENA / INTERNET → NwdTuner)
    │   ├── AppsOverlay ─ cajón de apps (PackageManager); también selector de apps del dock
    │   ├── CarCareOverlay ─ Mi auto: Mantenimiento · Viajes · Motor (desde el dock)
    │   ├── ProjectionOverlay ─ Auto/CarPlay: estado del motor de CarLink
    │   ├── FuelOfferOverlay ─ nafta baja: ofrece estaciones cerca · PlacesEditor ─ Mis lugares
    │   ├── PhoneShareOverlay ─ "¿Ir a …?" (destino que llegó del celular por ntfy)
    │   ├── SafetyOverlay ─ avisos de seguridad: luces apagadas andando, alertas del motor (cartel, pitido, voz)
    │   └── IntroOverlay ─ presentación (al abrir y al prenderse la pantalla)
    ├── rememberObd ─ pide el permiso y arranca CarEngine (de proceso): lector OBD2 (hilo de E/S) → ObdReader: datos, consumo, códigos, alertas
    ├── DiagnosticsOverlay (fuera del lienzo escalado) ──► AboutOverlay · Prueba completa (FullReport → .txt en Descargas)
    └── AboutOverlay

OnixApp (Application, todos los procesos) ─ CarLinkCore.install → motor de CarLink
├── :carlink-core ─ UsbRouterActivity (USB), SessionManager (una sesión), registro
├── DiAuto (Android Auto, proceso :aa) ─ su pantalla de proyección
└── DiPlay (CarPlay, proceso principal) ─ su pantalla de proyección

Servicios
├── RadioService (Media3: ExoPlayer + MediaSession registrada al crearse → notificación y primer plano)
└── MediaListenerService (vacío: habilita el acceso a sesiones de música)

Radio (estado y datos, compartidos por el servicio y la interfaz)
├── RadioState ─ emisora, sonando, conectando, pausa y motivo, LOCAL/NACIONAL
├── RadioCatalog ─ dial + directorio, revisión de cuáles funcionan, categorías
├── RadioFavorites ─ favoritas guardadas
├── RadioLogos ─ logos con caché (memoria y disco)
└── RadioNow ─ textos de estado para Inicio y la pantalla de música

Infraestructura
├── Net.get / Net.getBytes / Net.request ─ HTTP con cierre garantizado, código 2xx, límite de tamaño, redirecciones http↔https
├── AppLog ─ logcat "OnixLauncher" (sin claves: `redact`) + último error para Diagnóstico
└── network_security_config ─ HTTPS obligatorio para APIs
```

## Módulos

| Archivo | Responsabilidad | Depende de |
|---|---|---|
| `MainActivity.kt` | Activity única, oculta las barras del sistema; instala Conscrypt (`Tls.install`) y registra los avisos de proceso (`NwdSignals`, `AutoResume`, `Intro`, `NetWatch`, `RadioIll`); `onKeyDown` anota cada tecla en `NwdSignals` sin cambiar lo que hace (v0.64.1); recibe la vuelta de Spotify (`onixlauncher://spotify` → `SpotifyAuth.handleRedirect`) | `LauncherApp`, `NwdSignals`, `Tls`, `SpotifyAuth` |
| `LauncherApp.kt` | Estado global, reloj, clima, páginas y superposiciones. También es dueño de los trabajos de fondo atados a la pantalla: `PhoneShare.start`, `NwdTuner.bind`, permisos del motor y `Connection.state` → `EngineTrace`/`Projection` (si la Activity se destruye, esos trabajos se detienen). El bucle del OBD2 y `CarCare.tick` ya no: los lleva `CarEngine` (v0.81.12) | `Media`, `Location`, `Weather`, `Obd`, `CarCare`, `PhoneShare`, pantallas |
| `HomeScreen.kt` | Pantalla de inicio: encabezado, pronóstico, tarjetas y franja del vehículo | `Ambient`, `Road`, `PanelLayout`, `EnginePanel`, `Dock`, `Ui` |
| `NavMusicScreen.kt` | Segunda pantalla (reemplaza al Tablero en v0.34.0): mapa y música con pestañas Spotify / Radio | `MapNav`, `Media`, `Radio`, `Dock` |
| `Spotify.kt` | `SpotifyAuth` (PKCE, tokens en el equipo, renovación; los pedidos de tokens pasan por `SpotifyAuth.http`, que los tests reemplazan, v0.79.6; el pedido de permiso lleva un `state` al azar y `handleRedirect` ignora toda redirección que no lo traiga, AUD-093), `SpotifyApi` (listas, recientes, búsqueda, reproducir con Spotify Connect; parse puro) | `Net.request` |
| `MapNav.kt` | `MapPanel` (usa un `NavMap`: 3D o clásico; botón 2D/3D), `NavApi` (TomTom desde v0.75.0: búsqueda, ruta con tránsito y nombre del lugar; `maneuver` traduce las maniobras; parse puro; si la conexión normal falla, `DirectHttps`), punto elegido con el dedo, cartel de navegación, `SavedPlaces` (Casa, Trabajo, favoritos, recientes) | `Map3D`, `Net`, `Navigation` |
| `Navigation.kt` | `Guidance` (próxima maniobra, avisos, desvío; puro), `Step`, textos en castellano, `Voice` (TextToSpeech), `NavRequest` (destino pedido desde Inicio) | `TextToSpeech` |
| `EnginePanel.kt` | Telemetría de Inicio: velocímetro y cuentavueltas redondos, barras de temperatura y combustible, auto visto desde arriba, batería, consumo (km/l) y línea de códigos de falla (`codesStatus`); `CodesDialog` (salud por sistema `HealthGrid`, lista explicada, leer de nuevo, borrar con el auto parado) | `Drawing`, `Ui`, `ObdCodes`, `Obd` |
| `Dock.kt` | Barra de accesos: Inicio fijo y 7 lugares personalizables (`DockStore`, `DockSlot`, `Builtin`); modo edición con × y +; Apps abre el cajón; `PhoneApp` (Teléfono abre la app de teléfono de la radio) | `Ui`, `Drawing`, `AppsScreen` |
| `Obd.kt` | `CarEngine` (v0.81.12, ADR-0018): bucle del lector, receptor de avisos y `CarCare.tick`, en un `CoroutineScope` de proceso; `StartOnce` (un solo arranque). OBD2: busca el ELM327 emparejado (prende el Bluetooth si está apagado), conecta por SPP con tiempo máximo de 15 s y tres formas (segura, insegura, canal 1), `ObdRecovery` (puro: esperas de 15 s a 10 minutos entre reintentos y un solo socket por intento desde v0.81.14, sin reinicio del Bluetooth desde v0.81.13, contacto y pantalla como señal de reintento; v0.79.2: `withHardTimeout` y vigía `isStuck` de 20 s para una conexión trabada), inicializa (AT y 0100 con tiempo para el protocolo), en cada vuelta usa `ObdReader`; lee los códigos al conectar, cada 5 min y a pedido, y los borra a pedido; reconecta; reinicia el lector si el auto no contesta 5 lecturas seguidas; `ObdData` (`data class` desde v0.79.5: una lectura igual no recompone la pantalla, AUD-075), `ObdState`, `ObdParse` y `ObdWatch` (puros). v0.79.5 (AUD-072, AUD-080): `ObdRecovery.cycleBluetooth` apaga y prende el Bluetooth sin poder cortarse a la mitad, `isReaderFailure` (cerrar la pantalla no es falla) y `withHardTimeout(onLate)` cierra la conexión que llega después del tope; `ElmLink` (v0.79.6) recibe los flujos y el cierre, así se prueba sin Bluetooth (`ElmLinkTest`) | `BluetoothAdapter`, `ObdCodes` |
| `ObdCodes.kt` | Puros (v0.50.0): `ObdCodes`, `Dtc` (modos 03/07 en CAN de una y varias líneas y protocolos viejos, luz del motor, borrado 04, descripciones en castellano), `EngineAlert`/`EngineAlerts`/`AlertWatch` (alertas con espera), `Consumption` (km/l por MAP + temperatura + rpm, o MAF), `ObdParseExtra`, `ObdReader` (vuelta de lectura con la función de envío inyectada: en los tests, `FakeElm327`), `CarSystem`/`HealthLevel`/`CarHealth` (con `summary` para el informe, v0.67.1) (v0.64.0: sistema de cada código por SAE J2012 y estado de cada sistema) | `ObdParse` |
| `Projection.kt` | v0.56.0: `Projection` (`open`, `screenClass` puro y `returnTo`: volver a la pantalla de Android Auto o CarPlay, v0.72.0; `status` puro: `LinkState` en castellano; `find` puro: apps de proyección instaladas aparte; `detailOf` puro: motivo exacto al fallar, v0.79.1), `EngineTrace` (v0.79.1: últimos 50 pasos del motor para la Prueba completa; escucha `CarPlayStatusBus` desde `OnixApp` y el estado desde `LauncherApp`; `describe`, `session`, `logTail` (registro `files/logs/diplay.log` del motor) y `usbLine` puros), `ProjectionOverlay` (estado del motor con `Connection.get().state`, Cortar) e `iconProjection` | `carlink-core` |
| `Lite.kt` | v0.58.0: `LiteMode` (`isLowEnd` y `on` puros; elección guardada en SharedPreferences `liviano`): la leen `HomeScreen` (lluvia, emblema), `Intro` (encendido) y `MapPrefs` (mapa por defecto) | `ActivityManager`, `Build` |
| `IntroSound.kt` | v0.65.0: `IntroChime` (puro: muestras de un sonido propio; v0.81.15: acorde que entra de a poco y dos notas claras; por defecto suena solo el sonido), `IntroVoices` (v0.69.0, puro: voces del slogan según los archivos `inicio_*.ogg` del APK; v0.70.0 suma las de ElevenLabs) y `IntroSound` (modo y voz guardados en `inicio`; `preview` la hace escuchar desde Diagnóstico; `onIntro` toca el sonido con `AudioTrack` y después la frase de la voz elegida; no suena con proyección activa ni dos veces en 30 s) | `RecordedVoice`, `Projection`, `Intro` (desde `LauncherApp`) |
| `Festive.kt` | v0.65.0: `Festive` (puro: fiesta de cada fecha de Argentina, Pascua por Meeus/Jones/Butcher, domingos variables; prendido en `fiestas`; tema de prueba), `Holiday`, `Decor`, `FestiveLayer` (Canvas en el cielo: nieve, luces, fuegos, corazones, papel picado, pétalos, murciélagos) y `CarDecor` (v0.66.0; v0.67.0 animado: contornos `ROOF`, `HOOD`, `BELT` medidos sobre la foto y repartidos con `Festive.along`; tiras de luces con cable que titilan, nieve con carámbanos, moño, bandera que flamea, escarapela, calabazas con vela, sombrero, corazones que laten, banderines, flores) | `HomeScreen` (cielo y auto), `LiteMode` |
| `VoiceClips.kt` | v0.61.0: `VoiceClips` (puro: claves de las frases para avisos y maniobras, distancia redondeada a 50 m) y `RecordedVoice` (MediaPlayer en cadena desde `assets/voz`, foco de audio que baja la música). `Voice.say` lo usa si no hay TextToSpeech | `Voice`, `Guidance`, `SafetyAlerts` |
| `OnixApp.kt` | v0.56.0: `OnixApp` (Application; hereda de `com.andrerinas.openheadunit.App`, marca los motores como "Onix", `CarLinkCore.install` en todos los procesos) y `OnixHost` (`CarLinkHost`: vuelve a `MainActivity`) | `carlink-core`, DiAuto, DiPlay |
| `CarCare.kt` | v0.79.0 (rehecho con el autor): `TripTracker`, `TripSummary` (litros por consumo del OBD2 o por el nivel del tanque de 54 L), `MaintItem` y `Maintenance` (puros: cuánto falta por km y/o fecha, lo primero que llegue, textos, hecho), `Monthly` (totales por mes), `Reminders` (fechas y números), `CarCare` (guardado en `mi_auto`: `mantenimiento`, `viajes`, `meses`, `km_vistos`, `km_ajuste`, `aviso_dia`; v0.79.5, AUD-101: `readOrBackup` guarda una copia `<clave>_respaldo_<ms>` si lo guardado no se puede leer, antes de que el próximo guardado lo pise; fin de viaje con el aviso de contacto de la radio NWD, estado 3; aviso diario del ítem más urgente), `CarCareOverlay` (pestañas Mantenimiento · Viajes · Motor) | `Obd`, `EnginePanel` (`CodesDialog`), `Ui` |
| `SafetyAlerts.kt` | `SoundMode` y `AlertPrefs` (v0.78.0: todo / solo avisos / silencio y cada aviso prendido o apagado; `next`, `guidanceVoice`, `alertSound`, `parseMode` puros), `LightsReminder` y `RepeatWatch` (puros: cuándo avisar de las luces —v0.79.7: andando desde 2 km/h, de día y de noche, a los 5 s— y no repetir antes de 10 min), `SpeedWarning` (máximo elegido en Mi auto, guardado en `velocidad`; avisa pasándolo por más de 3 km/h 5 s), `SafetyOverlay` y `SafetyBanner` (cartel), pitido (`ToneGenerator`) y voz (`Voice`, con `always`) | `RadioIll`, `ObdState`, `Navigation` |
| `Lights.kt` | `LightsMode`, `lightsOn` (pura), `LightsPrefs`, `RadioIll` (señal de luces de la radio NWD: aviso `com.nwd.ACTION_ILL_STATE_CHANGE`, bit 2 de `mcu_no_source_device_state` y `high_beam_state`, observados) y `ScreenBrightness` (brillo máximo de la ventana), `NwdSignals` (avisos de la radio NWD y, solo con Diagnóstico abierto o durante la Prueba completa, cambios de los ajustes system/global/secure y de las propiedades `getprop` cada 3 s, en un hilo aparte): de dónde salen las luces del auto | `Configuration.uiMode`, avisos `com.nwd.action.*`, `Settings`, `getprop` |
| `AppsScreen.kt` | Cajón de apps (`AppsOverlay`, también como selector del dock) e íconos de apps instaladas (`appIcon`, `rememberAppIcon`) | `PackageManager` |
| `RadiosScreen.kt` | Interfaz de radio FM: display con logo y ★, dial, categorías, presintonías; interruptor ANTENA / INTERNET (`SourceSwitch`), búsqueda en el aire y RDS por la antena | `Radio`, `RadioCatalog`, `RadioLogos`, `NwdTuner` |
| `Radio.kt` | `Station`, `RadioState`, `RadioDirectory` (directorio, lectura de páginas, búsquedas), `RadioFavorites`, `Radio` (play, stop, toggle, step: por internet con `RadioService` o, en modo antena, con `NwdTuner`; `ANTENNA_NOTE`), `RadioService`, `radioCandidates` | Media3, `Net`, `NwdTuner` |
| `NwdTuner.kt` | v0.51.0: `NwdTuner` (Binder con `com.nwd.radio.service.RadioFeature`: leer frecuencia y RDS, sintonizar, buscar; despertar el chip; fuente de audio FM = 4 / Android = −128 con `ACTION_REQUEST_CHANGE_SOURCE`; modo antena guardado), `TunerMath` y `TunerStation` (puros) | servicio de la radio NWD, `Settings.System.mcu_current_source` |
| `RadioCatalog.kt` | `RadioCatalog` (catálogo y revisión de radios que funcionan), `RadioCategory`, `categoryOf`, `streamResponds`, `looksLikeAudio` | `Radio`, `Net` |
| `RadioLogos.kt` | `RadioLogos` (fuentes, caché, decodificación, `.ico`), `StationLogo`, `stationInitials`, `iconStar` | `Net`, `RadioDirectory` |
| `RadioNow.kt` | Estado de la radio para las tarjetas (`radioActive`, `showRadioCard`: radio si suena, si no otra app que suene, si no la radio en pausa; `radioStatus`, `LiveDot`) | `RadioState`, `Media` |
| `RadioProbe.kt` | Prueba de enlaces de una radio (incluye señales probables de La 100 en StreamTheWorld) | `Radio`, `Net` |
| `TucumanFm.kt` | Dial de FM de Tucumán: frecuencias (World Radio Map), enlaces de audio conocidos (radioarg.net), logos (World Radio Map) y páginas web | `Station` |
| `MediaListenerService.kt` | Servicio vacío para el acceso a notificaciones | — |
| `Weather.kt` | `weatherUrl`, `fetchWeather`, `parseWeather` | `Net`, org.json |
| `Media.kt` | `MediaWatcher`, `rememberMedia`, `rememberPlaybackPosition`, acciones propias de la app (aleatorio, me gusta), `playOtherApp` / `spotifyPlay` (Spotify sin abrirlo), `LastAudio` y `AutoResume` (retomar al encender), `IgnitionAudio` (v0.79.4: al poner contacto devuelve la fuente de la radio a Android con `NwdTuner.release` si sonaba Spotify o internet; `shouldRestore` puro), `mediaKey` (v0.64.0: teclas de música por `AudioManager` para apps sin sesión) | `MediaSessionManager` |
| `Location.kt` | `rememberGpsSpeed`, `PlaceRefresh` (v0.79.8: el clima y el lugar se piden de nuevo si el auto se alejó más de 3 km), `GpsFix` (v0.79.7: registro de las posiciones del GPS con el reloj del equipo; edad, frecuencia y retraso de entrega; `status` es el cartel "Buscando GPS… / Sin señal de GPS hace N s" del mapa, `isStale` marca las rutas "desde la última posición conocida" y `report` alimenta la Prueba completa; todo puro), `GpsSpeed` (dato viejo a los 5 s; `live()`: velocidad del OBD2 o del GPS sin depender de la pantalla, la usan Mi auto y los avisos desde v0.64.1, AUD-058), `lastLocation`, `placeName` (nombre del lugar), ubicación por defecto | `LocationManager`, `Geocoder` |
| `Ambient.kt` | Ambiente (hora del día con o sin lluvia), fondo, tono del auto, saludo y `GreetingName` (nombre del saludo) | recursos, SharedPreferences `saludo` |
| `Map3D.kt` | `NavMap` (interfaz del mapa), `LibreNavMap` (MapLibre 10.3.7: estilo oscuro `MapStyle` con OpenFreeMap/OpenMapTiles, edificios `fill-extrusion`, ruta y destino como capas GeoJSON, punto propio por GPS, cámara inclinada que sigue y gira), `OsmNavMap` (osmdroid, respaldo automático si el 3D no carga), `MapPrefs` | MapLibre, osmdroid, `LocationManager` |
| `Intro.kt` | `Intro` (cuándo se muestra: al abrir y con `ACTION_SCREEN_ON` si es la pantalla de inicio) e `IntroOverlay` (animación) | `chevrolet_emblem.png`, `ACTION_SCREEN_ON` |
| `Road.kt` | `RenderFlags` (interruptor de animaciones). La ruta y su línea central se quitaron en v0.27.0 | — |
| `PanelLayout.kt` | Paneles movibles y escalables, `LayoutStore` | SharedPreferences |
| `Diagnostics.kt` | Diagnóstico en vivo, `appVersionLabel`, `lightSettings` y el botón **Prueba completa** | `AppLog`, `BuildConfig` |
| `SettingsScreen.kt` | v0.80.0: `SettingsCategory`, `SettingsNav` (`open`, `category`), `CrashLog` (captura cierres inesperados en `SharedPreferences("fallos")`, formato puro `format`) y `SettingsOverlay` (categorías Apariencia, Sonido y avisos, Sistema) | `Ui` (`ThemePrefs`, `ThemeMode`, `AccentPalette`), `Lights`, `Lite`, `Diagnostics` |
| `FullReport.kt` | `BtDevices` (v0.81.25: lista los equipos Bluetooth emparejados para el informe). `ReportUpload` (v0.81.23: envía el informe a la PC por el canal de informes de ntfy y reintenta al volver la red). `FullReport` (prueba completa: red, servicios de internet, GPS, clima, luces, OBD2 con salud por sistema y respuestas crudas, radio con `RadioProbe`, música y Spotify sin tokens, voz, voces grabadas y mapa, Android Auto / CarPlay, memoria por proceso, Mi auto, teclas del volante, permisos, espacio, interfaz), `Check`/`CheckResult`; `tryBegin` (v0.79.5: no arranca dos veces); guarda el .txt (MediaStore en Android 10+, Descargas con permiso o carpeta de la app en 7 a 9) y lo copia | `Net`, `RadioProbe`, `ObdState`, `Voice`, `Connection` (CarLink), `CarCare`, `NwdSignals.keys`, `MediaStore` |
| `About.kt` | Acerca de, `AboutInfo` (autoría y créditos) | `BuildConfig` |
| `Net.kt` | `Net` (`get`, `getBytes`, `request`, `inspect`), `AppLog` (últimos 80 eventos; `info` y `warn` tapan claves con `redact`), `LOG_TAG`, `NetWatch` (vuelta de la red; `announce` avisa a la interfaz, la radio, el catálogo y el destino en espera, v0.81.22), `WifiRescue` (v0.81.19: reinicia el Wi-Fi si los nombres no resuelven durante un minuto; v0.81.21: prueba la red cada 30 s durante la caída y sube `NetWatch.back` cuando vuelve), `Tls` (Conscrypt como fábrica HTTPS por defecto en Android 7 a 9, con la confianza de `network_security_config`), `DirectHttps` (GET HTTP/1.1 por el socket de Conscrypt sin cambios, respaldo de las rutas en Android 7, HU-013) | `ConnectivityManager`, Conscrypt |
| `PhoneShare.kt` | v0.76.0: `SharedDest` y `PhoneShare` (`parse`, `coordsFromUrl`, `parseEvent`, `isFresh` puros; `start`: hilo que escucha `ntfy.sh/<canal>/json` y se reconecta; `resolve`: TomTom o link corto de Maps), `PhoneShareOverlay` ("¿Ir a …?") | `NavApi`, `NavRequest`, `Location`, `Ui` |
| `NearbyPlaces.kt` | v0.77.0: `PlaceCategory` (códigos de TomTom), `Nearby` (`nearbyUrl`, `alongUrl`, `alongBody`, `parse` puros; `nearby`, `along`), `FuelWatch` (puro: nafta ≤ 12 % andando, cada 30 min) y `FuelOfferOverlay`, `PlacesEditor` (Mis lugares) | `NavApi`, `SavedPlaces`, `Net`, `Ui` |
| `Ui.kt` | Paleta, `card` (vidrio grafito, compartido por todos los paneles), `PillButton`, `startSafe` | — |
| `Drawing.kt` | Íconos vectoriales | — |

## Flujos de datos

| Dato | Fuente | Frecuencia | Consumidor |
|---|---|---|---|
| Hora | `Calendar` | al cambiar el minuto (cada 1 s con Diagnóstico abierto) | inicio, pantalla de navegación y música, ambiente (con la lluvia del clima), saludo |
| Vuelta de la red | `NetWatch` (`ConnectivityManager.registerDefaultNetworkCallback`) | al volver el Wi-Fi o los datos después de un corte | clima (pide de nuevo), radio (retoma la emisora si se cortó sola) |
| Nombre del saludo | SharedPreferences `saludo` (`nombre`, `nombres`) | al tocar el saludo | VehicleStrip, NameDialog |
| Prueba completa | `FullReport.run` (todas las revisiones) | al tocar **Prueba completa** | `onix_informe_AAAA-MM-DD_HH-mm.txt` en Descargas/OnixLauncher y portapapeles |
| Presentación | `Intro.visible`: al crear el proceso y con `ACTION_SCREEN_ON` (solo si el launcher es la pantalla de inicio) | al abrir y al volver de la suspensión | IntroOverlay |
| Música | `MediaSessionManager` + `MediaController.Callback` | por eventos; progreso local cada 1 s mientras suena | MediaCard, MiniMedia |
| Acceso a música | `getActiveSessions` | reintento cada 5 s mientras no haya acceso | MediaCard |
| Velocidad | GPS (`LocationManager`) | ~1 s | velocímetro, ruta |
| Posiciones del GPS | `LocationManager` (solo `GPS_PROVIDER`), a 1 s y sin mínimo de metros (v0.79.7) en el velocímetro, el punto de los dos mapas y la guía; `GpsFix` anota cada una | ~1 s | punto azul de los mapas, cartel "Sin señal de GPS", Prueba completa (frecuencia y retraso) |
| Clima | Open-Meteo vía `Net.get` | 15 min (1 min si falla) | HeaderCard, ForecastCard, ambiente |
| Luces del auto | Modo noche de la radio (`Configuration.UI_MODE_NIGHT_YES`) o, en la radio NWD con el "sensor de iluminación" prendido, `Settings.System.high_beam_state` = 1 (`RadioIll`, por aviso de cambio); o el modo elegido a mano (SharedPreferences `luces`) | al cambiar la configuración / al tocar "Luces" en Diagnóstico (v0.50.0: ya no está en la franja) | faros del auto, haces, franja inferior, Diagnóstico, aviso de luces |
| Biblioteca de Spotify | Spotify Web API con token del equipo (`onixlauncher://spotify` vuelve a `MainActivity`) | al abrir Listas / Recientes, al abrir una lista (sus temas, v0.81.17) y al buscar | SpotifyLibrary |
| Navegación | GPS cada 1 s (`LocationManager`) mientras se navega; ruta de TomTom con maniobras | al moverse | cartel, voz, mapa que sigue y gira, recálculo |
| Búsqueda y ruta | TomTom (v0.75.0, ADR-0014): búsqueda con altura cerca de la ubicación y ruta con tránsito, vía `Net.get` y, si falla, `DirectHttps` | al tocar Buscar y al elegir un destino | MapPanel |
| Mosaicos del mapa | 3D: mosaicos vectoriales de OpenFreeMap (caché propia de MapLibre); clásico: OpenStreetMap (osmdroid, caché permanente en `filesDir/osmdroid/tiles`, hasta 500 MB) | al mover el mapa; sin conexión se ve lo ya visto | MapPanel |
| Preferencias del mapa | SharedPreferences `mapa` (`motor`, `inclinado`) | al tocar 2D/3D | MapPanel |
| Lugares guardados | SharedPreferences `lugares` (casa, trabajo, favoritos, recientes) | al elegir o guardar un destino | MapPanel |
| Lugares cerca y paradas | TomTom nearbySearch (categoría, 10 km) y, navegando, searchAlongRoute (POST con hasta 150 puntos de la ruta que falta, desvío ≤ 15 min); la parada entra como punto intermedio de la ruta (`via`) hasta pasar a menos de 80 m | al tocar una categoría o "Parada" | MapPanel |
| Destinos del celular | ntfy (v0.76.0): Compartir → ntfy en el celular; la radio escucha el canal privado (`NTFY_TOPIC`), toma mensajes de los últimos 15 min, busca con TomTom y pregunta | al llegar un mensaje | PhoneShareOverlay → `NavRequest` (arranca la guía) |
| Nombre del lugar | `Geocoder` del equipo; si no tiene servicio (la radio), TomTom (reverseGeocode) (sin ubicación: Tafí Viejo, la casa del autor, donde se pide el clima: `DEFAULT_LAT/LON/PLACE_NAME`, v0.79.7) | con el clima | HeaderCard |
| Apps instaladas | `PackageManager.queryIntentActivities` (MAIN/LAUNCHER) | al abrir Apps | AppsOverlay; ícono de Spotify en Inicio |
| Radio por la antena | `NwdTuner` (servicio de la radio NWD): frecuencia y RDS | al sintonizar, al buscar y cada 2 s con la pantalla de radio abierta | `RadioState` (nota `ANTENA`), display, Diagnóstico, informe |
| Radio | `RadioService` → `RadioState` | por eventos | FmRadioPanel, RadioCard (inicio), pestaña Radio (pantalla de música), notificación |
| Radios que funcionan | `RadioCatalog` (prueba cada señal local, 4 a la vez, y anota el motivo de cada resultado) | al abrir Radios si pasaron 3 días, o con REVISAR | lista de radios, informe de Diagnóstico; SharedPreferences `radio_salud` (incluye `detalle`) |
| Logos | `TucumanFm` (World Radio Map) → Radio Browser → página web → búsqueda por nombre | una vez por emisora; caché `logos_v2` y SharedPreferences `logos` | StationLogo |
| Favoritas | SharedPreferences `radio` (`favoritos`) | al marcar | presintonías, filtro, ⏮ ⏭ |
| Errores y eventos | `AppLog` (últimos 80) | al ocurrir | Diagnóstico, informe copiado, logcat |
| Prueba de radio | `RadioProbe` | al tocar "Probar" | informe copiado |
| Distribución de paneles | SharedPreferences `layout` | al editar | EditablePanel |
| Última fuente de audio | SharedPreferences `audio` (`fuente`, `emisora`) | al empezar a sonar; se borra si el usuario pausa | `AutoResume` al abrir la app y al prenderse la pantalla |
| Accesos del dock | SharedPreferences `dock` (`lugares`) | al quitar o agregar | Dock (Inicio y segunda pantalla) |
| Modo LOCAL/NACIONAL | SharedPreferences `radio` | al cambiar | `Radio` |
| Permiso ya pedido | SharedPreferences `permisos` | una vez | RadiosOverlay |
| OBD2 | ELM327 por Bluetooth (`Obd.kt` → `ObdReader`) | ~1 s conectado (cada 3 s si el auto no contesta); reintento a los 15 s, 30 s, 1, 2, 5 y cada 10 minutos, o enseguida al dar contacto; si el auto no contesta se reinicia el lector por la misma conexión | panel de motor, velocidad (prioridad sobre el GPS), Diagnóstico e informe |
| Consumo estimado | `ObdReader`: MAF (01 10) si el auto lo da; si no, MAP (01 0B) + temperatura del aire (01 0F, cada 10 vueltas) + rpm, suavizado | con cada vuelta del OBD2, en movimiento | panel de motor, Diagnóstico, informe |
| Códigos de falla | `ObdReader.readCodes`: 01 01 (luz del motor), 03 (guardados), 07 (pendientes); borrado con 04 | al conectar, cada 5 min, con **Leer de nuevo**; borrado al confirmar con el auto parado | `ObdState.codes` → línea del panel de motor, `CodesDialog`, Diagnóstico, informe |
| Alertas del motor | `EngineAlerts.of(ObdData)` + `AlertWatch` (8 s recalentado, 60 s batería) | con cada vuelta del OBD2 | `ObdState.alert` → `SafetyOverlay` (cartel, pitido, voz), Diagnóstico, informe |
| Viajes y kilometraje | `CarCare.tick` cada 1 s desde `CarEngine` (velocidad del OBD2 o GPS, litros por hora y temperatura del OBD2) | cada 1 s | `CarCareOverlay`; SharedPreferences `mi_auto` (`viajes`, `meses`, `km_vistos`, `km_ajuste`) |
| Mantenimiento | SharedPreferences `mi_auto` (`mantenimiento`, `aviso_dia`) | al abrir Inicio (8 s después) y al editar | `SafetyOverlay` (cartel azul, una vez por día), `CarCareOverlay` |
| Lector OBD2 elegido | SharedPreferences `obd` (`direccion`) | al conectar | `findReader` (el mismo lector la próxima vez) |
| Cuenta de Spotify | SharedPreferences `spotify` (tokens, y el Client ID si se pegó a mano; el de `BuildConfig.SPOTIFY_CLIENT_ID`, del secreto `SPOTIFY_CLIENT_ID`, gana si existe; nunca en el informe) | al conectar la cuenta y al renovar | `SpotifyAuth`, `SpotifyApi` |
| Destinos del celular (último leído) | SharedPreferences `celular` (`ultimo`: id del último mensaje, para seguir desde ahí) | al llegar un mensaje de ntfy | `PhoneShare.start` |
| Sonido de los avisos | SharedPreferences `avisos` (todo / solo avisos / silencio y cada aviso; se cambia en Diagnóstico) y `velocidad` (máximo, en Mi auto) | al cambiarlo | `AlertPrefs`, `SpeedWarning` |
| Brillo de la pantalla | SharedPreferences `pantalla` (`brillo_max`) | al cambiarlo | `ScreenBrightness` |
| Auto / CarPlay | `carlink-core`: `UsbRouterActivity` (único receptor de USB_DEVICE_ATTACHED) clasifica el celular y `SessionManager` arranca el motor (DiAuto en `:aa` o DiPlay); estado en `Connection.get().state` | al enchufar o desenchufar el USB | pantalla de proyección del motor; `ProjectionOverlay`; `OnixHost.openHome` al desenchufar |
| Modo liviano | `LiteMode.load` al arrancar (`Build.SUPPORTED_64_BIT_ABIS`, núcleos, `ActivityManager.MemoryInfo`) y la elección de Diagnóstico | al arrancar y al tocar Liviano | `HomeScreen`, `Intro`, `MapPrefs` |
| Voces grabadas | `tools/voces/frases.txt` → workflow `voces.yml` (Piper) → `assets/voz/<clave>.ogg` | al decir un aviso o una maniobra sin TextToSpeech | `Voice.say` → `RecordedVoice` |
| Aviso de luces apagadas | `LightsReminder` con la velocidad (desde 2 km/h) y `RadioIll`, sin importar la hora del día (v0.79.7); `RepeatWatch` (5 s, no repite antes de 10 min) | cada 1 s mientras se cumple la condición | `SafetyOverlay` |

## Radio: cadena de enlaces

Cada emisora tiene una lista de enlaces separados por `|`. Al sintonizar, `RadioService` los prueba en orden:

1. **Enlaces guardados.** En modo LOCAL los marcados `nac:` van al final; en NACIONAL, al principio.
   - `page:URL`: abre la página del reproductor oficial y saca el enlace de audio.
   - `.m3u` / `.pls`: lee la lista y usa el primer enlace.
2. **`lookup:`**: busca por nombre en Radio Browser, eligiendo primero Tucumán (LOCAL) o fuera de Tucumán (NACIONAL).
3. Si no queda ninguno: "SIN TRANSMISIÓN ONLINE".

Cadenas sin señal de Tucumán por internet (La 100, Mitre, Radio 10, Vale, Cadena 3, Radio María…): se usa la señal nacional, y la revisión las cuenta como que funcionan ("señal nacional").

Cortes: si la señal ya sonó y se corta (error o fin de la transmisión), se reconecta a la **misma** señal hasta 4 veces (1, 3, 6 y 10 s) antes de pasar a la siguiente. Mientras suena, el reproductor mantiene la CPU y el Wi-Fi activos (`WAKE_LOCK`). Si se pausa por otra app o por desconexión del audio, el motivo queda en `RadioState.pauseReason`.

Señales de StreamTheWorld (Cienradios: La 100, Mitre): el reproductor sigue redirecciones https↔http y lee HLS (`.m3u8`).

Cada orden (`ACTION_PLAY` / `ACTION_STOP`) incrementa un **número de generación**, y destruir el servicio también (AUD-100, v0.79.6: además se vacía la cola del hilo principal, así una reconexión pendiente no toca el reproductor liberado). Las búsquedas corren en un hilo aparte y, al volver al hilo principal, se descartan si la generación cambió (AUD-007). Las páginas `page:` solo se aceptan de dominios de confianza (`cienradios.com`, `cienradios.com.ar`), y los enlaces de audio que se sacan de ellas, de esos dominios y de `streamtheworld.com` (AUD-003).

## Imágenes

Se generan con los scripts de `tools/` y se guardan como recursos (`drawable-nodpi`). No se procesan en la app.

| Recurso | Script | Origen |
|---|---|---|
| `onix_car.png` | `make_car.py`, `clean_car.py` | Foto aportada por el usuario |
| `onix_glow.png` | `make_lights.py` | Derivado de `onix_car.png` |
| `bg_*.jpg` (1280 × 750) | `make_bg_mockups.py` | Día y atardecer sacados de las imágenes de referencia del autor: se borran paneles, textos y auto, y se rellena lo tapado. Noche derivada del atardecer, con un pueblo iluminado en el valle, y lluvia del día, para que los cuatro tengan la misma ruta. `make_bg_rain.py` (v0.39.0) deriva `bg_lluvia_atardecer.jpg` y `bg_lluvia_noche.jpg` de esos fondos. El auto de Inicio va en el mismo lugar que en esas imágenes (380, 182, 391 × 256 dp); se lo ubica con puntos SIFT y una afín. `make_bg.py` y `make_road.py` quedan como historial |
| `chevrolet_emblem.png` | (recorte manual) | Imagen aportada por el usuario; marca de GM |

## Build

GitHub Actions (`build.yml`) con Gradle wrapper 9.5.0 (AGP 9.3.0, Kotlin 2.2.10), en cuatro trabajos (ADR-0013); desde v0.79.5 con permiso de solo lectura salvo **publicar** (AUD-074):

- **tests:** chequeo de documentación (`check_docs.py`) → validación del wrapper → tests de lógica (bloqueantes) → cobertura (JaCoCo, también de las clases que cargan los tests de Robolectric desde v0.79.5).
- **app** (en paralelo): identidad de CarPlay → firma → release con R8 (lint rápido con `NewApi` fatal) → firma verificada; sube el APK y el mapa de R8 (`onix-launcher-mapping.txt.gz`).
- **controles** (en paralelo): capturas (Robolectric NATIVE + Roborazzi, animaciones apagadas con `RenderFlags`). El lint completo corre aparte (`lint.yml`, semanal y a pedido).
- **publicar:** `tests.txt`, `lint.txt`, GitHub Release `build-N` (APK, mapa de R8 y capturas) y tag `vX.Y.Z`. Ver [ADR-0002](docs/decisions/ADR-0002-compilacion-en-github-actions.md) y [ADR-0007](docs/decisions/ADR-0007-tests-bloqueantes.md).

Otros workflows: `carlink.yml` (diario: identidad de CarPlay de la última CarLink y aviso de motores nuevos), `lint.yml`, `notas.yml` (rehacer la nota de una release), `voces.yml` y `voces_ia.yml` (voces grabadas), `tomtom_prueba.yml`.

## Decisiones

- ADR-0012: Android Auto y CarPlay dentro de Onix con `carlink-core` (AGPL-3.0), por etapas.
- ADR-0013: APK solo de 32 bits (la radio) y CI en paralelo: menos peso y menos espera.
- ADR-0014: búsqueda y rutas con TomTom (reemplaza a Nominatim/OSRM de ADR-0009 para buscar y rutear).
- ADR-0015: el Client ID de Spotify puede venir de un secreto de GitHub (no hace falta pegarlo en la radio).
- ADR-0016: CI de mínimo privilegio, valores de afuera por variable de entorno y acciones fijadas por commit.
- ADR-0017: el APK trae solo castellano e inglés (−3,30 MB).
- ADR-0018: el OBD2 y los viajes viven con el proceso (`CarEngine`), no con la pantalla.

Ver [`docs/decisions/`](docs/decisions/) (ADR-0001 a ADR-0018).

## Diferencias conocidas con una arquitectura ideal

- No hay ViewModel ni repositorios: el estado vive en objetos globales observables (`RadioState`, `ObdState`, `CarCare`, `NavRequest`…) y la lógica pura está separada y con tests. Para este proyecto alcanza; no hace falta migrar a MVVM (auditoría 2026-11-05).
- Lo que sí falta es un dueño claro del ciclo de vida de algunos estados (auditoría 2026-11-05):
  - **AUD-069:** el estado de la navegación (destino, ruta, guía) vive en el `remember` de la página del mapa; al volver a Inicio el pager la descarta y la guía se corta. Propuesta: `object NavSession`, como `NavRequest`.
  - **AUD-082 (resuelto en v0.81.12):** el bucle del OBD2 y `CarCare.tick` vivían en la composición de la Activity; ahora son de `CarEngine`, de proceso (ADR-0018). Sigue en pie que, si Android mata el proceso entero, se cortan.
- Desde v0.56.0 hay 6 módulos Gradle: `:app` (Onix) y los del motor de CarLink (`:carlink-core`, `:carplay-shared`, `:carplay-common`, `:androidauto`, `:androidauto-contract`) en `carlink-core/` y `vendor/`. Se tocan solo si hace falta y cada cambio queda en `vendor/ONIX_CHANGES.md` (el único hasta hoy: paquete USB vacío en `vendor/diplay`, v0.79.3).
