# Onix Launcher

![Versión](https://img.shields.io/badge/versión-0.81.25-4C8DFF)
![Estado](https://img.shields.io/badge/estado-beta-F2B63C)
![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4)
![Tests](https://img.shields.io/badge/tests-34%20OK-59E08A)
![Licencia](https://img.shields.io/badge/licencia-AGPL--3.0-blue)

Launcher para el head unit Android del **Chevrolet Onix Joy 2018**. Reemplaza la pantalla de inicio del equipo por una interfaz pensada para manejar: tu auto sobre la ruta, clima real, música, radio FM de Tucumán por internet y una pantalla de navegación y música para usar el mapa, Spotify y la radio sin salir del launcher.

> Las capturas del autor no se incluyen en esta versión pública porque mostraban fotos y marcas de terceros. Se pueden regenerar con Roborazzi (ver más abajo).

---

## Contenido

- [Características](#características)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Uso](#uso)
- [Especificaciones técnicas](#especificaciones-técnicas)
- [Dependencias](#dependencias)
- [Módulos](#módulos)
- [Permisos](#permisos)
- [Servicios externos](#servicios-externos)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Compilación](#compilación)
- [Tests](#tests)
- [CI/CD](#cicd)
- [Configuración](#configuración)
- [Seguridad](#seguridad)
- [Solución de problemas](#solución-de-problemas)
- [Roadmap](#roadmap)
- [Versionado y changelog](#versionado-y-changelog)
- [Documentación](#documentación)
- [Autor y licencia](#autor-y-licencia)

---

## Características

| Área | Qué hace |
|---|---|
| **Inicio** | Hora (12 h) y fecha reales. Tu Onix sobre el valle con ruta; el fondo sigue la hora (día, atardecer, noche) y, si llueve, pasa a la versión con lluvia de ese momento. Saludo "Hola, Axel · Buenos días / Buenas tardes / Buenas noches" en la franja de abajo; tocándolo se cambia el nombre (los últimos usados quedan a un toque). Al abrir el launcher y al volver de la suspensión, presentación animada con el emblema y "FIND NEW ROADS". Las luces del auto siguen a la perilla de luces (en la radio NWD: aviso `com.nwd.ACTION_ILL_STATE_CHANGE` y ajustes `mcu_no_source_device_state` / `high_beam_state`); la franja muestra "Luces prendidas / apagadas" y el modo manual está en Diagnóstico. Panel de motor con velocímetro y cuentavueltas redondos, temperatura, nafta, batería y consumo estimado en km/l (OBD2), y el estado de los códigos de falla (tocando: lista explicada, leer de nuevo y borrar); sin datos dice "sin GPS" / "sin OBD2". |
| **Presentación y fiestas** | Al iniciar, sonido propio (v0.81.15: un acorde que entra de a poco y dos notas claras) y, si se elige, la voz con el slogan "Find new roads" (voz y sonido / solo sonido, por defecto / apagado). En fechas especiales de Argentina se decora el auto de Inicio con adornos animados (lucecitas que titilan por todo el auto en Navidad, bandera que flamea en fechas patrias, calabazas con vela en Halloween…) y el cielo suma un detalle. Se ajusta tocando "Hola, …" en Inicio. |
| **Mi auto** | Desde el dock: viajes anotados solos (km, tiempo, velocidad máxima y promedio, temperatura, litros y km/l con el OBD2), kilometraje del tablero, **aviso de velocidad** (máximo elegible, apagado de fábrica) y recordatorios (aceite y service por fecha y km, VTV, seguro, patente, otro) con aviso en Inicio una vez por día. |
| **Auto / CarPlay** | Dentro de Onix (v0.56.0, motor de CarLink): al enchufar el celular por USB arranca solo Android Auto (Android) o CarPlay (iPhone). El botón del dock muestra el estado. La identidad de CarPlay se arma sola al compilar: `docs/CARPLAY.md`. |
| **Modo liviano** | En radios lentas (automático): sin lluvia animada ni giro del emblema, presentación solo al abrir y mapa clásico por defecto. Ajustes → Apariencia → Modo liviano. |
| **Avisos de seguridad** | Cartel arriba de cualquier pantalla, pitido y voz (grabada si la radio no tiene TextToSpeech, v0.61.0): luces apagadas andando (desde 2 km/h, de día y de noche: acá es obligatorio andar con luces; avisa a los 5 s y no repite antes de 10 min) y alertas del motor (recalentado, batería que no carga, voltaje alto, batería baja). La voz necesita "Servicios de voz de Google" en la radio. |
| **Clima** | Datos reales de Open-Meteo: temperatura, mínima, estado, sensación térmica, humedad, viento, probabilidad de lluvia y pronóstico de 5 días. Usa tu ubicación; sin GPS, la casa del autor (Tafí Viejo). |
| **Música** | Muestra y controla lo que suena (Spotify, YouTube Music, la radio de la app u otro reproductor): carátula, título, artista, progreso, anterior / play / siguiente. |
| **Radio FM** | Interfaz de radio con display, dial de 87.5 a 108 MHz con ~77 emisoras de San Miguel de Tucumán en su frecuencia real, búsqueda, presintonías e interruptor **LOCAL / NACIONAL**. La señal local tiene prioridad. Logos de cada emisora, **favoritas** (estrella, mantener apretado) y **categorías** (Noticias, Deportes, Música, Religiosa, Cultura). Solo muestra las radios cuya señal de Tucumán funciona (revisión automática cada 3 días o con REVISAR). La radio que suena se ve y se controla desde Inicio y desde la pantalla de música. Sigue sonando con otras apps abiertas. En la radio NWD, interruptor **ANTENA / INTERNET**: por la antena del auto suenan todas las emisoras sin internet, ⏮ ⏭ buscan en el aire y se ve el nombre RDS. |
| **Navegación y música** | Con el botón Navegación o Música del dock (sin deslizar): mapa propio a la izquierda; Spotify (con listas, recientes y buscador) y radio a la derecha. |
| **Navegación** | Mapa propio: 3D (MapLibre con mosaicos de OpenFreeMap, por defecto) o clásico (OpenStreetMap, respaldo automático); búsqueda y rutas con tránsito por TomTom (ADR-0014); buscador, Casa, Trabajo, favoritos y recientes; mantener apretado un punto para ir ahí; navegación giro a giro con voz y recálculo, sin Google Maps ni Waze. |
| **Paneles** | Mantener apretado el fondo para mover y escalar clima, música, navegación y motor. La distribución se guarda. |
| **Ajustes** | Cinco categorías: Apariencia, Sonido y avisos, Auto y conexiones (lector OBD2 con **Reintentar**, luces, destinos del celular), Sistema (pantalla de inicio, actualizaciones, Acerca de, fallos) y Diagnóstico. |
| **Diagnóstico** | Categoría de Ajustes (v0.81.11): lecturas en vivo agrupadas (equipo y rendimiento, entorno, auto, medios y red) y la Prueba completa. Muestra quién atiende el botón Inicio de la radio. |
| **Acerca de** | Versión, autoría y créditos de terceros (desde Diagnóstico). |

### Todavía no implementado

- **Cámara de retroceso:** el botón se retiró hasta conocer la app de cámara del head unit (AUD-011).

### Implementado, falta probarlo en el auto

- **OBD2:** conexión y datos probados en el auto; falta probar los códigos de falla, las alertas y el consumo (v0.50.0, R-39 a R-41). El resto de las pruebas pendientes está en `docs/INSTRUCTIVO.md`.

---

## Requisitos

| | |
|---|---|
| **Android** | 7.1 o superior (API 25, desde v0.55.0). Objetivo: head unit con Android 7.1.1 |
| **Pantalla** | Horizontal. Diseñada para 1024x600; se escala a cualquier resolución |
| **Internet** | Datos o hotspot del celular, para clima y radio |
| **GPS** | Opcional: velocidad, ruta animada y clima de tu ubicación |
| **Apps opcionales** | Spotify |
| **Procesador** | ARM de 32 bits (`armeabi-v7a`, la radio Allwinner T3). Desde v0.68.0 no se instala en celulares solo de 64 bits (ADR-0013) |
| **Probado en** | Celular y la radio del auto (luces, R-38); lo nuevo, pendiente (`docs/INSTRUCTIVO.md`) |

---

## Instalación

1. En GitHub, abrí **Releases** y descargá `onix-launcher.apk` de la compilación más reciente.
2. Instalalo, permitiendo instalar apps desconocidas.
3. Abrí la app y aceptá el permiso de ubicación.
4. En la tarjeta de música tocá **Dar acceso** y habilitá Onix Launcher en el acceso a notificaciones.
5. (Opcional) Dock → **Ajustes** → **Sistema** → **Usar como inicio**, y elegí Onix Launcher y "Siempre". La misma fila dice si quedó elegido.

Desde v0.13.0 las actualizaciones se instalan encima, sin desinstalar.

---

## Uso

| Acción | Cómo |
|---|---|
| Ir a navegación y música | Tocar **Navegación** o **Música** en el dock; para volver, tocar **Inicio** o el botón Atrás de la radio |
| Navegar | Buscar o mantener apretado un punto del mapa → **Iniciar** → seguir el cartel y la voz → **Terminar** |
| Conectar Spotify | Tocar **Música** en el dock → pestaña Spotify → **Conectar con Spotify** → aceptar. Si la app se compiló con el secreto `SPOTIFY_CLIENT_ID` (v0.79.6) no hay nada que pegar; si no, antes hay que pegar el Client ID de tu app de developer.spotify.com (Redirect URI `onixlauncher://spotify`) |
| Personalizar el dock | Mantener apretado un ícono → × para quitar, + para agregar una app → **Listo** |
| Personalizar paneles | Mantener apretado el fondo → arrastrar el panel / manija azul para escalar → **Listo** (o **Restablecer**) |
| Radio | Dock → **Radios** → tocar o arrastrar el dial, ⏮ ⏭, presintonías, LOCAL / NACIONAL |
| Radio favorita | ★ en el display, o mantener apretada la presintonía; **★ FAVORITAS** filtra |
| Ajustes | Dock → **Ajustes** (tocar afuera para cerrar) |
| Diagnóstico | Dock → **Diagnóstico**, o Ajustes → **Diagnóstico** |
| Brillo siempre al máximo | Ajustes → Apariencia → **Brillo** (activado por defecto; tocá para volver al brillo de la radio) |
| Mandar un informe de todo | Ajustes → Diagnóstico → **Prueba completa** → **Iniciar** (hasta 2 min). Revisa todas las funciones, guarda `onix_informe_…txt` en **Descargas/OnixLauncher** y lo copia: mandá el archivo o pegá el texto en el chat. Cómo hacerla para que sirva (tres pruebas): [`docs/PRUEBA_COMPLETA.md`](docs/PRUEBA_COMPLETA.md) |
| Acerca de | Ajustes → Sistema → **Acerca de** |

---

## Especificaciones técnicas

| Ítem | Valor |
|---|---|
| Application ID / paquete | `com.axel.onixlauncher` |
| **minSdk** | **25** (Android 7.1) |
| **targetSdk** | **34** (Android 14) |
| **compileSdk** | **37** |
| Versión | `0.81.25` (en `version.properties`) |
| versionCode | `100 + número de compilación` de GitHub Actions |
| Lenguaje | Kotlin 2.2.10 (integrado en AGP 9, JVM target 17) |
| UI | Jetpack Compose, lienzo fijo de 1024x600 dp escalado |
| Arquitectura | Una Activity + Compose; dos servicios (radio y acceso a música) |
| Módulos Gradle | 1 (`:app`) |
| Build | Android Gradle Plugin 9.3.0, Gradle 9.5.0 (wrapper), JDK 21 (el código sigue en Java 17) |
| Optimización | R8 + reducción de recursos en release; solo 32 bits (ADR-0013, v0.68.0). El peso de cada APK sale en las notas de su release (v0.67.0: 38,7 MB con 32 y 64 bits; v0.68.0: 23,1 MB) |
| Firma | Release con clave fija (ver [Seguridad](#seguridad)) |
| Orientación | `sensorLandscape` |
| Código | ≈ 11.000 líneas de Kotlin propias en 41 archivos; ≈ 1.800 líneas de tests; más ≈ 368.000 líneas del motor de CarLink (`carlink-core/`, `vendor/`, de terceros) |

---

## Salidas a otras apps

Regla de oro 2 (`CLAUDE.md`): todo se usa dentro del launcher. Estas son las únicas salidas que quedan y por qué:

| Desde | Abre | Motivo |
|---|---|---|
| Dock → Teléfono | App de teléfono Bluetooth de la radio (`com.nwd.android.phone`; si no está, otra app de teléfono o el marcador) | Las llamadas por Bluetooth las maneja la app de teléfono de la radio; una app común no puede usar el manos libres |
| Dock → Ajustes (si se vuelve a agregar; también en Apps) | Ajustes de Android | Configuración del sistema |
| Música → "Dar acceso" | Ajustes de notificaciones | Configuración inicial (permiso para ver qué suena) |
| Spotify → "Conectar con Spotify" | Página de permiso de Spotify | Configuración inicial (una vez) |
| Ajustes → Sistema → "Usar como inicio" | Elegir launcher | Configuración inicial |
| Auto/CarPlay → "Autorizar" | Diálogo de Android para la VPN de CarPlay por cable | Configuración inicial (una vez) |
| Al encender (v0.79.14) | Spotify, por 4 s, y vuelve a Onix | Solo si Spotify era lo último, figura sonando y la radio no lo tiene como proceso de audio válido (`sys.nwd.valid.play.pid`): sin eso suena mudo hasta abrir su app. Android no deja otra opción (el "pausa y play" por la sesión no alcanzó) |
| Apps / íconos de apps en el dock | La app elegida | Es lo que pide el usuario |

## Dependencias

### Librerías de la app

| Dependencia | Versión | Uso |
|---|---|---|
| `androidx.compose:compose-bom` | 2026.02.01 | Versiones alineadas de Compose |
| `androidx.compose.ui:ui` | BOM | Interfaz |
| `androidx.compose.foundation:foundation` | BOM | Layouts, gestos, `HorizontalPager` |
| `androidx.compose.material3:material3` | BOM | Texto |
| `androidx.activity:activity-compose` | 1.9.0 | `ComponentActivity`, permisos |
| `androidx.core:core-ktx` | 1.13.1 | Utilidades, pantalla completa |
| `androidx.media3:media3-exoplayer` | 1.11.1 | Reproducción de radio |
| `androidx.media3:media3-session` | 1.11.1 | Sesión de medios y controles en segundo plano |
| `androidx.media3:media3-exoplayer-hls` | 1.11.1 | Radios en HLS (`.m3u8`) |
| `androidx.media3:media3-datasource` | 1.11.1 | HTTP del reproductor (redirecciones https/http) |
| `androidx.annotation:annotation-experimental` | 1.4.0 | `@OptIn` para APIs de Media3 |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 | Instala los perfiles de arranque de Compose y demás librerías en el APK instalado a mano (arranque más rápido) |
| `org.osmdroid:osmdroid-android` | 6.1.20 | Mapa de OpenStreetMap en la pantalla de navegación |
| `org.maplibre.gl:android-sdk` | 10.3.7 | Mapa 3D (último MapLibre con OpenGL ES 2.0, el de la radio) |
| `org.conscrypt:conscrypt-android` | 2.5.2 | Conexiones seguras modernas (TLS 1.3) en Android 7: sin esto, las rutas fallaban |

### Tests

| Dependencia | Versión | Uso |
|---|---|---|
| `junit:junit` | 4.13.2 | Tests |
| `org.robolectric:robolectric` | 4.12.2 | Android en la JVM |
| `io.github.takahirom.roborazzi:roborazzi` / `roborazzi-compose` | 1.20.0 | Capturas de pantalla |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | BOM | Soporte de Compose en tests |

### Build

| Herramienta | Versión |
|---|---|
| Android Gradle Plugin | 9.3.0 |
| Kotlin (plugin de Compose) | 2.2.10 |
| Compose Compiler | 1.5.14 |
| Gradle (wrapper) | 9.5.0 |
| JDK | 17 (Temurin en CI) |

Licencias de cada componente: [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

---

## Módulos

Seis módulos Gradle: `:app` (Onix, paquete `com.axel.onixlauncher`, la tabla de abajo) y los cinco del motor de CarLink en `carlink-core/` y `vendor/` (`:carlink-core`, `:carplay-shared`, `:carplay-common`, `:androidauto`, `:androidauto-contract`; ADR-0012). Código propio de Onix:

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Activity única, pantalla completa; anota las teclas que llegan (`onKeyDown`: de la radio o de música) para el informe |
| `LauncherApp.kt` | Raíz: reloj, música, clima, GPS, páginas y superposiciones |
| `HomeScreen.kt` | Pantalla de inicio: fondo, auto, encabezado (reloj y clima), pronóstico, música, navegación, motor y franja del vehículo |
| `NavMusicScreen.kt` | Segunda pantalla: mapa a la izquierda; música (Spotify / Radio) a la derecha |
| `Spotify.kt` | Spotify: inicio de sesión PKCE, listas, recientes, búsqueda y reproducir (Spotify Connect) |
| `MapNav.kt` | Pantalla del mapa (3D o clásico), buscador, ruta y nombre del lugar con TomTom (v0.75.0, ADR-0014), elegir un punto con el dedo, cartel de navegación; Casa, Trabajo, favoritos y recientes |
| `Navigation.kt` | Navegación giro a giro propia: maniobras, avisos por voz (TextToSpeech), recálculo |
| `EnginePanel.kt` | Telemetría de Inicio (con consumo y estado de los códigos) y ventana de códigos de falla con la salud del auto por sistema |
| `Dock.kt` | Barra inferior: Inicio fijo y 7 accesos personalizables (mantener apretado → quitar / agregar apps); Teléfono abre la app de la radio (`PhoneApp`) |
| `AppsScreen.kt` | Cajón de apps e íconos de las apps instaladas |
| `Obd.kt` | OBD2: `CarEngine` (bucle del lector y reloj de viajes, de proceso; v0.81.12), conexión Bluetooth con el ELM327, `ObdData`, `ObdState`; lee datos y códigos con `ObdReader`; reinicia el lector por la misma conexión si el auto no contesta; se recupera solo (`ObdRecovery`: tiempo máximo, tres formas de conectar, reintento al dar contacto; un solo socket por intento y esperas de 15 s a 10 minutos desde v0.81.14, porque la radio no libera los lugares de conexión; sin reinicio del Bluetooth desde v0.81.13) |
| `ObdCodes.kt` | OBD2 avanzado: códigos de falla (`Dtc`, descripciones en castellano), alertas del motor, consumo estimado, salud por sistema (`CarHealth`) y `ObdReader` (lo que se le pide al lector en cada vuelta) |
| `Projection.kt` | Pantalla Auto/CarPlay: estado del motor de CarLink en palabras, Dar permisos, Autorizar (VPN de CarPlay por cable), Volver a Android Auto / CarPlay, Desconectar y aviso de otra app de proyección; `EngineTrace` (v0.79.1): pasos del motor para la Prueba completa |
| `Lite.kt` | Modo liviano: detección de gama baja y elección (automático / sí / no) |
| `IntroSound.kt` | Sonido de inicio: `IntroChime` (sonido propio, un acorde y dos notas desde v0.81.15, puro), `IntroVoices` (voces para elegir del slogan, puro) y `IntroSound` (opciones, voz elegida, prueba y reproducción con la frase "Find new roads") |
| `Festive.kt` | Temas festivos de Argentina: `Festive.on` (fiesta de cada fecha, con Pascua y domingos variables), `Holiday`, `FestiveLayer` (detalle en el cielo) y `CarDecor` (adornos sobre el auto) |
| `VoiceClips.kt` | Voces grabadas: qué frases dicen los avisos y cada maniobra (`VoiceClips`) y su reproducción en orden (`RecordedVoice`) |
| `OnixApp.kt` | Clase Application: hereda de la del motor de Android Auto e instala el motor de CarLink (`OnixHost` vuelve a Inicio) |
| `CarCare.kt` | Mi auto (v0.79.0): pestañas Mantenimiento (`MaintItem`, `Maintenance`: "lo hice a los ___ km", "cada ___ km / meses"), Viajes (`TripTracker`: terminan al apagar el auto; litros por consumo o por el tanque; `Monthly`: por mes) y Motor (datos, códigos y borrar); kilometraje |
| `SafetyAlerts.kt` | Avisos de seguridad: luces apagadas andando, velocidad alta (`SpeedWarning`), alertas del motor y nafta baja (cartel, pitido y voz); modo de sonido y cada aviso por separado (`AlertPrefs`, v0.78.0) |
| `Lights.kt` | Luces del auto: según la radio (cable de luces; en la NWD, el ajuste `high_beam_state`), a mano o por hora; brillo al máximo para la ventana del launcher; avisos de la radio y, con Diagnóstico abierto, cambios de ajustes y propiedades para el informe |
| `RadiosScreen.kt` | Interfaz de radio FM (display, dial, presintonías, favoritas, LOCAL / NACIONAL, ANTENA / INTERNET) |
| `RadioLogos.kt` | Logos de las radios (descarga, caché) e insignias con iniciales |
| `RadioCatalog.kt` | Catálogo de radios, revisión de cuáles funcionan, categorías |
| `RadioNow.kt` | Estado de la radio para Inicio y la pantalla de música |
| `RadioProbe.kt` | Prueba de los enlaces de una radio desde Diagnóstico |
| `Radio.kt` | `Station`, `RadioDirectory`, `Radio` (por internet o, en modo antena, por el sintonizador), `RadioService` (Media3) |
| `NwdTuner.kt` | Radio FM por la antena: cliente del sintonizador de la radio NWD (`com.nwd.radio.service`) y cambio de fuente de audio |
| `TucumanFm.kt` | Dial de FM de San Miguel de Tucumán |
| `Weather.kt` | Clima de Open-Meteo: consulta y lectura |
| `Media.kt` | Sesiones de música por eventos (`MediaWatcher`), teclas de música para apps sin sesión (`mediaKey`) y fuente de audio devuelta a Android al poner contacto (`IgnitionAudio`) |
| `Location.kt` | GPS: velocidad, última ubicación y nombre del lugar; ubicación por defecto; velocidad en vivo que no depende de la pantalla (`GpsSpeed.live`) |
| `Ambient.kt` | Ambiente (día/atardecer/noche, con o sin lluvia), fondo, tono del auto, saludo según la hora y nombre del saludo (`GreetingName`) |
| `Map3D.kt` | Mapa 3D con MapLibre (estilo oscuro propio, edificios en 3D, inclinación y giro) y mapa clásico osmdroid de respaldo, con la misma interfaz `NavMap` |
| `Intro.kt` | Presentación animada (emblema y "FIND NEW ROADS") al abrir y al volver de la suspensión |
| `Road.kt` | `RenderFlags` (interruptor de animaciones) |
| `PanelLayout.kt` | Paneles movibles y escalables (`LayoutStore`) |
| `Diagnostics.kt` | Diagnóstico en vivo y botón de prueba completa |
| `SettingsScreen.kt` | Menú de Ajustes por categorías (Apariencia, Sonido y avisos, Sistema), navegación (`SettingsNav`) y registro de cierres inesperados (`CrashLog`) |
| `FullReport.kt` | Prueba completa: revisa todas las funciones (también Android Auto / CarPlay, memoria por proceso, Mi auto y teclas del volante), arma el informe, lo guarda como .txt en Descargas y lo copia; guía en `docs/PRUEBA_COMPLETA.md` |
| `About.kt` | Acerca de: autoría y créditos |
| `Net.kt` | HTTP seguro (`Net.get`), registro de errores (`AppLog`), vuelta de la red (`NetWatch`), reinicio del Wi-Fi sin nombres (`WifiRescue`, v0.81.19) y TLS moderno en Android 7 (`Tls`, Conscrypt); GET por TLS 1.3 directo para las rutas (`DirectHttps`) |
| `PhoneShare.kt` | Destinos desde el celular (v0.76.0): escucha el canal privado de ntfy, entiende direcciones, ubicaciones de WhatsApp y links de Maps, y pregunta "¿Ir a …?" |
| `NearbyPlaces.kt` | Lugares cerca por categoría y paradas en el camino con TomTom, Mis lugares (Casa, Trabajo y favoritos escribiendo la dirección) y aviso de nafta baja (v0.77.0) |
| `Ui.kt` | Paleta (grafito y azul), vidrio de los paneles, botones y apertura de otras apps |
| `Drawing.kt` | Íconos vectoriales |
| `MediaListenerService.kt` | Servicio vacío para el acceso a notificaciones |

Diagrama y flujos de datos: [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## Permisos

| Permiso | Para qué | Cuándo se pide |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Velocidad GPS y clima de tu ubicación | Al abrir la app |
| `INTERNET` | Clima y radio | Automático |
| `ACCESS_NETWORK_STATE` | Saber cuándo vuelve el Wi-Fi para reconectar la radio y el clima | Automático |
| `WRITE_EXTERNAL_STORAGE` (hasta Android 9) | Guardar el informe de la prueba completa en Descargas | Una vez, la primera vez que tocás **Prueba completa** (si no lo das, se guarda en la carpeta de la app) |
| `WAKE_LOCK` | Que la radio no se corte con la pantalla apagada | Automático |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Radio en segundo plano | Automático |
| `POST_NOTIFICATIONS` | Controles de la radio en la notificación (Android 13+) | Una sola vez, al abrir Radios |
| `BLUETOOTH` (hasta Android 11) | Conectarse al lector OBD2 ELM327 emparejado | Automático |
| `BLUETOOTH_ADMIN` (hasta Android 11) | Reiniciar el Bluetooth de la radio si el lector OBD2 no aparece (v0.73.0; ya lo sumaba el motor de CarLink) | Automático |
| `BLUETOOTH_CONNECT` (Android 12+) | Conectarse al lector OBD2 ELM327 emparejado ("Dispositivos cercanos") | Una sola vez, al abrir la app |
| `REQUEST_INSTALL_PACKAGES` | Instalar actualizaciones descargadas desde **Ajustes → Sistema** (previa verificación de firma del APK) | Al instalar una actualización |
| Acceso a notificaciones | Leer y controlar la música que suena | Botón **Dar acceso** |

**Motor de Android Auto y CarPlay (v0.56.0):** las librerías de CarLink suman sus permisos al instalar: USB y Bluetooth (`BLUETOOTH*`), Wi-Fi y red (`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_LOCAL_NETWORK`), ubicación (`ACCESS_COARSE_LOCATION`, la pide Android para Wi-Fi Direct), micrófono (`RECORD_AUDIO`, para llamadas y asistentes), audio (`MODIFY_AUDIO_SETTINGS`), llamadas (`READ_PHONE_STATE`, `MANAGE_OWN_CALLS`), servicios en primer plano (`FOREGROUND_SERVICE_CONNECTED_DEVICE`, `_MICROPHONE`, `_LOCATION`), arranque (`RECEIVE_BOOT_COMPLETED`), ventanas (`SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`), `BROADCAST_STICKY`, `WRITE_SETTINGS`, `PACKAGE_USAGE_STATS`, `REQUEST_INSTALL_PACKAGES` y `ACCESS_SUPERUSER` (opcionales del motor). Los peligrosos los pide el motor al conectar. Detalle en `docs/HEAD_UNIT.md`.

Componentes exportados: `MainActivity` (launcher), `HomeAlias` (pantalla de inicio, deshabilitado por defecto), `MediaListenerService` (protegido por permiso del sistema) y `RadioService` (sesión de medios, ver AUD-004).

---

## Servicios externos

| Servicio | Uso | Clave |
|---|---|---|
| [ElevenLabs](https://elevenlabs.io) | Voces con emoción del slogan de inicio, generadas una vez en la CI (`voces_ia.yml`); no se usa desde la radio | Clave del autor como GitHub Secret (`ELEVENLABS_API_KEY`), opcional |
| [Open-Meteo](https://open-meteo.com) | Clima y pronóstico (datos CC BY 4.0, atribución en Acerca de) | No requiere |
| [Radio Browser](https://www.radio-browser.info) | Buscar el enlace vigente de una radio | No requiere |
| Reproductor oficial de Cienradios | Enlace local de La 100 Tucumán | No requiere |
| World Radio Map | Logos de las radios del dial (se descargan en el equipo) | No requiere |
| radioarg.net | Fuente de los enlaces de audio de radios de Tucumán (consultada al programar, no desde la app) | — |
| StreamTheWorld (Triton Digital) | Audio de La 100 y Mitre | No requiere |
| OpenStreetMap (mosaicos) | Mapa de la pantalla de navegación (datos ODbL, atribución en el mapa y en Acerca de) | No requiere |
| [ntfy](https://ntfy.sh) (v0.76.0) | Recibir destinos que se comparten desde el celular (Compartir → ntfy); desde v0.81.23, enviar el informe de la Prueba completa a la PC del autor (canal aparte, `tools/bajar_informe.ps1`) | Canal privado del autor como GitHub Secret (`NTFY_TOPIC`); app ntfy en el celular |
| [TomTom](https://developer.tomtom.com) (v0.75.0) | Buscador de destinos (direcciones con altura), lugares cerca y en el camino (v0.77.0), ruta con tránsito y nombre del lugar | Clave del autor como GitHub Secret (`TOMTOM_API_KEY`), plan gratuito de 2.500 pedidos por día |
| [OpenFreeMap](https://openfreemap.org) | Mapa 3D: mosaicos vectoriales y letras (esquema OpenMapTiles) | No requiere |
| [Spotify Web API](https://developer.spotify.com/documentation/web-api) | Listas, recientes, búsqueda y reproducir (Premium) | Client ID de una app propia del autor: GitHub Secret `SPOTIFY_CLIENT_ID` (entra al compilar, v0.79.6) o, si no está, se pega en el equipo (no va al repo) |

La app no usa analítica ni envía datos personales. Sí usa la clave de TomTom y el canal de ntfy del autor (van dentro del APK, cargados desde GitHub Secrets al compilar; por eso las releases son privadas, AUD-067) y, si se conecta, la cuenta de Spotify del usuario (tokens solo en el equipo).

---

## Estructura del proyecto

```text
onix-launcher/
├── app/
│   ├── build.gradle.kts                 Configuración del módulo, SDKs y dependencias
│   ├── proguard-rules.pro               Reglas de R8
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/axel/onixlauncher/   Código Kotlin (ver Módulos)
│       │   └── res/
│       │       ├── drawable-nodpi/      Fotos y capas: auto, faros, fondos, emblema
│       │       ├── values/themes.xml
│       │       └── xml/network_security_config.xml
│       └── test/java/com/axel/onixlauncher/
│           ├── ScreenshotTest.kt        Capturas de las pantallas
│           └── logic/                   Tests de lógica
├── carlink-core/                        Motor de CarLink (AGPL-3.0): USB, sesión, registro (ADR-0012)
├── vendor/                              Motores de terceros: diauto/ (Android Auto), diplay/ (CarPlay); cambios en ONIX_CHANGES.md
├── gradle/wrapper/                      Gradle wrapper 9.5.0
├── .github/
│   ├── workflows/                       build.yml (compilación, tests y release), carlink.yml, lint.yml,
│   │                                    notas.yml, voces.yml, voces_ia.yml, tomtom_prueba.yml
│   └── scripts/                         check_docs.py, test_summary.py, lint_summary.py,
│                                        release_notes.sh, tag_versions.sh
├── tools/                               Scripts que generan imágenes y voces; carlink.properties
├── docs/
│   ├── audits/                          Auditorías
│   ├── decisions/                       Decisiones de arquitectura (ADR)
│   ├── releases/                        Notas de cada versión + snapshot
│   ├── screenshots/                     Capturas para este README
│   └── ARTIFACTS.md                     Paneles del proyecto en Claude
├── version.properties                   Versión SemVer (única fuente)
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── gradlew · gradlew.bat
└── README.md · CHANGELOG.md · VERSION.md · ARCHITECTURE.md · TODO.md
    AUDIT_REPORT.md · PROJECT_AUDIT.md · COPYRIGHT.md · THIRD_PARTY_LICENSES.md
```

---

## Compilación

Requisitos: JDK 21 y el Android SDK (plataforma 37). El wrapper descarga Gradle 9.5.0.

```bash
./gradlew :app:assembleDebug        # APK de prueba
./gradlew :app:assembleRelease      # APK optimizado y firmado (necesita la clave)
./gradlew :app:testDebugUnitTest    # tests de lógica y capturas
```

El APK queda en `app/build/outputs/apk/<tipo>/`. Las capturas, en `app/build/screenshots/`.

> Las releases se compilan en GitHub Actions y se descargan desde el celular. Desde 2026-11-05 la PC del autor también tiene JDK 21 y el SDK de Android (`%LOCALAPPDATA%\Android\Sdk`, NDK 28.2 y 29.0, CMake 3.22.1) para compilar y correr los tests sin la radio; la compilación release local usa `app/keystore.properties`.
>
> **Claves en la PC:** la CI recibe las claves de GitHub Secrets; en la PC se cargan una sola vez en `secrets.properties` (raíz del proyecto, en `.gitignore`), con los mismos nombres: `TOMTOM_API_KEY`, `NTFY_TOPIC`, `SPOTIFY_CLIENT_ID` y `UPDATER_READ_TOKEN`. Una variable de entorno con el mismo nombre tiene prioridad; una clave vacía deja esa función sin clave (el buscador avisa, Spotify pide el Client ID en la radio). La identidad de CarPlay se toma de `carplay-auth/offline-mfi/identity.pk8` y `certificate.p7b` si esa carpeta existe (también en `.gitignore`); sin ella el APK local sale sin CarPlay.

---

## Tests

| Suite | Tipo | Tests | Bloquea la release |
|---|---|---|---|
| `logic/RadioLogicTest` | JVM | 34 | Sí |
| `logic/AmbientAndMediaTest` | JVM | 22 | Sí |
| `logic/WeatherParseTest` | Robolectric | 6 | Sí |
| `logic/ObdParseTest` | JVM | 12 | Sí |
| `logic/PhoneShareStreamTest` | Robolectric | 6 | Sí |
| `logic/GpsFixTest` | JVM | 11 | Sí |
| `logic/ElmLinkTest` | JVM (lector simulado por flujos) | 6 | Sí |
| `logic/NetTest` | JVM (servidor HTTP local) | 12 | Sí |
| `logic/WifiRescueTest` | JVM | 4 | Sí |
| `logic/ResilienceTest` | JVM | 3 | Sí |
| `logic/ReportUploadTest` | JVM | 3 | Sí |
| `logic/BtDevicesTest` | JVM | 3 | Sí |
| `logic/SpotifyTokenTest` | Robolectric | 14 | Sí |
| `logic/SpotifyClientIdTest` | JVM | 4 | Sí |
| `logic/RadioServiceLifecycleTest` | Robolectric (servicio real) | 2 | Sí |
| `logic/DockTest` | JVM | 3 | Sí |
| `logic/NavApiTest` | Robolectric | 6 | Sí |
| `logic/PhoneShareTest` | Robolectric | 5 | Sí |
| `logic/NearbyPlacesTest` | Robolectric | 5 | Sí |
| `logic/AlertPrefsTest` | JVM | 3 | Sí |
| `logic/GuidanceTest` | JVM | 3 | Sí |
| `logic/FullReportTest` | JVM | 8 | Sí |
| `logic/AuditRegressionTest` | Robolectric | 3 | Sí |
| `logic/SpotifyTest` | Robolectric | 6 | Sí |
| `logic/SavedPlacesTest` | JVM | 2 | Sí |
| `logic/ObdCodesTest` | JVM (con `FakeElm327`, lector simulado) | 13 | Sí |
| `logic/CarHealthTest` | JVM | 4 | Sí |
| `logic/IntroAndFestiveTest` | JVM | 9 | Sí |
| `logic/SafetyAlertsTest` | JVM | 6 | Sí |
| `logic/TunerMathTest` | JVM | 4 | Sí |
| `logic/CarCareTest` | JVM | 12 | Sí |
| `logic/CarCareStorageTest` | Robolectric | 4 | Sí |
| `logic/ProjectionTest` | JVM | 9 | Sí |
| `logic/LiteModeTest` | JVM | 2 | Sí |
| `logic/VoiceClipsTest` | JVM | 4 | Sí |
| `logic/ObdRecoveryTest` | JVM | 13 | Sí |
| `logic/ObdConnectTest` | JVM | 8 | Sí |
| `logic/EngineTraceTest` | JVM | 5 | Sí |
| `logic/UsbZeroLengthPacketTest` | JVM | 1 | Sí |
| `logic/IgnitionAudioTest` | JVM | 5 | Sí |
| `logic/IgnitionLogTest` | JVM | 3 | Sí |
| `logic/DirectHttpsTest` | JVM | 6 | Sí |
| `ScreenshotTest` | Robolectric + Roborazzi (sin aserciones) | 22 | No |

Qué cubren los tests de lógica: frecuencias de las emisoras, orden de enlaces LOCAL / NACIONAL, mezcla del dial con el directorio, dominios de confianza, listas `.m3u`/`.pls`, ambiente por hora, luces del auto (según la radio, a mano o por hora), códigos de clima, lectura de la respuesta de Open-Meteo, posición de la música, autoría, códigos de falla y consumo del OBD2 contra un ELM327 simulado, alertas del motor y aviso de luces.

Último resultado de la CI (compilación 158, v0.79.15): **294 ejecutados, 294 exitosos, 0 fallidos** (276 de lógica + 18 capturas), cobertura 23,8 % (2028 de 8517 líneas; la compilación 151 daba 23,6 %; la compilación 146 daba 16,2 % porque no contaba las clases que cargan los tests de Robolectric, AUD-083; la 147, 20,4 %; la 148, 23,1 %). Desde v0.63.0 la CI mide la cobertura de los tests de lógica (JaCoCo); desde v0.79.5 también cuenta las clases que cargan los tests de Robolectric (AUD-083: antes daban 0 %). Cada release publica `tests.txt` con el detalle y la cobertura.

---

## CI/CD

Cada push a `main` ejecuta [`.github/workflows/build.yml`](.github/workflows/build.yml), con cuatro trabajos (desde v0.68.0, ADR-0013, los tres primeros en paralelo):

| Trabajo | Qué hace | Bloquea la release |
|---|---|---|
| **tests** | Revisa la documentación, valida el Gradle wrapper y corre los tests de lógica con cobertura | Sí |
| **app** | Compila la versión release (R8, solo 32 bits, firma fija) | Sí |
| **controles** | Capturas de las pantallas | No |
| **Voces grabadas** (`voces.yml`, a pedido o al cambiar `tools/voces/frases.txt`) | Genera las frases con Piper y las guarda en `app/src/main/assets/voz/` | No |
| **Lint completo** (`lint.yml`, aparte: lunes y a pedido) | Android Lint de todo el código de Onix (~25 min con los motores). `NewApi` se revisa igual en cada compilación: es fatal en el lint rápido de la release | No |
| **publicar** (al terminar los tres) | Arma `tests.txt` y `lint.txt`, publica la release `build-<número>` con el APK, las capturas y la descripción (`release_notes.sh`: nota de `docs/releases/`, resultados y tamaño del APK), y crea el tag `vX.Y.Z` si la versión es nueva | — |
| **CarLink al día** (`carlink.yml`, todos los días a las 07:23 y a pedido, v0.74.0) | Si sale una versión de CarLink o la fijada deja de estar publicada: verifica el APK nuevo con su `.sha256`, actualiza `tools/carlink.properties` (identidad de CarPlay) y pide una compilación; si cambió la versión de los motores, abre un aviso (issue) para actualizarlos | No |
| **Rehacer notas de releases** (`notas.yml`, a pedido, v0.68.1) | Vuelve a armar la descripción de releases ya publicadas con la nota actual de `docs/releases/`, conservando sus resultados | No |

No compilan los cambios que tocan solo documentación (`*.md`, `docs/`) o los scripts de imágenes (`tools/`). Gradle usa caché de compilación y queda abierto entre pasos. Las versiones anteriores a v0.18.0 se identifican por su release `build-N` (equivalencias en `CHANGELOG.md`).

---

## Configuración

| Elemento | Dónde |
|---|---|
| Versión SemVer | `version.properties` |
| SDKs, dependencias, firma | `app/build.gradle.kts` |
| Ubicación por defecto del clima | `Location.kt` → `DEFAULT_LAT`, `DEFAULT_LON` |
| Emisoras y enlaces | `TucumanFm.kt` |
| Dominios de reproductores de confianza | `Radio.kt` → `RadioDirectory.trustedHosts` |
| Seguridad de red | `res/xml/network_security_config.xml` |

Preferencias guardadas en el equipo: `layout` (paneles), `radio` (LOCAL / NACIONAL), `permisos` (permiso de notificaciones ya pedido), `velocidad` (máximo del aviso de velocidad, v0.63.0), `inicio` (sonido de inicio, v0.65.0; voz elegida, v0.69.0), `fiestas` (temas festivos, v0.65.0).

Variables de entorno: solo `GITHUB_RUN_NUMBER` en la CI (define el versionCode).

---

## Seguridad

- **Red:** HTTPS obligatorio para Open-Meteo, Radio Browser, Cienradios, OpenStreetMap, OpenFreeMap, TomTom, ntfy y Spotify. Además de los certificados del sistema se confía en las raíces públicas de Let's Encrypt (ISRG Root X1 y X2), que Android 7.1 no trae completas. Todo lo demás admite HTTP: el audio de las radios (muchas argentinas solo transmiten así) y los logos y páginas de emisoras. Riesgo aceptado: es contenido público y no se envía ningún dato del usuario por HTTP (AUD-031).
- **Servicio de radio:** está exportado porque los controles del sistema lo necesitan, pero solo acepta órdenes de la propia app (clave al azar por proceso, AUD-029).
- **Enlaces de terceros:** solo se aceptan páginas y enlaces de audio de dominios de confianza (`cienradios.com`, `cienradios.com.ar`).
- **Spotify:** los tokens se guardan solo en el equipo (SharedPreferences `spotify`); no están en el código, en el repo ni en los registros. El Client ID viene del secreto `SPOTIFY_CLIENT_ID` (dentro del APK, que es privado) o se pega a mano y queda en el equipo; no es un secreto de verdad (PKCE, sin "client secret"), pero tampoco va al repo. "Desconectar Spotify" los borra.
- **Datos personales:** ninguno sale del equipo, salvo el informe de la Prueba completa, que se envía al canal privado de ntfy del autor cuando se corre la prueba (lecturas del equipo y del auto, sin claves; dura unas horas en el servidor), y salvo la ubicación aproximada que se envía a Open-Meteo para el clima, y tu ubicación y el destino que buscás, que se envían a TomTom para buscar, calcular la ruta y poner el nombre del lugar. Los destinos que se mandan desde el celular pasan por ntfy.sh, en un canal con nombre secreto.
- ⚠ **Clave de firma (AUD-001, HIGH):** en esta versión pública la clave **no** está en el repositorio: cada quien firma con la suya (ver `docs/SIGNING.md`). La compilación ya acepta la clave desde GitHub Secrets (sin cambiarla): pasos en [`docs/SIGNING.md`](docs/SIGNING.md) y [ADR-0010](docs/decisions/ADR-0010-firma-desde-secretos.md). Rotarla obliga a desinstalar una vez.

---

## Solución de problemas

Recuperación y volver al launcher original: [`docs/HEAD_UNIT.md`](docs/HEAD_UNIT.md#5-instalación-actualización-desinstalación-y-recuperación).

| Problema | Causa probable |
|---|---|
| La música no aparece | Falta el acceso a notificaciones (**Dar acceso**). Se detecta solo al habilitarlo |
| Velocímetro en "--" | Sin permiso de ubicación o sin señal GPS |
| Clima "sin datos" | Sin internet. Reintenta cada minuto |
| Radio "SIN TRANSMISIÓN ONLINE" | La emisora no tiene enlace conocido ni figura en el directorio |
| Radio con "SEÑAL NACIONAL" | No hay señal local disponible en ese momento |
| Radio "EN PAUSA · OTRA APP" | Otra app (Spotify, YouTube, un video) tomó el audio. Tocá play |
| Radio "EN PAUSA · AUDIO DESCONECTADO" | Se cortó el Bluetooth o se desenchufaron los auriculares |
| Algo no anda | Diagnóstico → **Último error**. Con `adb`: `adb logcat -s OnixLauncher` |
| No se instala la actualización | Si venís de v0.12.0 o anterior, desinstalá una vez |

---

## Roadmap

| Versión | Contenido |
|---|---|
| v0.17.0 ✅ | Correcciones de la auditoría, Acerca de, tests de lógica, wrapper |
| v0.20.0–v0.24.0 ✅ | Radio de Tucumán con logos, favoritas y señal nacional |
| v0.25.0–v0.30.0 ✅ | Rediseño de Inicio, fondos, luces según la perilla |
| v0.31.0 ✅ | OBD2 con ELM327 Bluetooth |
| v0.33.0–v0.37.0 ✅ | Dock personalizable, navegación propia, Spotify dentro del launcher |
| v0.38.x ✅ | Todo dentro del launcher, retomar audio al encender, revisión técnica |
| v0.49.0–v0.53.0 ✅ | Luces por la señal de la radio, códigos de falla y consumo, FM por la antena, Mi auto, arranque más rápido |
| v0.54.0–v0.60.1 ✅ | Android Auto y CarPlay dentro de Onix (motor de CarLink), modo liviano, auditorías 1 y 2 |
| v0.61.0–v0.67.1 ✅ | Voces grabadas, Prueba completa ampliada, aviso de velocidad, salud del auto, sonido de inicio, temas festivos sobre el auto, auditorías 3 y 4 |
| Próximo | Pruebas en el auto (`docs/INSTRUCTIVO.md`), memoria del motor de Android Auto, aire acondicionado por OBD2, mapa sin internet |
| v1.0.0 | Estable, probado en el auto |

Detalle: [`TODO.md`](TODO.md).

---

## Versionado y changelog

[SemVer](https://semver.org/lang/es/) (`MAJOR.MINOR.PATCH`). La versión vive en `version.properties`; el número de compilación de GitHub sube el `versionCode`. En la app se ve como `0.18.0 · build N`.

- Versión actual: [`VERSION.md`](VERSION.md)
- Historial: [`CHANGELOG.md`](CHANGELOG.md)
- Notas de cada versión: [`docs/releases/`](docs/releases/)

---

## Documentación

| Documento | Contenido |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Arquitectura, módulos, flujos de datos |
| [`AUDIT_REPORT.md`](AUDIT_REPORT.md) | Auditoría y estado de cada hallazgo |
| [`PROJECT_AUDIT.md`](PROJECT_AUDIT.md) | Estado del proyecto y roadmap |
| [`CLAUDE.md`](CLAUDE.md) | Reglas del proyecto: la documentación va en el mismo cambio que el código (la CI lo verifica) |
| [`TODO.md`](TODO.md) | Pendientes |
| [`docs/HEAD_UNIT.md`](docs/HEAD_UNIT.md) | Compatibilidad con Android 7.1, instalación, recuperación y hallazgos del control técnico |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Plan de pruebas (CI, celular y radio) con su estado |
| [`docs/decisions/`](docs/decisions/) | Decisiones de arquitectura (ADR) |
| [`docs/ARTIFACTS.md`](docs/ARTIFACTS.md) | Paneles del proyecto |
| [`docs/PRUEBA_COMPLETA.md`](docs/PRUEBA_COMPLETA.md) | Qué revisa la Prueba completa y cómo hacerla |
| [`docs/INSTRUCTIVO.md`](docs/INSTRUCTIVO.md) | Qué tiene que hacer el autor, paso a paso |

---

## Autor y licencia

**Axel Hernán González** — desarrollador y autor del proyecto.

© 2026 Axel Hernán González. Licencia **AGPL-3.0** desde v0.56.0 ([`LICENSE`](LICENSE)), porque incluye el motor de Android Auto y CarPlay de CarLink (ADR-0012). Uso personal; repositorio privado. Ver [`COPYRIGHT.md`](COPYRIGHT.md).
Los componentes de terceros (librerías, servicios, fotos, marcas) mantienen sus licencias y derechos: ver [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md). Chevrolet y Onix son marcas de General Motors; este proyecto no tiene relación con GM.
