# Changelog

Todos los cambios importantes del proyecto se documentan en este archivo.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versionado con [SemVer](https://semver.org/lang/es/).

Las versiones v0.1.0 a v0.15.0 se reconstruyeron desde el historial de git y de GitHub Actions. Cada una corresponde a una compilación (build) publicada en GitHub Releases. Los builds 1 a 12 mostraban `versionName "0.1"` dentro de la app; los builds 13 a 16 mostraban `0.<build>`; desde v0.17.0 la app muestra la versión SemVer (`0.17.0 · build N`).

## [Unreleased]

## [v0.81.25] - 2026-11-13

Pedido del autor tras el informe de la mañana (OBD2 sin conectar): ver quién está emparejado con la radio.

### Added
- **Bluetooth emparejado en la Prueba completa (`FullReport.kt`, `BtDevices`):** nueva fila "Bluetooth emparejado con la radio" en la sección OBD2, con nombre, tipo, estado de conexión y los últimos 5 caracteres de la dirección de cada equipo. El estado se lee con `BluetoothDevice.isConnected()` (no pública, por reflexión); si no está disponible queda "estado desconocido". Sin Bluetooth o apagado: N/D.

### Tests
- `BtDevicesTest` (nuevo, 3): descripción de cada equipo, dirección acortada y resumen de la lista.
- Suite en la PC: 341 ejecutados, 340 exitosos, 0 fallidos, 1 omitido. La lectura real del Bluetooth no se probó en la PC; falta la radio.

## [v0.81.24] - 2026-11-12

Hallazgo del informe de la Prueba completa de las 21:48 (v0.81.23): "Actualizar" decía "Sin conexión a internet" con la red andando.

### Fixed
- **Token del actualizador (`SettingsScreen.kt`, `Updates.isValidToken`):** el botón "Cambiar token" había guardado el texto del informe (que la Prueba completa deja en el portapapeles); el encabezado `Authorization` con saltos de línea hacía fallar el pedido (`IllegalArgumentException`) y se mostraba como falta de internet. Ahora el token guardado debe ser de 8 a 255 caracteres `A-Z a-z 0-9 _ - .` sin espacios; si no lo es se ignora y se usa el compilado (`UPDATER_READ_TOKEN`). Al pegar, un texto inválido se rechaza con un aviso.
- **Registro (`Net.request`):** un `IllegalArgumentException` por pedido mal armado ya no se registra con su mensaje (traía el valor del encabezado, o sea el token) ni cuenta como falla de red para `WifiRescue`.

### Tests
- `SettingsAndThemeTest`: un texto pegado que no es token se ignora (1 nuevo).
- Suite en la PC: 338 ejecutados, 337 exitosos, 0 fallidos, 1 omitido. El token compilado se probó desde la PC contra la API de GitHub (HTTP 200, `build-190`, APK con redirección). Sin prueba en la radio.

## [v0.81.23] - 2026-11-12

Pedidos del autor: que el informe de la Prueba completa llegue a la PC sin mandarlo a mano, y sugerencias al escribir en el buscador del mapa.

### Added
- **Envío del informe a la PC (`FullReport.kt`, `ReportUpload`; `tools/bajar_informe.ps1`):** al terminar la Prueba completa el texto se sube con `PUT` a un canal de ntfy aparte (el de destinos más `-informes`, para que la radio no reciba su propio informe), con el nombre del archivo en el encabezado `Filename`. Antes de enviar se guarda en el equipo; si no hay red sale cuando vuelve (`NetWatch.announce` → `ReportUpload.retry`). En la PC, `tools\bajar_informe.ps1` consulta el canal y baja los archivos a `informes/` (fuera de git); solo acepta nombres `onix_informe_*.txt`. El adjunto dura unas 3 horas en ntfy.sh.
- **Sugerencias en el buscador del mapa (`MapNav.kt`, `LiveSearch`):** con 4 letras o más busca solo tras 600 ms sin escribir, sin cerrar el teclado; una respuesta que llega después de seguir escribiendo se descarta. Cada búsqueda es una consulta a TomTom.

### Changed
- **Texto de la Prueba completa (`Diagnostics.kt`):** muestra el estado del envío a la PC en lugar de "Mandalo en el chat".

### Tests
- `ReportUploadTest` (nuevo, 3): canal de informes, texto del estado y cuándo se sugiere.
- Suite en la PC: 337 ejecutados, 336 exitosos, 0 fallidos, 1 omitido. Envío y descarga probados desde la PC con un archivo de prueba (HTTP 200, 12.299 bytes de ida y vuelta). Sin prueba en la radio.

## [v0.81.22] - 2026-11-12

Hallazgos de prioridad media de la revisión de reintentos del 12/11/2026 (`TODO.md`). Publicada cuando el autor lo pidió (había pedido frenar las versiones).

### Fixed
- **Destino del celular sin internet (`PhoneShare.kt`, `handle`, `failureText`, `retryWaiting`):** el mensaje del canal quedaba leído y se mostraba "No encontré". Con los nombres caídos queda en espera ("Sin internet: busco … cuando vuelva") y se busca de nuevo al volver la red.
- **Catálogo de radios (`RadioCatalog.load`, `retry`):** `loaded` se marcaba antes de pedir el directorio; ahora solo si contestó, y `loading` se baja en `finally`. Al volver la red se pide de nuevo.
- **Revisión de radios con mala señal (`RadioCatalog.shouldSaveCheck`):** no se guarda con los nombres caídos ni si funcionan menos de la mitad de las que funcionaban (antes bastaba con que contestara una).
- **Logos (`RadioLogos.kt`):** una falla con la red caída no se anota en la lista de enlaces fallidos ni como "sin logo" por 7 días.
- **Mapa 3D (`MapNav.kt`):** si pasó al clásico solo, se prueba el 3D de nuevo cuando vuelve la red.
- **Ruta que falla por la red (`MapNav.kt`, `RouteRetry`):** se reintenta a los 30, 60, 90 y cada 120 s (3 s si la red ya volvió), hasta 20 veces. El arranque automático (`autoStart`) solo vale para el destino que lo pidió.

### Changed
- **Aviso único de vuelta de la red (`NetWatch.announce`):** lo usan el aviso de Android y `WifiRescue`; además de la interfaz y la radio, reintenta el catálogo y el destino en espera.
- **Más vías cuentan para `WifiRescue`:** `Net.getBytes` (logos y portadas) y `DirectHttps` (rutas y búsqueda).

### Sin cambio
- "Una respuesta pone la cuenta de fallas en cero": se deja así a propósito. Contar por servidor haría reiniciar el Wi-Fi por un nombre que dejó de existir (una radio dada de baja); la prueba de red cada 30 s decide si hay internet.

### Tests
- `ResilienceTest` (nuevo, 3): mensaje y espera del destino, cuándo se guarda una revisión de radios, esperas del reintento de ruta.
- Suite en la PC: 334 ejecutados, 333 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.21] - 2026-11-12

Revisión de todo el código propio (`app/src/main/java/com/axel/onixlauncher/`, 12/11/2026) buscando componentes que, tras una falla, no reintentan solos. Pedido del autor después del informe de las 17:18 (Wi-Fi conectado, sin nombres). Hallazgos de prioridad alta corregidos acá; los de media y baja quedan en `TODO.md`.

### Fixed
- **La vuelta de la red no se veía si el Wi-Fi nunca se desconectaba (`Net.kt`, `WifiRescue.answered`, `recovered`):** `NetWatch.back` solo subía con `onLost` seguido de `onAvailable`. Ahora, cuando un servidor contesta después de 2 o más fallas de nombre (o después de un reinicio del Wi-Fi), sube `NetWatch.back` y se llama a `LastAudio.resumeRadioAfterNetwork`: clima, lugar, listas, letra y radio piden de nuevo.
- **La caída tardaba en detectarse (`WifiRescue.watch`, `probe`):** solo contaban las fallas de `Net.get` y `Net.request`; con el clima ya cargado eso es un pedido cada 15 minutos. Mientras los nombres no resuelven se prueba la red cada 30 s con un pedido mínimo (`connectivitycheck.gstatic.com/generate_204`, sin registro en "Últimos eventos").
- **La radio por internet quedaba muda tras una caída (`Radio.kt`, `fail`):** agotados los reintentos (unos 20 s) paraba y solo volvía con un aviso de red de Android. Ahora `fail` pide una prueba de red (`WifiRescue.suspect`); si los nombres no resuelven queda la vigilancia y, al volver, se retoma la emisora.
- **Listas de Spotify y letra (`NavMusicScreen.kt`):** `SpotifyLibrary`, `MiniLyricsBar` y `LyricsPanel` vuelven a pedir cuando sube `NetWatch.back` (antes había que cambiar de pestaña o de tema).

### Verificado sin cambios
- `CHANGE_WIFI_STATE` está en el manifiesto final (viene de las librerías de CarLink) y figura concedido en el informe de la radio.
- Reintentan bien: clima (60 s sin datos, 15 min con datos), lector OBD2, destinos del celular (ntfy), acceso a las sesiones de música, recálculo de ruta, token de Spotify.

### Tests
- `WifiRescueTest` (+1, 4): cuándo se da por vuelta la red.
- Suite en la PC: 331 ejecutados, 330 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.20] - 2026-11-12

Prueba del autor en la radio (v0.81.18/19): al abrir una lista aparecía "Spotify no deja ver los temas de esta lista".

### Fixed
- **Temas de una lista (`Spotify.kt`, `SpotifyApi.playlistTracks`, `parsePlaylistTracks`):** Spotify eliminó `GET /playlists/{id}/tracks` (cambios de febrero de 2026; las apps en modo desarrollo migraron el 9 de marzo de 2026). Ahora se usa `GET /playlists/{id}/items`, con 50 por página (el máximo actual), y el tema se lee de `item` (se acepta `track` por compatibilidad). Fuente: referencia y guía de migración de la Web API de Spotify, leídas el 12/11/2026. La dirección nueva devuelve 403 en listas que no son propias ni colaborativas: en ese caso sigue el aviso.
- **Cantidad de temas en Listas (`parsePlaylists`):** el objeto de la lista cambió `tracks` por `items`; se leen los dos.

### Tests
- `SpotifyTest` (6): el tema en `item` y en `track`, y la cantidad de temas en `items.total`.
- Suite en la PC: 330 ejecutados, 329 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.19] - 2026-11-12

Informe del auto del 12/11 17:18 (v0.81.18): a las 17:11 se cortó la red (el celular que la comparte se quedó sin señal), a las 17:14 volvió, y desde entonces todos los pedidos fallaron con `UnknownHostException` (Spotify, TomTom, Open-Meteo, Radio Browser, LRCLIB) con el Wi-Fi "conectado".

### Added
- **Reinicio del Wi-Fi cuando los nombres no resuelven (`Net.kt`, `WifiRescue`):** `Net.get` y `Net.request` cuentan las fallas de nombre seguidas; con 3 o más durante al menos un minuto, con el Wi-Fi como red activa y sin Android Auto ni CarPlay, se apaga y prende el Wi-Fi (`WifiManager.setWifiEnabled`, 3 s apagado). Si no alcanza, se repite a los 5, 10 y cada 20 minutos. Cualquier respuesta de un servidor reinicia la cuenta.

### Changed
- **Listas de Spotify sin internet (`NavMusicScreen.kt`):** con los nombres caídos dice "Sin conexión a internet: las listas no se pueden cargar" (antes "No hay nada para mostrar").

### Tests
- `WifiRescueTest` (nuevo, 3): cuándo reinicia, esperas entre reinicios y qué fallas cuenta.
- Suite en la PC: 330 ejecutados, 329 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.18] - 2026-11-12

Pedido del autor: poder manejar la música sin que la app le dé play sola, y la letra al pie más grande sin achicar la lista.

### Added
- **Ajuste "Retomar la música al dar contacto" (`Media.kt`, `LastAudio.autoResumeEnabled`, `setAutoResume`, `mayAutoPlay`; `SettingsScreen.kt`, `SoundSettings`):** prendido por defecto (como antes). Apagado, la app no da play por su cuenta: ni al dar contacto o arrancar (`LastAudio.resume`), ni al volver la red (`resumeRadioAfterNetwork`), ni desde `IgnitionAudio.kickSpotify`. La fuente de audio se sigue devolviendo a Android al dar contacto (`NwdTuner.bounceToAndroid`), para que el play manual se escuche.
- **Ajustes → Sonido y avisos → "Cuenta de Spotify":** botón Desconectar (aparece con la cuenta conectada).

### Changed
- **`kickSpotify` respeta Android Auto y CarPlay:** con una sesión activa no manda play ni pasa Spotify al frente (antes solo `LastAudio.resume` lo consideraba).
- **Letra al pie (`MiniLyricsBar`):** de 17 a 20 sp. El renglón "Desconectar Spotify" de la biblioteca se quitó (pasó a Ajustes) y su lugar lo usa el recuadro.

### Tests
- `IgnitionAudioTest` (+1, 5): `LastAudio.mayAutoPlay`.
- Suite en la PC: 327 ejecutados, 326 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.17] - 2026-11-12

Pedido del autor: ver los temas de una lista de Spotify para elegir, y agrandar el recuadro de la letra en Navegación → Música.

### Added
- **Temas de una lista (`Spotify.kt`, `SpotifyApi.playlistTracks`, `parsePlaylistTracks`, `playlistId`; `NavMusicScreen.kt`, `SpotifyLibrary`):** tocar una lista la abre y muestra sus temas (`GET /playlists/{id}/tracks`, de a 100, hasta 500). Cada tema se reproduce con la lista como contexto (`context_uri` + `offset.uri`), así sigue con los siguientes. "▶ Todo" reproduce la lista entera y "‹ Listas" vuelve. Si Spotify no deja leer la lista, se avisa. Antes, tocar una lista la reproducía desde el primer tema.

### Changed
- **Recuadro de letra al pie (`MiniLyricsBar`):** letra de 13 a 17 sp, hasta dos renglones y más alto.

### Tests
- `SpotifyTest` (+1, 6): temas de una lista (temas vacíos o locales descartados, total, contexto de la lista y cuerpo de reproducción).
- Suite en la PC: 326 ejecutados, 325 exitosos, 0 fallidos, 1 omitido. Sin prueba en la radio.

## [v0.81.16] - 2026-11-12

Causa de que Spotify se cerrara al dar contacto, encontrada con el registro de la radio y el código de su sistema (`android.os.NwdAudioService`, en `boot-framework.oat`).

### Fixed
- **Spotify ya no pasa al frente al dar contacto si la radio lo protege (`Media.kt`, `IgnitionAudio.radioProtects`, `kickSpotify`):** unos 0,6 s después de despertar, el servicio de FM de la radio pone `key_arm_fm_audio_mode=1` por unos 20 ms y `NwdAudioService` cierra toda app que reproduce audio (`fmState = 1, kill play process in background`). Se salvan las apps de una lista fija, las de la radio y las de navegación (`/nwdappconfig/app/NaviSound.ini`). Con `com.spotify.music` agregado a esa lista (2026-11-12, `docs/RADIO_NWD.md`), `pidof` dio 9938 antes y después de apagar y prender el contacto, y sonó con `sys.nwd.valid.play.pid` vacío. La app lee la lista: si Spotify figura, no lo abre (si quedó en pausa, solo le da play); si no figura o no se puede leer, sigue como antes.

### Tests
- `IgnitionAudioTest` (+1, 4): lectura de la lista de la radio (nombre completo, líneas comentadas, archivo ilegible).
- Suite en la PC: 325 ejecutados, 324 exitosos, 0 fallidos, 1 omitido. Esta versión no se probó todavía en el auto.

## [v0.81.15] - 2026-11-11

Pedido del autor: cambiar el sonido de inicio por uno más actual y dejar el slogan apagado por defecto.

### Changed
- **Sonido de inicio nuevo (`IntroSound.kt`, `IntroChime`):** un acorde de re mayor con novena que entra en 0,55 s (dos voces apenas desafinadas por nota) y dos notas claras arriba (la y re agudos), 2,6 s en total; antes eran tres campanas de 1,6 s. Compuesto en el código: no copia el sonido de ninguna marca.
- **Sin elección guardada suena solo el sonido (`IntroSound.savedMode`):** antes el valor por defecto era voz y sonido. La voz del slogan sigue como opción; una elección ya guardada se respeta.

### Tests
- `IntroAndFestiveTest` (+2, 9): el sonido entra suave (los primeros 50 ms por debajo de un cuarto del pico) y el modo por defecto.
- Suite en la PC: 324 ejecutados, 323 exitosos, 0 fallidos, 1 omitido. Sin prueba de oído en la radio.

## [v0.81.14] - 2026-11-11

Causa de las fallas del lector OBD2 encontrada en el auto con el registro de la radio (`logcat` por ADB sobre WiFi, como root). Corrige el supuesto de la v0.81.13: el problema principal no estaba en la lógica de reconexión.

### Fixed
- **Los reintentos agotaban los lugares de conexión del Bluetooth de la radio (`Obd.kt`, `ObdRecovery.connectVerified`, `retryDelayMs`):** en la radio NWD el `BluetoothSocket` está reemplazado (etiqueta `goc`) y habla con el programa `/system/bin/gocsdk_xr829` (etiqueta `nwdbt`). `connect()` vuelve al instante aunque no haya enlace; el enlace real se ve como `spp_socket_on_data spp Connected <dirección>`. Cada socket abierto ocupa un `client_fd` que el programa no libera ni al cerrarlo ni al morir la app (13, 21 y 22 en 20 s con Onix reintentando; 21 sockets acumulados con Onix detenido). Agotados, contesta `on_spp_socket_connected error: spp_socket error:all clients are connected` y no conecta ninguna app (tampoco Torque) hasta que el programa se reinicia. Onix abría hasta 3 sockets por vuelta y reintentaba cada 5 a 30 s.
  - Un solo socket por vuelta (`maxSockets = 1`); tras una falla, la vuelta siguiente prueba la otra manera (`ObdRecovery.nextWay`).
  - Esperas de 15 s, 30 s, 1, 2, 5 y después 10 minutos (antes 5, 10, 20 y 30 s). El contacto y la pantalla prendida siguen reintentando enseguida.
- **Causa de fondo, fuera de la app:** el lector ELM327 atiende a un solo equipo. Con el celular del autor teniéndolo tomado (Torque en segundo plano), ni Onix ni Torque de la radio enlazaban; con el Bluetooth del celular apagado, los dos conectaron al primer intento (Onix: `connect` 22:57:50.430, `spp Connected` 22:57:50.432).

### Changed
- **Estado del lector cuando no conecta:** "sin conexión (lector apagado o en uso por otro equipo); reintenta en …", con la espera en segundos o minutos (`ObdRecovery.waitText`). El motivo técnico sigue en "Últimos eventos".

### Tests
- `ObdConnectTest` (+2, 8): con un socket por vuelta, una manera trabada no abre otra; una manera que no llega a abrir no gasta el cupo.
- `ObdRecoveryTest` (+2, 13): esperas nuevas, `nextWay` y `waitText`.
- Suite en la PC: 322 ejecutados, 321 exitosos, 0 fallidos, 1 omitido. Lint completo del código de Onix: 0 errores, 140 avisos, 0 problemas de compatibilidad con Android 7.1 (`NewApi`).

## [v0.81.13] - 2026-11-11

Auditoría de la conexión con el lector OBD2 (pedido del autor: Torque conecta casi siempre, en el celular y en la misma radio; Onix falla seguido). Sin registros nuevos del auto: se quitaron las diferencias encontradas en el código con una app estándar. Hipótesis, sin confirmar en el auto.

### Changed
- **El motor de CarLink ignora al lector OBD2 (`carlink-core`: `SystemEventReceiver.onPhoneLink`, `PhoneClassifier.isDiagnosticReader`; `vendor/ONIX_CHANGES.md`):** con cada `ACL_CONNECTED` el motor clasificaba al equipo como posible teléfono; al lector podía mandarle una consulta SDP (`fetchUuidsWithSdp`) o traer Inicio al frente, justo mientras Onix abría su único canal RFCOMM. El OBD2 figuraba como "Funciona" el 28/10 (HU-010), el motor entró el 03/11 (v0.56.0) y los arreglos de reconexión empezaron el 04/11 (v0.73.0).
- **El auto que no contesta ya no corta el Bluetooth (`Obd.kt`, `ObdWatch`):** a las 5 lecturas sin respuesta del auto se cerraba el socket y se reconectaba todo; ahora se manda `ATZ` y el arranque por la misma conexión, y se pregunta cada 3 s mientras el auto no conteste. Una lectura vacía (ni el voltaje del lector) cerraba la conexión; ahora hacen falta `MAX_SILENT_POLLS` (3) seguidas (`ObdWatch.silent`, `mustReconnect`).
- **Los avisos ya no cortan una conexión sana (`ObdRecovery.settlesAfterWake`):** el aviso de contacto puesto (`ACTION_MCU_STATE_CHANGE`, estado 1) y el corte `ACL_DISCONNECTED` cerraban la conexión activa; un aviso atrasado, de un intento anterior, podía cerrar una recién abierta. Ahora el contacto solo dispara la espera de 15 s si venía de contacto apagado o no hay conexión; se quitó `shouldDropActiveLink`.
- **Reintentos con paciencia (`ObdRecovery.retryDelayMs`):** 5, 10, 20 y 30 s (antes 3 s las primeras tres fallas y 10 s después).
- **La manera de conectar se recuerda entre arranques (`ObdRecovery.savedWay`, preferencia `obd/manera`).** Sigue empezando por el canal 1 directo, la única que conectó en los informes del auto (v0.81.0).

### Removed
- **Reinicio automático del Bluetooth de la radio tras 3 fallas (`restartBluetooth`, `ObdRecovery.cycleBluetooth`, `shouldRestartBluetooth`):** desconectaba también el teléfono y volvía a disparar las reacciones del motor de CarLink. Torque conecta en la misma radio sin hacerlo. El permiso `BLUETOOTH_ADMIN` queda: se usa para prender el Bluetooth si está apagado y para cortar una búsqueda de equipos.

### Added
- **Diagnóstico → "Inicio según la radio" (`HomeDefault.radioLauncher`, `describeRadio`):** lee `persist.nwd.launcher.default`, la propiedad con la que el firmware NWD decide a qué launcher proteger (dato de la guía que aportó el autor). Solo lectura; entra en la Prueba completa.

### Tests
- `ObdRecoveryTest` (11): salen los 3 del reinicio del Bluetooth y el de `shouldDropActiveLink`; entran reintentos espaciados, `settlesAfterWake`, `savedWay` y que el motor ignore al lector (la misma lista de nombres que `ObdParse.looksLikeObd`).
- `ObdParseTest` (+1, 12): una lectura vacía no corta la conexión.
- `SettingsAndThemeTest` (+1, 9): `describeRadio`.
- Suite en la PC: 318 ejecutados, 317 exitosos, 0 fallidos, 1 omitido. Lint completo del código de Onix: 0 errores, 140 avisos, 0 problemas de compatibilidad con Android 7.1 (`NewApi`).

## [v0.81.12] - 2026-11-11

### Changed
- **El OBD2 y los viajes viven con el proceso (`CarEngine` en `Obd.kt`, `LauncherApp.kt`; AUD-082, ADR-0018):** el bucle del lector, su receptor de avisos (contacto, pantalla, Bluetooth) y el `CarCare.tick` de cada segundo salen de la composición de la Activity y pasan a un `CoroutineScope` de proceso. `rememberObd` solo pide el permiso, arranca el motor (`CarEngine.startObd`) y lee `ObdState`; `StartOnce` evita un segundo arranque si la pantalla se recrea. La lógica del bucle es la misma, movida. Sin servicio en primer plano (no hace falta mientras Onix sea el launcher). Sin prueba en el auto.
- **El actualizador compara también la compilación (`Updates.hasUpdate` y `Updates.buildNumber` en `SettingsScreen.kt`):** antes solo comparaba la versión, así que una versión corregida y vuelta a publicar con el mismo número no se ofrecía. Ahora, con la misma versión, hay actualización si el `build-N` publicado es mayor que el `versionCode` instalado. Una versión menor nunca se ofrece.

### Tests
- `SettingsAndThemeTest` (+1, 8): versión mayor, misma versión con compilación mayor, igual o menor, versión menor y etiqueta sin número.
- `ObdRecoveryTest` (+1, 12): `StartOnce` deja pasar un solo arranque y reintenta si el arranque falló.
- Suite en la PC: 317 ejecutados, 316 exitosos, 0 fallidos, 1 omitido. Lint completo del código de Onix: 0 errores, 140 avisos, 0 problemas de compatibilidad con Android 7.1 (`NewApi`).

## [v0.81.11] - 2026-11-09

### Changed
- **Diagnóstico dentro de Ajustes (`SettingsScreen.kt`, `Diagnostics.kt`, `LauncherApp.kt`):** `DiagnosticsOverlay` pasa a ser `DiagnosticsPanel`, la quinta categoría de Ajustes (`SettingsCategory.DIAG`); el ícono Diagnóstico del dock abre Ajustes en esa categoría. Solo lecturas, en cuatro grupos (Equipo y rendimiento, Entorno, Auto, Medios y red), con la Prueba completa arriba. Antes la pantalla mezclaba lecturas con controles y la fila de la Prueba completa quedaba cortada fuera del panel (captura `9_diagnostico`).
- **Ajustes en cinco categorías (`SettingsScreen.kt`):** nueva **Auto y conexiones** (estado del lector OBD2 con **Reintentar**, que manda el mismo aviso que el contacto; luces del tablero; destinos desde el celular). **Sonido y avisos** usa las mismas filas que el resto (antes, los paneles chicos de Diagnóstico). Brillo, luces, modo liviano, sonido de inicio, avisos y "Usar como inicio" quedan solo en Ajustes.

### Fixed
- **"Usar como inicio" no avisaba si Onix quedaba elegido (`HomeDefault` en `SettingsScreen.kt`):** el botón prendía `HomeAlias` y abría `ACTION_HOME_SETTINGS`; si la radio no trae esa pantalla, `startSafe` lo ignoraba y no pasaba nada. Ahora se pide el Inicio (`ACTION_MAIN` + `CATEGORY_HOME`), que hace aparecer la ventana de Android para elegir, y la fila **Pantalla de inicio** muestra lo que contesta Android (`resolveActivity`): Onix, falta elegir u otro paquete. El dato también sale en Diagnóstico y en la Prueba completa. Hipótesis, sin confirmar en la radio; si el firmware fuerza su launcher, el paquete que figure lo va a mostrar.

### Tests
- `SettingsAndThemeTest` (+2, 7): las cinco categorías y `showsDiagnostics`; estados de `HomeDefault`.
- `SpotifyTokenTest`: ya no depende de que la compilación venga sin Client ID (con `secrets.properties` cargado fallaban 2 tests). Suite en la PC: 315 tests, 0 fallas, 1 omitido.
- `ScreenshotTest` (+1): capturas `22_ajustes_sonido`, `23_ajustes_auto` y `24_ajustes_sistema`; `9_diagnostico` ahora es la categoría de Ajustes.

## [v0.81.10] - 2026-11-09

### Fixed
- **OBD2: el reinicio automático del Bluetooth no corría nunca (`Obd.kt`):** desde la v0.79.18, el aviso `ACL_DISCONNECTED` del lector subía `ObdRecovery.kick`, y el bucle pone en cero `failures` y `restarts` con cada `kick`. Cerrar un intento fallido también desconecta el lector, así que la cuenta nunca llegaba a `FAILURES_BEFORE_RESTART` y el reintento salía sin esperar. Nueva regla pura `ObdRecovery.restartsRetries`: solo el contacto (puesto o sacado) y la pantalla prendida reinician la cuenta; el corte del lector sigue cerrando la conexión abierta. Hipótesis sobre el efecto en la radio, sin confirmar en el auto.

### Changed
- **Compilación en la PC con las claves de la CI (`app/build.gradle.kts`):** `TOMTOM_API_KEY`, `NTFY_TOPIC`, `SPOTIFY_CLIENT_ID` y `UPDATER_READ_TOKEN` se leen de la variable de entorno (CI, GitHub Secrets) y, si no está, de `secrets.properties` en la raíz (en `.gitignore`). La identidad de CarPlay se toma de `carplay-auth/offline-mfi/` si existe. Antes el APK compilado en la PC salía con todas las claves vacías y sin CarPlay.

### Tests
- `ObdRecoveryTest` (+1, 11): el corte del lector no reinicia la cuenta de fallas; el contacto y la pantalla sí. Suite en la PC: 312 tests, 0 fallas.

## [v0.81.9] - 2026-11-09

### Removed
- **Efectos artificiales de faros de noche (`HomeScreen.kt`):** Remoción completa de `HeadlightBeams` y `HeadlightFlares`. El modo noche mantiene un aspecto 100% fotográfico y auténtico utilizando exclusivamente la textura base `onix_glow` sobre el vehículo y la iluminación natural de la fotografía de fondo.
- **Fundas y ploteos experimentales de carrocería en fechas patrias (`Festive.kt`):** Se eliminaron las fundas de capot y accesorios experimentales, restaurando la clásica y sobria escarapela argentina en el capot.

## [v0.81.8] - 2026-11-09

### Changed
- **Faros sobre el asfalto más sutiles (`HomeScreen.kt`):** Se redujo la intensidad de la proyección en el pavimento en un 55% para una integración fotográfica suave sin sobreexposición.
- **Funda patria completa en capot y techo con cubre-espejos (`Festive.kt`):** Diseño fiel a las fundas de tela reales para autos con la Bandera Argentina: franjas longitudinales celeste, blanca y celeste cubriendo todo el capot con el Sol de Mayo en el centro, extensión completa sobre el techo sin estrellas arriba, cubre-espejos retrovisores en celeste y blanco, y laterales limpios.

## [v0.81.7] - 2026-11-09

### Changed
- **Faros e iluminación orgánica realista sobre el asfalto (`HomeScreen.kt`):** Se eliminaron los polígonos vectoriales duros; la proyección sobre el suelo ahora utiliza gradientes radiales elípticos difusos en perspectiva, simulando la dispersión suave de luz real sobre el asfalto húmedo.
- **Ploteo integral del auto con la Bandera Argentina (`Festive.kt`):** La carrocería completa del Onix se viste con los colores patrios (capó en tres franjas con el Sol de Mayo dorado central en 3D, techo y montantes en celeste y blanco, y laterales/puertas/faldones cubiertos).
- **Luces navideñas dinámicas y corona en la parrilla (`Festive.kt`):**
  - Corona de pino en la parrilla frontal con acículas 3D, bayas rojas, moño de terciopelo y micro-luces LED cálidas.
  - Guirnaldas con bombillas C9 facetadas en forma de lágrima, casquillos verdes al cable y filamentos incandescentes.
  - Dinámica festiva viva que combina onda corrediza (chaser wave), micro-centelleo (sparkle) de alta frecuencia y alto contraste con reflejos sobre la pintura.

## [v0.81.6] - 2026-11-09

### Changed
- **Faros delanteros con proyección frontal realista (`HomeScreen.kt`):** `HeadlightBeams` ahora proyecta los haces hacia adelante-izquierda siguiendo el ángulo de marcha del auto sobre la calzada, con gradientes longitudinales y manchas elípticas de luz sobre el asfalto. `HeadlightFlares` calibra destellos y halos en perspectiva.
- **Ploteo deportivo patrio y escarapela ampliada (`Festive.kt`):** Ploteo en doble franja celeste-blanca sobre capó y techo en perspectiva 3D, y escarapela en parrilla ampliada un 60% con Sol de Mayo central.
- **Calendario festivo extendido (`Festive.kt`):** Navidad comienza el 8 de diciembre (armado del arbolito); Año Nuevo abarca del 27 de diciembre al 2 de enero; Semana de Mayo del 18 al 25 de mayo; Semana de la Independencia del 4 al 9 de julio; Halloween la última semana completa de octubre.
- **Menú de Ajustes estilo Glass con opacidad configurable (`SettingsScreen.kt`, `Ui.kt`):** Panel con borde brillante de cristal y selector de opacidad en Apariencia (**Vidriado 68%**, **Equilibrado 82%**, **Sólido 96%**).

## [v0.81.5] - 2026-11-09

### Fixed
- **Alineación exacta del faro izquierdo (`HomeScreen.kt`):** Se ajustaron las coordenadas del flare y halo óptico (`HeadlightFlares`) de la óptica izquierda de `(0.123w, 0.523h)` a `(0.052w, 0.453h)` acorde a la posición real de la lámpara en `onix_glow.png`. El haz proyectado sobre el asfalto (`HeadlightBeams`) ahora nace exactamente en `(400, 298) dp`.

### Added
- **Letra en vivo compacta al pie en Spotify (`NavMusicScreen.kt`):** Nueva barra `MiniLyricsBar` al pie de las pestañas Listas, Recientes y Buscar; reproduce en tiempo real la estrofa sincronizada actual sin tener que cambiar a la pestaña Letra, y permite saltar a la vista completa con un toque.

## [v0.81.4] - 2026-11-09

### Changed
- **Temas festivos adaptativos al ambiente (`Festive.kt`, `HomeScreen.kt`):** `FestiveLayer` y `CarDecor` ahora reciben el parámetro `ambient` para modular efectos según la luz ambiental y lluvia:
  - *Año Nuevo (Fuegos artificiales)*: resplandor completo y chispas con estela brillante de noche/atardecer; atenuación sutil de día para evitar sobreexposición en cielo claro; difuminado suave con lluvia.
  - *Navidad (Luces y nieve)*: tiras de luces en techo y carrocería con resplandor vivo en penumbra/noche y brillo contenido de día; nieve en techo modulada por el tinte ambiental de la carrocería; densidad de copos de nieve moderada si llueve para no saturar con `RainLayer`.
  - *Fechas Patrias (Cielo y escarapela)*: franja celeste y blanca con Sol de Mayo radiante de día, transición a tonos ámbar al atardecer y halo azul medianoche sutil de noche; escarapela sombreada con el tinte del auto.
  - *Halloween*: niebla sobre asfalto adaptada a las condiciones de luz y lluvia.

## [v0.81.3] - 2026-11-09

### Changed
- **Temas festivos realistas (`Festive.kt`):** `CarDecor` deja de dibujar adornos de caricatura sobre la foto del auto (sombrero de bruja, calabazas, gorro de fiesta, moño, banderines, corazones, flores y bandera en el techo); solo quedan nieve y tiras de luces en Navidad y una escarapela chica en fechas patrias. `FestiveLayer` pasa a efectos de ambiente: copos con profundidad, fuegos artificiales con resplandor y chispas con estela que caen, niebla baja en Halloween, cielo celeste y blanco con Sol de Mayo tenue en fechas patrias (`patriaSky`), papelitos que giran y corazones/pétalos más sutiles.
- **Botón Radio / Spotify más grande (`SwitchChip` en `HomeScreen.kt`):** padding 12×7 dp, ícono de 18 dp, texto de 14 sp y borde con el color de acento, para tocarlo con el auto en movimiento.

### Removed
- Funciones de dibujo sin uso tras el cambio: `bow`, `flag`, `witchHat`, `pumpkin`, `shinyHeart`, `bunting`, `partyHat` y `flower`.

## [v0.81.2] - 2026-11-08

### Added
- **Faros del auto realistas con destello y proyección (`HomeScreen.kt`):** `HeadlightFlares` superpone a cada óptica un núcleo blanco incandescente, halo difuso cálido que rompe hacia afuera del vidrio y destello anamórfico horizontal. `HeadlightBeams` proyecta dos haces cónicos en perspectiva iluminando el asfalto tanto de día como de noche (eliminada la restricción `!ambient.isNight`).
- **Indicadores sutiles de conexión en la franja inferior (`VehicleStrip` en `HomeScreen.kt`, `Drawing.kt`):** ícono reactivo de señal Wi-Fi / hotspot (`iconWifi`) y punto indicador de lectura de motor OBD2 junto a la insignia ONIX JOY.

## [v0.81.1] - 2026-11-08

### Fixed
- **Sincronización del GPS y seguimiento de mapa sin lag (`Location.kt`, `Map3D.kt`, `MapNav.kt`):** intervalo de solicitud de GPS reducido a `0L` (sin demora artificial de 1000 ms), animación de cámara de navegación agilizada de 900 ms a 350 ms para no retrasar la vista del conductor y umbral de rotación a baja velocidad fijado en `> 0.8 m/s`.
- **Botones de destino inteligentes al llegar (`MapNav.kt`, `SavedPlaces`):** verificación de radio (`isNear` <= 100 m); al llegar a Casa o Trabajo se ocultan los botones de "Guardar como Casa" / "Guardar como Trabajo" y, si ambos ya están configurados, se muestra exclusivamente "Guardar como Favorito".

## [v0.81.0] - 2026-11-08

A partir de la elección del usuario para actualizaciones en la app y de los informes de Prueba completa de la `v0.79.20` (`build 164`, `19-02` y `19-04`).

### Added
- **Actualizador integrado en la app (`Updates` en `SettingsScreen.kt`):** en **Ajustes → Sistema**, fila **Actualizaciones** para pegar una sola vez un token de lectura de GitHub (`SharedPreferences("actualizaciones")`), consultar `/releases/latest`, descargar `onix-launcher.apk`, verificar que esté firmado con el mismo certificado que la app instalada (`GET_SIGNATURES`) y abrir el instalador de Android con un toque mediante `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
- **Botones Anterior (`⏮`) y Siguiente (`⏭`) en el reproductor minimizado (`NavMusicScreen.kt`):** cuando se contrae el panel de música para ver el mapa a pantalla completa (`984 dp`), la píldora superior incluye ahora `⏮`, `▶/❚❚` y `⏭` (cambia de tema en Spotify o de emisora en la radio).

### Fixed
- **Reproducción de temas desde Recientes y Buscar en Spotify (`Spotify.kt`):** `SpotifyItem` conserva el `contextUri` del álbum en cada tema y `SpotifyApi.playBody` envía `{"context_uri":"spotify:album:...","offset":{"uri":"spotify:track:..."}}` (el mismo formato con contexto que sí funcionaba en las listas), además de marcar Spotify como última fuente y despertar la sesión local si sigue en pausa a los `600 ms`.
- **Spotify al encender y al volver el Wi-Fi (`Media.kt`):** en el informe `19-02` la radio estuvo sin Wi-Fi los primeros 69 s tras el contacto (`18:57:33` → `18:58:42 · Red: volvió`); `LastAudio.resumeRadioAfterNetwork` retoma ahora también Spotify cuando vuelve la red y no está sonando, `spotifyPlay` envía siempre el broadcast `KEYCODE_MEDIA_PLAY` además de `transportControls.play()`, y `IgnitionAudio.kickSpotify` repite `spotifyPlay` a los `1500 ms` y `3200 ms` antes de volver al launcher.
- **OBD2 colgado al coincidir con el reinicio Bluetooth del estéreo NWD (`Obd.kt`):** los informes mostraron que el estéreo reinicia su chip Bluetooth ~12 s después de `ACTION_MCU_STATE_CHANGE` `estado=1` (`18:57:45 rw.zlink.bt.name=Chevrolet`), colgando el `write("ATZ")` que se abría a `+0 s`, y que este lector ELM327 conecta siempre por la manera 3 (`createRfcommSocket(1)`, índice 2). `ObdRecovery` arranca con `DEFAULT_PREFERRED_WAY = 2`, espera `WAKE_SETTLE_MS = 15_000L` tras el contacto antes de abrir sockets RFCOMM, pausa la reconexión con contacto apagado (`estado=0`), deja `1,5 s` entre maneras fallidas y cierra también el `ParcelFileDescriptor` (`mPfd`) en `closeBluetoothSocket`.

## [v0.80.0] - 2026-11-08

### Added
- **Menú de Ajustes por categorías (`SettingsScreen.kt`):** nueva pantalla propia con tres secciones (**Apariencia**, **Sonido y avisos**, **Sistema**) accesible desde el botón **Ajustes** del dock (que ya no sale a los Ajustes de Android, cumpliendo la regla de oro 2), desde el cajón de **Apps** y desde **Diagnóstico**.
- **Tema Oscuro, Claro o Automático y colores de acento (`Ui.kt` / `ThemePrefs`):** paleta reactiva en toda la interfaz con elección directa entre **Oscuro**, **Claro** y **Automático** (usa modo claro de día con faros apagados y pasa a oscuro apenas se prenden las luces del auto por el cable ILL o al atardecer/noche), más 6 colores de acento (**Azul**, **Ámbar**, **Verde**, **Rojo**, **Violeta** y **Celeste**).
- **Registro de fallos (`CrashLog`):** captura cualquier cierre inesperado en `OnixApp` y lo muestra en **Ajustes → Sistema** con opción de limpiarlo sin necesidad de conectar una PC.

## [v0.79.20] - 2026-11-08

A partir de las dos Pruebas completas hechas en el auto con la v0.79.19 (`build 163`, informes `13-27` y `13-28`): Spotify seguía mudo al encender (`sintonizador FM sin usar`, `sonando=false`, sin `sys.nwd.valid.play.pid`) y el OBD2 fallaba en las 3 maneras con `prueba de escritura: no respondió en 8 s (trabado)` justo después de que `ExternalBluetoothProbe` abriera y cerrara `/dev/socket/zj_bt_socket` a las `13:24:32`.

### Fixed
- **Spotify mudo al dar contacto:** `NwdTuner.bounceToAndroid` ahora ejecuta el ciclo completo que hace el usuario al tocar **Antena** (`select` + `wake` + `TX_SET_FREQUENCY`) para que `com.nwd.radio.service` despierte el chip de audio, y a los `1800 ms` devuelve la fuente a `SOURCE_ANDROID` (`release`). `IgnitionAudio.restore` espera `2500 ms` antes de `LastAudio.resume` para que el `play` no choque con el cambio de fuente, y `kickSpotify` (a los `5000 ms`) ya no aborta cuando `sonando=false`: registra siempre el estado en el informe, pasa Spotify al frente si sigue en pausa o sin `sys.nwd.valid.play.pid`, le manda `spotifyPlay` a los `1500 ms` con Spotify ya en primer plano y vuelve a Onix a los `4000 ms`.
- **OBD2 trabado en la prueba de escritura:** `ExternalBluetoothProbe.deviceSources` ya no abre ni cierra `/dev/socket/zj_bt_socket` al iniciar la app (solo se usa proyección por USB y ese `shutdown` afectaba al demonio Bluetooth de la radio `k2001-nwd`); `closeBluetoothSocket` ejecuta `shutdownInput()` y `shutdownOutput()` sobre el `LocalSocket` interno (`mSocket`) de `BluetoothSocket` antes de `socket.close()` para despertar en el kernel cualquier `write()` bloqueado; `ElmLink.close()` es idempotente y el registro de `connectVerified` incluye el paso exacto (`ElmLink.step`).

## [v0.79.19] - 2026-11-08

### Added
- **Spotify con portadas, búsqueda en vivo y letras sincronizadas:** `SpotifyItem` incluye `imageUrl` y `SpotifyCovers` guarda en caché las tapas de listas, recientes y búsquedas; el buscador ejecuta las consultas en vivo tras 350 ms sin apretar Enter, ofrece chips con las últimas búsquedas y sugiere los temas escuchados recientemente en la cuenta; nueva pestaña **Letra** (`Lyrics` / `LRCLIB`) sincronizada con el segundo actual del tema.
- **Reproductor minimizable en Navegación y Música:** `NavMusicScreen` permite contraer el panel de música (`❯`) para estirar el mapa a pantalla completa (`984 dp`) dejando una píldora de control rápido (`▶/❚❚` y `❮ Música`) en la barra superior.

### Fixed
- **Pulido visual en Inicio, Navegación y Radio FM:** escala del velocímetro cada `40 km/h` (`EnginePanel.kt`) para que no se encimen los números superiores; cápsula oscura para el nombre de la emisora sobre la aguja y ancho de presintonías en `196 dp` (`RadiosScreen.kt`); chips de categorías del mapa en `140 dp` (`MapNav.kt`) para que entren 4 enteros sin cortarse al borde.

## [v0.79.18] - 2026-11-08

### Fixed
- **Reconexión confiable del lector OBD2 ELM327 al dar contacto:** `ObdRecovery.connectVerified` exige que el lector realmente conteste al menos un byte (`ElmLink.answered` tras enviar `ATZ` con 5 s de margen) para descartar canales Bluetooth RFCOMM mudos y pasar de inmediato a la siguiente manera de conectar; además recuerda la última manera que funcionó (`preferredWay` / `wayOrder`), evita el doble reinicio `ATZ` en `initElm`, y cierra en el acto cualquier conexión vieja cuando se pone contacto o se corta el enlace Bluetooth del lector (`shouldDropActiveLink`).

## [v0.79.17] - 2026-11-08

### Fixed
- **Audio mudo al dar contacto:** `NwdTuner.bounceToAndroid` conmuta la fuente del MCU a `SOURCE_RADIO` (`4`) y a los 500 ms vuelve a `SOURCE_ANDROID` (`-128`) sin despertar el chip de FM; `IgnitionAudio.restore` espera 600 ms para llamar a `LastAudio.resume`, automatizando el paso por modo antena que destraba el amplificador de la radio NWD.
- **Demora al iniciar Android Auto por cable USB:** `SessionManager.onUsbPhone` registra el teléfono conectado por USB en `KnownPhones` (`usb:<nombre>`), de modo que `EnginePrewarmPolicy.shouldPrewarm` precaliente el proceso `:aa` al arrancar también en estéreos con módulo Bluetooth separado.
- **TuneIn detenido tras corte de red en Android Auto / CarPlay:** `LastAudio.resumeRadioAfterNetwork` envía `KeyEvent.KEYCODE_MEDIA_PLAY` mediante `mediaKey` cuando vuelve la red con una sesión de proyección activa (`Projection.sessionActive`).

## [v0.79.16] - 2026-11-07

Informes del auto del 07/11 (22:29 y 22:30, v0.79.15): no servían para ver el encendido ni el sonido. "Últimos eventos" guarda 80 líneas y los errores de red (73 `UnknownHostException`, la radio sin internet durante más de una hora) sacaron el encendido de las 21:27; además la red caída puede ser lo que dejaba a Spotify sin listas ni sonido.

### Added
- `AppLog.recentKey()`: las líneas "Contacto", "Al encender" y "Red al encender" se guardan también en un registro aparte (40 líneas) que no se llena con errores repetidos. La Prueba completa lo muestra como "Registro del encendido (guardado aparte)".
- `IgnitionNet`: a los 0, 10, 30 y 60 s de poner contacto se anota el estado de la red ("Red al encender (+10 s): WIFI conectada" o "sin conexión"), para separar la falta de internet de una falla del audio.
- Tests: `IgnitionLogTest` (3).

## [v0.79.15] - 2026-11-07

CarPlay por cable se cortaba a los 3 a 8 segundos de conectar (informes del 06/11 13:32 y del 07/11 01:29: `the USB link restarted`, `Android could not queue USBMUX read request`), mientras que la app DiPlay sola andaba sin cortes con el mismo iPhone y cable.

### Changed
- La capa USB de CarPlay (`IphoneUsbHost`, `NcmUsbBridge`, `UsbConfigurationSelection`) vuelve a ser la de DiPlay 0.2.22 original (`DiPlay-main.zip`) en lugar de la que CarLink reescribió (lectura USB permanente en cola, reuso de la configuración activa). Se conservan el paquete vacío de la v0.79.3 (el "Confiar" del iPhone) y `hasPermission`; se pierde el registro de tiempos del enlace NCM (`onLinkEvent`). Detalle en `vendor/ONIX_CHANGES.md`. Hipótesis a confirmar en el auto: si sigue cortando, la causa no está en esa capa.

## [v0.79.14] - 2026-11-06

Spotify mudo al encender, con la v0.79.13 instalada (informes 13:32 y 13:33). El salto de 1,5 s dejó el audio válido igual al que suena (14810 → 14050) y aun así no sonó nada, ni el saludo de inicio: el silencio es de todo el audio de Android. Si se abre Spotify a mano suena y sigue sonando al volver a Onix.

### Changed
- `IgnitionAudio.FOREGROUND_MS` pasa de 1,5 s a 4 s: Spotify queda al frente un rato real, como cuando se abre a mano.

### Added
- "Al encender" registra `arm_sound`, `key_arm_fm_audio_mode` y `mcu_current_source`; la Prueba completa suma los ajustes del sistema con `arm_`, `sound`, `audio`, `mute` y `mcu_current_source`. La radio cambió `arm_sound` 1 → 0 al volver de la antena a Android (13:33:34): hipótesis sin confirmar, falta comparar el valor en silencio y con sonido.

## [v0.79.13] - 2026-11-06

Spotify mudo al encender. Con la v0.79.12 instalada el "pausa y play" (v0.79.11) corrió y Spotify siguió mudo hasta abrir su app. Los informes muestran `sys.nwd.valid.play.pid` distinto de `sys.nwd.playing.pids` con Spotify mudo (7155 contra 13442 y 11040) e iguales con Spotify sonando (14810, 13271). El paso por el FM a mano (08:54 y 10:35) tampoco lo arreglaba.

### Fixed
- **La radio no tenía a Spotify como proceso de audio válido al encender** (Spotify sobrevive al apagado por HU-014, `BackgroundManager.ini`). `IgnitionAudio.kickSpotify` ahora lee `sys.nwd.valid.play.pid` y `sys.nwd.playing.pids`; si suena algo y el pid válido no está entre los que suenan (`IgnitionAudio.needsForeground`), lanza Spotify (`getLaunchIntentForPackage`) y a los 1,5 s trae Onix de vuelta (`MainActivity`, `REORDER_TO_FRONT`). Se quitó el "pausa y play". Registra el audio válido antes y después. Salida a otra app anotada en `README.md` ("Salidas a otras apps"). Hipótesis a confirmar en el auto (R-96).

### Tests
- `IgnitionAudioTest`: `needsForeground` con los valores de los cuatro informes y los casos vacíos, y `shouldKickSpotify`.

## [v0.79.12] - 2026-11-06

A partir de la Prueba completa del auto con la v0.79.10 (`onix_informe_2026-11-06_11-17`) y de lo que contó el autor: el lector OBD2 anda con el auto en marcha, pero a veces no conecta.

### Fixed
- **OBD2: el socket "conectaba" pero la primera escritura (ATZ) se trababa.** En los 12 intentos del informe el paso fue "escribiendo ATZ", también después de reiniciar el Bluetooth de la radio. `connect()` devolvía la primera manera que no tiraba error y nunca probaba la 2 (insegura) ni la 3 (canal 1 directo). Nuevo `ObdRecovery.connectVerified`: cada manera se verifica con un `ATZ` de prueba con tope de 8 s (`PROBE_MS`); la que no deja escribir se cierra y se pasa a la siguiente; si ninguna sirve, tira el último error. `CONNECT_GUARD_MS` pasa a 3 × (15 s + 8 s) + 5 s. Hipótesis: alguna de las otras maneras deja escribir en esta radio; falta probar en el auto.

### Added
- Registro de qué manera de conectar dejó escribir o por qué falló cada una.

### Tests
- `ObdConnectTest` (4, nuevo): la primera manera que deja escribir se usa; una que se traba se cierra y se pasa a la siguiente; una que no abre se salta; si ninguna sirve, el último error.

## [v0.79.11] - 2026-11-06

Spotify mudo al encender. Observado en el auto (v0.79.10): con Spotify mudo, al abrir la app de Spotify empezó a sonar. El informe de la v0.79.9 ya mostraba la fuente de la radio en 0 y "sonando=true".

### Fixed
- **Spotify figura "sonando" pero no saca audio después de poner contacto.** El `play` de `playOtherApp` sobre una sesión que ya dice "sonando" no hace nada. `IgnitionAudio.kickSpotify`: a los 3 s de devolver el audio a Android, si Spotify era lo último y su sesión figura `STATE_PLAYING`, `pause()` y 0,8 s después `play()` por los controles de la sesión (sin abrirlo). Hipótesis (lo que hace abrir la app, reiniciar el reproductor, no se pudo confirmar): falta probar en el auto.

### Tests
- `IgnitionAudioTest` +1 (`shouldKickSpotify`).

## [v0.79.10] - 2026-11-06

A partir de la Prueba completa del auto con la v0.79.9 (`onix_informe_2026-11-06_10-37`). Qué mostró: el arreglo del cierre del lector anduvo (fallas contadas, reinicio del Bluetooth a la tercera) pero el lector sigue sin contestar —el arranque se traba aun después de reiniciar el Bluetooth, lo que apunta al lector—; y el audio de Spotify siguió mudo con la fuente de la radio ya en 0.

### Fixed
- **El contacto no se detectaba si la radio manda `extra_mcu_state` como byte o texto.** Lo leían con `getIntExtra` (devuelve -1 si el tipo no es Int) los tres receptores: `IgnitionAudio` (Media.kt), el reintento del OBD2 (Obd.kt) y el cierre del viaje (CarCare.kt). Se ve en el informe: a las 10:36:18 llegó `extra_mcu_state=1` y no apareció "Al encender". Nuevo `ObdRecovery.mcuStateOf` / `mcuState(intent)` que lee por contenido. Hipótesis, no confirmada en el auto.

### Added
- **Registro del contacto** ("Contacto: la radio avisó estado=N") y **del paso en que se traba el lector** (`ElmLink.step`: "escribiendo ATZ" / "esperando la respuesta a ATZ"), en los eventos de "trabado".

### Tests
- `McuStateTest` (5, nuevo) y `ElmLinkTest` +2 (el paso dice dónde se bloquea una escritura; queda en "listo" al terminar).

## [v0.79.9] - 2026-11-06

Dos fallas de la Prueba completa hecha en el auto con la v0.79.8 (informe `onix_informe_2026-11-06_08-56`).

### Fixed
- **Spotify mudo al encender con la app recién arrancada.** El aviso de contacto de la radio llega antes de que exista la app, así que `IgnitionAudio.restore` (v0.79.4) no corría y Spotify "sonaba" con la fuente de la radio afuera de Android. Ahora el arranque de la app también la llama (`LauncherApp.kt`), y si no hay nada que devolver se retoma como antes. Causa vista en el informe: figuraba "Al encender: retomo Spotify" y no "devuelvo el audio a Android".
- **OBD2 "conectando…" sin fallas ni reintentos.** El vigía cerraba el socket a los 20 s ("el lector quedó trabado") pero nada más aparecía en los eventos durante más de 2 minutos: el bucle quedaba colgado. `ElmLink.close()` ahora cierra en un hilo aparte (espera como mucho 2 s) y el arranque del lector (`initElm`) corre con un tope duro de 45 s (`ObdRecovery.INIT_GUARD_MS`); un cierre colgado ya no frena el bucle, la falla se cuenta y siguen los reintentos y el reinicio del Bluetooth. Hipótesis (no se pudo ver el cuelgue exacto en la PC): el cierre del socket se cuelga en la pila Bluetooth de la radio.

### Tests
- `ElmLinkTest` +2: `close()` no se cuelga si el cierre del socket se cuelga (RED observado) y sigue cerrando el socket.
- CI (build 151): 281 ejecutados, 281 exitosos, 0 fallidos, cobertura 23,6 %.

## [v0.79.8] - 2026-11-06

Pedido del autor andando: el clima y el lugar siguen al auto.

### Added
- **El clima y el nombre del lugar se piden de nuevo si el auto se alejó más de 3 km** del punto del último pedido (`PlaceRefresh` en `Location.kt`; se revisa cada 30 s con la última posición conocida), además del turno de 15 minutos y de la vuelta de la red. Sin ubicación no cambia nada.

### Tests
- `PlaceRefreshTest` (5, nuevo): otra ciudad, mismo lugar, tramo corto, umbral de 3 km y sin punto anterior.
- CI (build 150): 279 ejecutados, 279 exitosos, 0 fallidos, cobertura 23,6 %.

## [v0.79.7] - 2026-11-06

Resto de la fase 2 de la auditoría (`docs/tasks/fase-2-auditoria.md`), más tres pedidos del autor hechos andando: GPS del mapa, aviso de luces a cualquier velocidad y la casa como ubicación por defecto.

### Fixed
- **Mapa: el punto azul se actualiza cada segundo** (AUD-119, pedido del autor: "mientras me muevo tiene un delay de unos segundos"). Los dos mapas (3D y clásico) pedían la posición como mucho cada 2 s o cada 5 m, mientras el velocímetro y la guía la piden cada 1 s: a 40 km/h el punto saltaba unos 22 m entre actualizaciones. Ahora es cada 1 s y sin mínimo de metros. El límite viejo se había puesto para frenar un pedido de ubicación por red que cargaba los servicios de Google (v0.35.2); eso ya lo resuelve "solo GPS". **Falta probar en el auto (R-89):** si la radio entrega las posiciones más lento que eso, la lentitud es del GPS de la radio.
- **Mapa: avisa cuando no hay GPS** (AUD-120). Hasta la primera posición del GPS el mapa queda en la última conocida (donde quedó estacionado el auto: la casa del autor) y no lo decía; las rutas salían de ahí sin avisar. Ahora un cartel dice "Buscando GPS…" (o "Sin señal de GPS hace N s / min" si se corta en un túnel o cochera), y una ruta calculada sin posición reciente (más de 2 min) agrega "desde la última posición conocida". La posición vieja se sigue usando: casi siempre es la correcta (el auto estaba estacionado).

### Added
- **La Prueba completa mide el GPS** (sección GPS, "Posiciones en vivo (frecuencia)"): hace cuánto llegó la última posición, cada cuántos segundos llegan (medio y máximo), cuánto tardan en llegar desde que el GPS las calcula, precisión, satélites y cuántas posiciones vio. Es FALLA si pasaron más de 10 s sin posiciones o si llegan cada más de 2,5 s. Sirve para saber si la lentitud del mapa es de la radio.

### Security
- **La Prueba completa muestra la dirección Bluetooth del lector OBD2 con solo el último byte** (`XX:XX:XX:XX:XX:EF`, AUD-096). El informe se copia al portapapeles (que en Android 7 puede leer cualquier app) y a Descargas; el nombre del lector sigue a la vista.
- **AUD-092 revisado, sin cambio:** la actividad `AdbClusterActivity` de DiPlay se cierra sola salvo que su ajuste esté prendido y el intent traiga un token que el motor genera en memoria, así que su exposición es mínima. Cerrarla rompería la función de tablero del motor, que la abre por el shell. No se toca `vendor/`.

### Changed
- **Ubicación por defecto: la casa del autor** (AUD-105, decisión del autor: "usá la de mi casa"). Sin GPS, el clima, el mapa, la búsqueda desde el celular y "Mis lugares" usan `-26.7368, -65.2580` ("Calle 9 de Julio 550, Tafí Viejo", la posición que dio TomTom) y el clima dice "Tafí Viejo". Antes eran dos puntos distintos de San Miguel de Tucumán (uno para el clima y el mapa, otro para la búsqueda y "Mis lugares"), a unos 12 km de la casa. Con GPS no cambia nada.
- **Aviso de luces apagadas: ahora a cualquier velocidad y a cualquier hora** (AUD-121, pedido del autor: "acá está prohibido andar sin luces prendidas, aunque vaya a 2 km/h"). Antes avisaba desde 20 km/h de noche y solo desde 60 km/h de día. Ahora avisa andando desde 2 km/h (deja afuera el ruido del GPS con el auto parado, que da 0 o 1), de día y de noche, y a los 5 s en vez de a los 15 (con 15 s, a paso de hombre ni se llegaba). Es el mismo aviso: cartel, pitido y la voz; no repite antes de 10 min; sigue necesitando que la radio informe las luces; con "Silencio" solo sale el cartel. **Falta probar en el auto (R-90).**
- **APK 3,30 MB más chico (de 24,43 a 21,13 MB, −13,5 %)** (AUD-086): `resources.arsc` traía 88 idiomas de las librerías (AppCompat, Material, Media3, los motores…) y la app solo está en castellano. Se dejan castellano e inglés (`androidResources.localeFilters` en `app/build.gradle.kts`); `resources.arsc` pasa de 4,23 a 0,93 MB. Lo único que cambia es que las pantallas de los motores, si el sistema estuviera en otro idioma, salen en inglés. Medido contra el APK de la CI del build 148: la única otra diferencia son los dos archivos de la identidad de CarPlay, que solo agrega la CI. Decisión del autor ("seguí con todo lo que me sugeriste").
- Sin cambios visibles: el bucle de los destinos del celular (ntfy) usa `PhoneShare.streamUrl`, `consume` y `nextWait`, que hacen lo mismo que antes y se pueden probar sin red (AUD-085).

### Tests
- **Resultado de la CI (compilación 149):** 274 ejecutados, 274 exitosos, 0 fallidos; cobertura de los tests de lógica 23,6 % (1995 de 8439 líneas).
- `PhoneShareStreamTest` (6, nuevo, Robolectric): el enlace del canal sigue desde el último mensaje (`since`) y codifica el nombre; solo se entregan los mensajes de los últimos 15 min; un mensaje viejo igual mueve el cursor para no repetirse al reconectar; los eventos que no son mensajes no tocan nada; la hora se mira en cada mensaje (la conexión dura horas; verificado: si se mira una sola vez, el test falla); la espera para reconectar crece hasta un minuto.
- `ObdParseTest` (+1, 11): la dirección Bluetooth sale enmascarada.
- `WeatherParseTest` (6, +1): sin ubicación el lugar y el punto son la casa del autor (Tafí Viejo, `-26.7368, -65.258`); la consulta del clima la usa.
- `SafetyAlertsTest`: 3 tests reescritos con la regla nueva de las luces (avisa a cualquier velocidad desde 2 km/h, de día y de noche; no avisa parado, con 1 km/h de ruido del GPS, con las luces prendidas ni sin señal de la radio; avisa a los 5 s).
- `GpsFixTest` (11, nuevo): cuándo la posición "está viva" (10 s) y es vieja (2 min); qué dice el mapa en cada caso; edad de la última posición; intervalo medio y máximo, retraso de entrega (con el reloj del equipo, no con la hora, que en estas radios puede estar mal) y cantidad; solo se guardan las últimas 30; el texto del informe.

## [v0.79.6] - 2026-11-06

Fase 2 de la auditoría 2026-11-05 (`docs/tasks/fase-2-auditoria.md`), pasos 1 y 3: red de seguridad y tanda chica. Y, a pedido del autor, el Client ID de Spotify desde GitHub Secrets.

### Added
- **El Client ID de Spotify puede venir de un GitHub Secret** (`SPOTIFY_CLIENT_ID`, pedido del autor 2026-11-06: no pegarlo a mano en la radio). Se carga al compilar, igual que la clave de TomTom y el canal de ntfy, y la pantalla de Spotify ya no pide pegar nada: solo "Conectar con Spotify". No es un secreto de verdad (la conexión usa PKCE, sin "client secret", y el Client ID va en el enlace de permiso), pero queda fuera del repositorio. Es opcional: sin el secreto, todo sigue como antes (se pega a mano). Si hay uno cargado en la app y otro pegado a mano, vale el de la app. En la Prueba completa se lee "Client ID cargado=sí (viene en la app)" o "(pegado a mano)". El valor se limpia a letras y números al compilar. Nunca se imprime en la CI.

### Security
- **Spotify: la redirección `onixlauncher://spotify` solo vale si viene del permiso que pidió Onix** (AUD-093). Cualquier app del equipo puede abrir ese enlace. Ahora el pedido de permiso lleva un `state` al azar que Spotify devuelve; sin ese `state` (o con otro, o repetido) la redirección se ignora, sin tocar ni el mensaje de estado. Antes, otra app podía mandar un código ajeno y hacer que Onix intentara canjearlo o mostrara "no se pudo conectar". Un permiso que se haya empezado con la versión anterior y se termine con esta se ignora: hay que volver a tocar Conectar.

### Fixed
- **Radio: una reconexión pendiente ya no arranca el reproductor después de que el servicio se destruye** (AUD-100). Si el sistema o Media3 paraban el servicio sin pasar por la orden de parar, la reconexión o la búsqueda que había quedado pendiente corría igual sobre el reproductor ya liberado. El test la reprodujo antes del arreglo.
- **Logos de radios: un enlace roto ya no puede cerrar la app** (AUD-118). `Net.getBytes` leía el enlace fuera del `try`, así que uno que Java no puede leer (por ejemplo `http://radio:xyz/logo.png`, de un directorio de radios) tiraba una excepción que nadie atrapaba al mostrar esa radio. Ahora devuelve `null`, como dice su documentación. Lo encontró el test nuevo.

### Changed
- Documentación de las decisiones: ADR-0015 (Client ID de Spotify desde un secreto de GitHub) y ADR-0016 (CI de mínimo privilegio y acciones fijadas por commit, de v0.79.5).
- Sin cambios visibles: `ElmLink` (el lector del ELM327) recibe los flujos de entrada y salida en vez del socket, y `SpotifyAuth` hace sus pedidos de tokens por `SpotifyAuth.http` (siempre `Net.request` en la app). Es solo para poder probarlos sin Bluetooth ni red (AUD-085).

### Tests
- **Resultado de la CI (compilación 148):** 255 ejecutados, 255 exitosos, 0 fallidos; cobertura de los tests de lógica 23,1 % (1934 de 8355 líneas).
- `ElmLinkTest` (6, nuevo): el lector sin Bluetooth. Respuesta sin el prompt, restos descartados, tiempo máximo, respuesta tardía que no se mezcla con el comando siguiente, vigía y cierre. Verificado: sin la línea que descarta restos fallan los dos tests que corresponden.
- `SpotifyClientIdTest` (4, nuevo): el Client ID de la app gana sobre el pegado a mano, sin el de la app vale el pegado, sin ninguno no hay, y se ignoran los espacios. Verificado a mano que sin el secreto el campo queda vacío y que con un valor lleno de comillas y símbolos queda solo con letras y números.
- `RadioServiceLifecycleTest` (2, nuevo, Robolectric con el servicio real): una reconexión pendiente no corre después de destruir el servicio, y destruirlo invalida las búsquedas en curso (AUD-100).
- `SpotifyTokenTest` (+4, 14 en total): `state` distinto en cada pedido de permiso, redirección con otro `state`, sin `state` o sin permiso en curso que se ignora sin tocar nada, y `state` de un solo uso (AUD-093).
- `SpotifyTokenTest` (10 al principio, Robolectric): token vigente sin pedidos, renovación, corte de red o error del servidor sin desconectar, 400 que desconecta, redirección y verificador de un solo uso, y dos pedidos a la vez que renuevan una sola vez (AUD-053; verificado: sin `@Synchronized` renueva dos veces).
- `NetTest` (12, nuevo): servidor HTTP local sin internet. Redirecciones (hasta 4), tope de tamaño, errores, enlace inválido, `get`, `request` con cuerpo y encabezados, y que el registro no lleve tokens ni claves.
- `ObdParseTest` (+2): errores del lector y respuestas de otro PID quedan `null`; varias ECU y encabezados.
- Cobertura de los tests de lógica: 20,4 % → 22,3 % (`Spotify.kt` 36 → 72 %, `Obd.kt` 25 → 32 %).

## [v0.79.5] - 2026-11-05

Fase 1 de la auditoría técnica 2026-11-05 (`docs/audits/AUDIT-2026-11-05.md`, `docs/tasks/fase-1-auditoria.md`): cambios de bajo riesgo, cada uno con su test, probados en la PC del autor (215/215).

### Security
- **CI más segura** (auditoría 2026-11-05):
  - `carlink.yml` ya no mete el número de versión de CarLink dentro del script: lo pasa por una variable y lo rechaza si no tiene formato de versión (AUD-073).
  - `build.yml` corre con permiso de solo lectura; solo el trabajo que publica la release puede escribir (AUD-074).
  - Las acciones de terceros con permisos están fijadas por commit (AUD-074).
- **Mapa de R8** (`onix-launcher-mapping.txt.gz`, unos 9 MB comprimido) junto al APK en cada release, para poder leer los errores que lleguen del auto (AUD-088).

### Fixed
- **OBD2: el Bluetooth ya no puede quedar apagado** (AUD-072). Cuando el lector falla 3 veces, Onix apaga y prende el Bluetooth (v0.73.0). Si la pantalla de Onix se cerraba o se recreaba justo entre el apagado y el prendido (por ejemplo, al abrir Android Auto o al cortar el contacto), el Bluetooth de la radio quedaba apagado: sin OBD2, teléfono ni Android Auto inalámbrico. Ahora el reinicio siempre termina. Además, cerrar la pantalla ya no se cuenta como falla del lector ni deja un "se cortó la conexión" falso en Últimos eventos.
- **Mi auto: el historial ya no se puede perder** (AUD-101). Si los viajes, el mantenimiento o los totales por mes guardados no se podían leer (archivo dañado), Mi auto los mostraba vacíos y el viaje siguiente los pisaba para siempre. Ahora se guarda una copia tal cual (`<clave>_respaldo_<fecha>`) antes de seguir, y "Últimos eventos" lo anota. Además, el kilometraje que informa el OBD2 se aplica en el hilo principal, el mismo que cuenta los km del viaje (antes podían pisarse).
- **Prueba completa: dos toques seguidos ya no arrancan dos pruebas a la vez** (AUD-103).
- **Registro sin claves en logcat** (AUD-104): `AppLog.info` tapa `key=`, `token=` y `access_token=` igual que los avisos.
- **OBD2: conexión huérfana** (AUD-080). Si el lector contestaba después del tope de 50 s, esa conexión quedaba abierta y podía trabar los reintentos; ahora se cierra.

### Performance
- **Menos trabajo con el OBD2 conectado** (AUD-075): una lectura del lector con los mismos valores que la anterior (motor en ralentí, auto quieto) ya no hace recomponer toda la pantalla cada 0,7 s. `MediaInfo` no se cambia: cada lectura trae su hora y nunca sería igual a la anterior.

### Tests (medición)
- **La cobertura cuenta lo que ya se probaba** (AUD-083): JaCoCo no medía las clases que cargan los tests de Robolectric (`Spotify.kt`, `PhoneShare.kt` y `NearbyPlaces.kt` daban 0 % con sus tests en verde). Con el plugin `jacoco` aplicado en `app/build.gradle.kts`, con los mismos tests de lógica que la CI: Spotify 0 → 36,2 %, PhoneShare 0 → 30,8 %, NearbyPlaces 0 → 24,1 %, MapNav 3,3 → 18,6 %; **total 16,5 → 20,4 %** (medido en la PC). No se agregó ningún test para subir el número.

### Removed
- Código sin uso (AUD-106): `CircleButton` y `openApp` (`Ui.kt`), `iconGauge` e `iconEngine` (`Drawing.kt`).

### Tests
- `ProjectionTest` (+1): las pantallas de Android Auto y CarPlay que Onix abre por nombre existen en los motores (AUD-081); si una actualización de CarLink las renombra, la CI falla en vez de que "Volver a Android Auto / CarPlay" deje de andar sin aviso.
- `FullReportTest` (+1): la Prueba completa no arranca dos veces.
- `CarCareStorageTest` (4, nuevo, Robolectric): viajes y meses dañados se respaldan antes de pisarlos; lo sano se lee sin respaldo; sin nada guardado, vacío.
- `ObdParseTest` (+1): una lectura igual no cambia el estado (falla sin el arreglo).
- `ObdRecoveryTest` (+3): reinicio del Bluetooth cancelado a la mitad (verificado: falla sin el arreglo), cancelación que no cuenta como falla y conexión tardía que se cierra.

### Changed
- **APK 1,26 MB más chico** (24,4 MB): se quitaron recursos Java que ningún código lee y que R8 no recorta solo: tablas de BouncyCastle `pqc/picnic` (1,21 MB), mensajes `x509/CertPathReviewerMessages*`, fuentes `google/protobuf/*.proto` y `DebugProbesKt.bin` (AUD-071, AUD-113). No cambia nada de la app; `vendor/` no se tocó.

## [v0.79.4] - 2026-11-05

### Fixed
- **Sin sonido al volver a poner contacto** (pedido del autor: a veces, después de apagar y prender el auto, Spotify sigue "sonando" pero no se escucha, y hay que pasar a la radio y volver a Spotify). Desde que la radio ya no cierra Spotify al apagar (HU-014), la radio a veces deja su fuente de audio fuera de Android al prender. Ahora, 3 s después de poner contacto, si lo último que sonaba era Spotify o una radio por internet, Onix le pide a la radio la fuente Android (lo mismo que pasaba al ir y volver de la radio) y retoma lo último. Con el FM por antena o con la música en pausa no toca nada. "Últimos eventos" anota la fuente que tenía la radio.

### Tests
- `IgnitionAudioTest` (1): cuándo se devuelve la fuente a Android.

## [v0.79.3] - 2026-11-05

### Fixed
- **CarPlay por cable: el iPhone no mostraba "Confiar"** (iPhone 17 Pro Max del autor; R-83). El logcat del 05/11 20:01 mostró que el iPhone contestaba todo menos el pedido de confianza (`Pair`), que medía justo **4608 bytes, 9 paquetes USB de 512 enteros**. Cuando un envío USB termina justo en el borde de un paquete hay que mandar un paquete vacío después; el motor no lo hacía y el iPhone se quedaba esperando el resto. Ahora el motor lo manda (cambio en `vendor/`, anotado en `vendor/ONIX_CHANGES.md`). La app DiPlay sola andaba porque usa otro nombre de equipo y su pedido no cae justo en el borde.

### Tests
- `UsbZeroLengthPacketTest` (1): paquete vacío solo cuando el envío termina justo en el borde.

## [v0.79.2] - 2026-11-05

### Fixed
- **OBD2 que queda en "conectando…" para siempre** (pedido del autor: dos viajes sin OBD2 y sin reinicio del Bluetooth; Prueba completa del 05/11 17:12: "conectando…", ninguna falla anotada en 4 minutos). La conexión se quedaba esperando un connect o una escritura del Bluetooth que nunca volvía, así que no se contaba ninguna falla y nunca llegaba el reinicio del Bluetooth. Ahora:
  - conectar tiene un tope propio de 50 s, aparte del de cada intento;
  - un vigía cierra la conexión si ningún comando sale en 20 s;
  - las dos cosas cuentan como falla, así que a la tercera llega el reinicio del Bluetooth como estaba previsto.

### Tests
- `ObdRecoveryTest` (+1): conexión trabada (vigía de 20 s y tope que cierra y avisa).

## [v0.79.1] - 2026-11-05

### Added
- **La Prueba completa dice dónde se corta CarPlay** (pedido del autor: el iPhone 17 Pro Max no mostraba nada y la prueba solo decía "Revisá el cable USB"):
  - "Motor" suma el **motivo exacto** que da el motor al fallar.
  - **Equipos USB:** qué ve la radio enchufado (Apple aparece como `05ac`).
  - Sección nueva **"Motor de Android Auto y CarPlay (últimos pasos)"**: cada paso de CarPlay con la hora (buscar el iPhone, pasarlo a modo CarPlay, confiar, control iAP2, red por USB…), los cambios de estado del motor, sus avisos y los últimos 30 eventos del registro de CarLink.
  - Sección **"Registro del motor de CarPlay (diplay.log)"**: las últimas 80 líneas del registro propio del motor (el mismo que guarda la app DiPlay, que al autor le funcionaba en la radio), sin direcciones ni claves.
  - Un fallo del motor también queda en "Últimos eventos".
- **OBD2: lo que hace el Bluetooth** queda en "Últimos eventos" (una búsqueda de equipos, apagado/prendido, un equipo que se desconecta), para ver por qué el lector se cortaba cada 23 s en la Prueba completa del 05/11 13:12.

### Tests
- `EngineTraceTest` (5): pasos de CarPlay en palabras, estado con el motivo, sin repetidos y hasta 50 líneas, registro de CarPlay sin direcciones, equipos USB.
- `ObdRecoveryTest` (+1): eventos del Bluetooth en palabras.

## [v0.79.0] - 2026-11-05

### Changed
- **Mi auto rehecho** (debatido con el autor; `docs/tasks/mi-auto-v2.md`), con tres pestañas:
  - **Mantenimiento:** cada ítem con "lo hice a los ___ km" (y la fecha), "se hace cada ___ km" y/o "cada ___ meses"; dice "Faltan 3.200 km" o "Pasado por 300 km" con colores (lo primero que llegue, km o fecha) y avisa en Inicio una vez por día. **Hecho** pide a qué km se hizo (y con eso corrige el kilometraje si el auto no lo informa por OBD2). Lista de arranque: aceite y filtro, filtro de aire, de habitáculo y de combustible, alineación y balanceo (con rotación) cada 10.000 km; neumáticos cada 60.000 km; batería y VTV por fecha; pastillas, líquido de frenos, bujías y correa de distribución para completar. Se pueden agregar, editar y borrar. Se quitaron seguro, patente y "service" genérico.
  - **Viajes:** terminan **al apagar el auto** (aviso de contacto de la radio NWD) o a los 5 min parado; litros por el consumo del lector OBD2 o, si no lo da, por lo que bajó el tanque (≈, tanque de 54 L); **resumen por mes** (km, litros, km/l) y cartel corto al terminar ("Viaje terminado: 14,0 km · 22 min · 1,1 l").
  - **Motor:** temperatura, vueltas, batería, nafta y velocidad en vivo, luz del motor y códigos con su significado; "Ver códigos, salud y borrar".
- **Borrar códigos también andando** (como Torque, pedido del autor): solo pide el lector conectado y confirmar; el botón dice "Borrar códigos y apagar la luz".
- **El aviso de velocidad pasa a Diagnóstico → Avisos** (antes en Mi auto): el botón "Velocidad" recorre no → 60 → … → 130 ("no" = apagado).

### Tests
- `CarCareTest`: cuánto falta para cada ítem, ítems sin completar, Hecho y la lista de arranque, litros por el tanque y resumen por mes (reemplazan los de recordatorios).

## [v0.78.1] - 2026-11-05

### Added
- **Kilometraje del auto por OBD2, si lo informa** (pregunta del autor): al conectar, Onix pide el kilometraje total (PID 01A6, de la norma OBD2 desde 2019). Si el auto contesta, Mi auto se carga y corrige solo; si no (lo más probable en un Onix 2018), sigue como antes: se carga una vez a mano y suma los km recorridos. Solo lectura. La Prueba completa muestra si lo informa, y "Probar OBD2" suma 0131, 01A0 y 01A6.

### Tests
- `ObdParseTest` (+1): lectura del kilometraje total.

## [v0.78.0] - 2026-11-05

### Added
- **Sonido como Google Maps y Waze** (pedido del autor): un botón con tres modos, **🔊 Todo** (indicaciones y avisos), **⚠ Solo avisos** (sin indicaciones, con los avisos de seguridad) y **🔇 Silencio** (no suena nada; los carteles se ven igual). Está en el cartel de navegación (reemplaza "Voz sí / no") y en Diagnóstico → Avisos; se recuerda.
- **Cada aviso por separado:** Diagnóstico → Avisos → Nafta, Luces, Velocidad y Motor, prendidos o apagados. Un aviso apagado no muestra cartel ni suena. `AlertPrefs` en `SafetyAlerts.kt` (preferencias `avisos`).

### Tests
- `AlertPrefsTest` (+3): los tres modos, qué suena en cada uno y cómo se guardan.

## [v0.77.0] - 2026-11-05

### Added
- **Lugares cerca** (pedido del autor): botones ⛽ Combustible, 🍔 Comida, ☕ Café, 🅿 Estacionar, 💊 Farmacia, 🏧 Cajero y 🔧 Taller en el mapa; muestran los más cercanos con la distancia ("YPF · Av. Mate de Luna 2100 · 1,2 km"). TomTom, categorías de POI.
- **Paradas en el camino:** navegando, el botón **Parada** busca sobre la ruta que falta (con el desvío en minutos); al tocar uno se agrega como parada antes del destino ("Llegaste a la parada" y sigue). "Quitar parada" la saca.
- **Mis lugares:** Casa, Trabajo y favoritos se guardan **escribiendo la dirección** (pedido del autor: "lo escribo y lo guarda"); se busca con TomTom y muestra lo que guardó. Se abre con ✎ en el mapa o tocando Casa / Trabajo sin guardar (también desde Inicio). Los favoritos se borran con ×. Sigue "Guardar como Casa / Trabajo" en el destino.
- **Nafta baja:** con el OBD2 en 12 % o menos y andando, aviso con voz ("Atención: queda poca nafta. ¿Busco una estación en el camino?") y botón **Buscar**, una vez cada 30 min. Frase nueva `nafta_baja` (Piper y voz Comercial).
- Nuevo `NearbyPlaces.kt`.

### CI
- Las dos generaciones de voces (`voces.yml`, `voces_ia.yml`) ya no se pisan: corren de a una y reintentan el push.

### Tests
- `NearbyPlacesTest` (+5): URL por categoría, lugares cerca y en el camino ordenados, cuerpo del pedido, ruta con parada y maniobra de la parada, y cuándo avisar la nafta.

## [v0.76.0] - 2026-11-05

### Added
- **Destinos desde el celular, sin cable** (pedido del autor): en WhatsApp (o cualquier app), Compartir → **ntfy** al canal privado del autor; Onix lo recibe en segundos, busca la dirección con TomTom y pregunta "¿Ir a …?". Con **Ir** abre la navegación y arranca la guía sola. Entiende una dirección escrita, la ubicación que manda WhatsApp, links de Google Maps (también los cortos, `maps.app.goo.gl`) y coordenadas. Solo toma mensajes de los últimos 15 min. Canal: GitHub Secret `NTFY_TOPIC` (entra al compilar). Nuevo `PhoneShare.kt`.
- Diagnóstico y Prueba completa: estado del canal ("Destinos del celular").

### Tests
- `PhoneShareTest` (+5): dirección escrita, ubicación de WhatsApp, links de Maps (con coordenadas, con dirección, cortos), coordenadas fuera de rango y mensajes de ntfy (solo los recientes).

## [v0.75.0] - 2026-11-05

### Changed
- **Búsqueda y rutas con TomTom** (pedido del autor, ADR-0014): el buscador encuentra las direcciones con altura de Tucumán ("9 de Julio 550, Tafí Viejo", "Av. Adolfo de la Vega 473"), que OpenStreetMap no tenía, y las rutas traen tránsito en vivo. También el nombre del punto elegido en el mapa y la localidad de Inicio. Se quitaron Nominatim, Photon, OSRM y FOSSGIS (el autor pidió no dejarlos ni de respaldo). La clave es del autor (GitHub Secret `TOMTOM_API_KEY`, entra al compilar); el dibujo del mapa no cambia.
- **Prueba completa:** líneas "Búsqueda (TomTom)", "Rutas (TomTom)" y "Rutas (TomTom, TLS 1.3 directo)", y la prueba TLS con `api.tomtom.com`.

### Security
- El registro de la app tapa las claves en las direcciones (`key=***`, `token=***`), para que no aparezcan en el informe (`AppLog.redact`).

### Tests
- `NavApiTest`: lugares, ruta con maniobras, traducción de maniobras, inverso y URL de TomTom (reemplazan los de Nominatim y OSRM). `DirectHttpsTest` (+1): el registro tapa las claves.
- Prueba real en la CI (`tomtom_prueba.yml`): las dos direcciones encontradas con la altura y sus rutas calculadas.

## [v0.74.0] - 2026-11-04

### Changed
- **Motores de Android Auto y CarPlay de CarLink 1.0.9** (antes 1.0.7), pedido del autor: reconexiones y arranques más rápidos (USB reenchufado, iPhone que vuelve), reintento al volver el Wi-Fi o el Bluetooth, un celular que ya no está emparejado no frena los arranques automáticos, cifrado de CarPlay más rápido en Android 7.1–8.1 y arreglos de los motores (DiPlay 0.2.22). Código copiado sin cambios (`vendor/ONIX_CHANGES.md`). `conscrypt-android` de la app pasa a 2.8.0.

### Added
- **CarLink al día (`carlink.yml`):** todos los días revisa la última release de CarLink. Si hay una nueva o la fijada deja de estar publicada (lo que dejó los APK sin CarPlay, AUD-068), verifica el APK nuevo con su `.sha256`, actualiza `tools/carlink.properties` y pide una compilación. Si cambió la versión de los motores, abre un aviso (issue) para actualizarlos.
- La versión y la huella del APK de CarLink pasan de `build.yml` a `tools/carlink.properties`.

## [v0.73.0] - 2026-11-04

### Fixed
- **OBD2 que no aparecía al dar contacto** (pedido del autor: "que sea automático"). La conexión Bluetooth con el lector no tenía tiempo máximo: si el ELM327 todavía no estaba listo, Onix se quedaba en "conectando…" para siempre (Prueba completa del 04/11). Ahora (`ObdRecovery`, en `Obd.kt`):
  - tiempo máximo de 15 s para conectar, y se cancela cualquier búsqueda de equipos antes;
  - tres formas de conectar: segura, insegura y canal 1 directo (la que usan Torque y otras apps con los clones);
  - reintento enseguida al dar contacto (aviso de la radio NWD, estado 1) o al prender la pantalla, y reintentos cada 3 s al principio;
  - tras 3 fallas seguidas, se apaga y prende el Bluetooth de la radio solo (como máximo 2 veces por contacto, separadas por 2 min, y nunca con Android Auto o CarPlay andando);
  - si el Bluetooth está apagado, se prende.
- Permiso `BLUETOOTH_ADMIN` (hasta Android 11) declarado por la app; ya lo traía el motor de CarLink.

### Tests
- `ObdRecoveryTest` (+4): cuándo reiniciar el Bluetooth, cuándo no, esperas entre reintentos y señales de contacto y pantalla.

## [v0.72.3] - 2026-11-04

### Fixed
- Build 127: `VoiceClipsTest` no compilaba (un reemplazo de texto rompió el `import` de `assertEquals`); corregido.

### Changed
- **Voz Comercial por defecto para todo** (corrección del autor: la que más le gustó es la Comercial, no el Locutor AR): slogan, aviso de luces y todas las indicaciones y avisos grabados (`ia_<clave>.ogg`, ahora con `Onix Comercial`, tono serio). La elección guardada antes se ignora (preferencia nueva `voz3`).

## [v0.72.2] - 2026-11-04

### Changed
- **Todas las frases grabadas con el Locutor AR** (pedido del autor): indicaciones del GPS ("En 200 metros, girá a la derecha"), "Recalculando", "Llegaste" y los avisos (velocidad, motor, batería, luces) se graban con ElevenLabs y la voz `Onix Locutor AR`, con tono serio (estabilidad 0,85, sin estilo), en `assets/voz/ia_<clave>.ogg`. La app usa esas si están; si no, las de Piper. Las genera `voces_ia.yml` a partir de `tools/voces/frases.txt` (línea `@@frases` de `elevenlabs.txt`).

### Tests
- `VoiceClipsTest` (+1): nombre del archivo de cada frase con el Locutor AR.

## [v0.72.1] - 2026-11-04

### Changed
- **Locutor AR por defecto para todo** (pedido del autor): slogan de inicio y aviso de luces. Las tres voces del autor (`Onix Comercial`, `Onix Locutor AR`, `Onix Locutora AR`) se crearon en la web de ElevenLabs y la CI las usó por su nombre. La elección guardada antes se ignora (preferencia nueva `voz2`), así arranca con el Locutor AR; se puede cambiar en Diagnóstico.
- **Aviso de luces con tono serio:** se graba con estabilidad alta y sin estilo ("Atención. Las luces están apagadas. Encendelas para un viaje más seguro."); el slogan conserva la energía.

### Tests
- `IntroAndFestiveTest`: Locutor AR por defecto y primero en la lista.

## [v0.72.0] - 2026-11-04

### Changed
- **Voces con IA (ElevenLabs):** todas dicen solo el slogan "Find new roads" (pedido del autor; se quitó "¡Arrancamos!" y los saludos). Voz nueva **Comercial**: locutor de publicidad estadounidense de voz grave, buscado en la biblioteca de ElevenLabs por uso "advertisement" y descripción "deep" (estilo de comercial de autos; no imita a ninguna persona). Es la voz por defecto cuando está.
- **Aviso de luces apagadas con la voz elegida:** pitido + "Atención: las luces están apagadas. Encendelas para un viaje más seguro." dicho por la voz elegida en Diagnóstico (ElevenLabs genera `luces_<voz>.ogg`); con las voces de Piper sigue la frase de siempre.
- **Auto / CarPlay:** con una sesión andando, el panel del dock muestra **Volver a Android Auto** (o **Volver a CarPlay**) para regresar a la pantalla de proyección; "Cortar" pasa a llamarse **Desconectar**.

### Removed
- Los puntitos de página de Inicio: desde v0.48.0 no se desliza entre pantallas (se cambia con los botones).

### Added
- **Diagnóstico → Última tecla:** muestra en vivo qué manda cada botón de la radio o del volante (también si se repite la misma).

### Tests
- `IntroAndFestiveTest` (+1): voz Comercial por defecto y aviso de luces con la voz elegida. `ProjectionTest` (+1): pantalla de cada motor para volver.

## [v0.71.0] - 2026-11-04

### Fixed
- **Rutas en la radio (HU-013), nuevo intento:** la Prueba completa del 04/11 (14:59) mostró que el saludo TLS 1.3 de Conscrypt con OSRM y FOSSGIS anda, pero el pedido por `HttpURLConnection` falla ("Read error … protocol error"). La causa probable es que el `HttpURLConnection` de Android 7 deja el socket solo con su lista de TLS 1.2, y esos servidores no aceptan ese TLS 1.2 (el TLS del sistema también falla con ellos). Ahora, si OSRM y FOSSGIS no contestan, la ruta se pide por **TLS 1.3 directo** (`DirectHttps`, en `Net.kt`): socket de Conscrypt sin cambios, certificado y nombre del servidor verificados, y un GET HTTP/1.1 simple.
- **Dock → Teléfono:** abría el marcador de Android, que la radio no tiene ("No se pudo abrir android.intent.action.DIAL"). Ahora abre la app de teléfono Bluetooth de la radio (`com.nwd.android.phone`) o, si no está, otra app de teléfono (`PhoneApp`, en `Dock.kt`).

### Added
- **Prueba completa:** dos líneas nuevas, "Rutas (OSRM, TLS 1.3 directo)" y "Rutas de respaldo (FOSSGIS, TLS 1.3 directo)", para confirmar el arreglo en la radio.

### Tests
- `DirectHttpsTest` (+5): lectura de la respuesta HTTP (largo fijo, en trozos, error y basura), armado del pedido y elección de la app de teléfono.

## [v0.70.1] - 2026-11-04

### Fixed
- **CarPlay vuelve a los APK (AUD-068):** desde el build 86 (v0.59.0) la compilación no pudo armar la identidad de CarPlay y todos los APK salieron **sin CarPlay** (solo el build 84 la tuvo): CarLink publicó versiones nuevas y borró el APK 1.0.7 (HTTP 404). La CI ahora toma la identidad del APK de **CarLink 1.0.9** con su huella SHA-256 publicada (`d4737a02…dac46`) y, si esa versión también desaparece, usa la última release de CarLink con la huella de su archivo `.sha256` y avisa. El paso dice por qué falla (descarga, huella o contenido del APK).
- **Voces con IA:** la clave de ElevenLabs cargada no tiene el permiso *Voices: Read* (HTTP 401 `missing_permissions`), así que la primera generación no hizo ninguna voz. `tools/voces/elevenlabs.py` ahora sigue con voces prediseñadas de ElevenLabs (George, Brian, Sarah) cuando no puede usar la biblioteca, y no pisa la lista de voces si no generó ninguna.

## [v0.70.0] - 2026-11-04

### Added
- **Voces con emoción para el slogan (ElevenLabs):** pedido del autor (más expresivas y con estilos argentinos). Workflow nuevo `voces_ia.yml` y `tools/voces/elevenlabs.py`: si existe el secreto `ELEVENLABS_API_KEY`, generan una vez cuatro voces: Locutor AR y "¡Arrancamos!" (voz masculina argentina de la biblioteca de ElevenLabs), Locutora AR (femenina argentina) y Asistente IA (voz George, estilo asistente sereno; voz original, no imita a ningún actor). Los audios quedan en el APK: la radio no usa internet ni la clave. Guía: `docs/VOCES_IA.md`.
- Diagnóstico → Sonido de inicio muestra esas voces primero cuando existen; la voz por defecto pasa a Locutor AR.

### Tests
- `IntroAndFestiveTest`: voz por defecto y orden con las voces de IA.

## [v0.69.0] - 2026-11-04

### Added
- **Voces para elegir del slogan "Find new roads":** pedido del autor (la voz anterior sonaba genérica y sin ganas). La CI genera seis voces con Piper y un efecto de locutor de radio (graves, presencia, compresión y un poco de sala; tono algo más grave): Locutor y Épico (en_US-ryan-high), Locutor 2 (en_US-joe-medium), Británico (en_GB-alan-medium), Grave (en_US-norman-medium) y Daniela (es_AR-daniela-high, con más energía). Queda también la Original de v0.65.0. Configuración en `tools/voces/inicio.txt`; fichas de licencia en `assets/voz/LEEME_inicio.txt`.
- **Diagnóstico → Sonido de inicio:** botón "Voz y sonido / Solo sonido / Apagado" y una fila de voces: tocar una la hace escuchar y la elige. Por defecto, Locutor.

### Tests
- `IntroAndFestiveTest` (+1): voces disponibles según el APK y voz por defecto.

## [v0.68.1] - 2026-11-04

### Changed
- **Descripciones de las releases para el público (pedido del autor):** las notas de `docs/releases/` se escriben en forma impersonal, sin hablarle al autor. Se corrigieron las de v0.66.0 a v0.68.0 (y dos frases de v0.34.0 y v0.41.0). El pie de cada release dice "Instalación: descargar … e instalarlo sobre la versión anterior".
- La descripción se arma con un único script (`.github/scripts/release_notes.sh`), que usan la compilación y el workflow nuevo **Rehacer notas de releases** (`notas.yml`, a pedido): vuelve a armar releases ya publicadas con la nota corregida y conserva sus resultados reales.
- `README.md`: tabla de la CI al día con los cuatro trabajos de v0.68.0.

## [v0.68.0] - 2026-11-04

### Changed
- **APK más liviano (pregunta del autor, ADR-0013):** solo lleva el código para procesadores de 32 bits, que es lo que usa la radio (Allwinner T3). Las librerías de 64 bits (mapa, conexiones seguras, motores de Android Auto y CarPlay) ocupaban unos 16 MB sin usarse. En la radio no cambia nada. Ya no se instala en celulares solo de 64 bits (se puede compilar para ellos con `-Ponix.abis=armeabi-v7a,arm64-v8a`).
- **Versiones publicadas antes:** la CI corre en paralelo los tests de lógica, el APK y las capturas (antes los tests iban primero y el APK esperaba), y los motores compilan su código nativo solo para 32 bits. Los tests siguen siendo obligatorios: si uno falla, no hay release.
- Las notas de cada release dicen el **tamaño del APK**.

### Fixed
- El paso que crea el tag `vX.Y.Z` ya no marca la compilación como fallida si GitHub no lo deja crear (pasó en el build 108: la release estaba publicada igual).

### Docs
- `README.md`: requisitos (procesador de 32 bits, probado en la radio) y optimización al día.

## [v0.67.1] - 2026-11-04

### Fixed
- **Auditoría 4 (todo el proyecto):** informe en `docs/audits/AUDIT-2026-11-04c.md`.
- AUD-061: si una voz grabada no se podía cargar, el reproductor y el archivo quedaban abiertos (se gastaba memoria de audio cada vez). Ahora se liberan.
- AUD-062: si el sonido de inicio fallaba, el canal de audio no se liberaba. Ahora se libera siempre.
- AUD-063: la guía de la Prueba completa decía tres pruebas y una lista vieja; ahora son las cuatro del instructivo (A a D) y la lista de lo que revisa está al día.
- AUD-065: la Prueba completa ahora dice la **salud por sistema** del auto (OBD2), en una línea: qué sistema tiene falla o pide atención.
- AUD-064: el README mostraba resultados de tests de la compilación 18 y un roadmap hasta v0.38; `TODO.md` tenía cuatro pendientes ya resueltos.

### Tests
- `CarHealthTest` +1: resumen de la salud por sistema para el informe.

### Docs
- `docs/INSTRUCTIVO.md` reescrito completo y detallado: todo lo que tiene que hacer el autor, paso a paso.

## [v0.67.0] - 2026-11-04

### Changed
- **Adornos festivos realistas y animados (pedido del autor):** Navidad con tres tiras de lucecitas con cable (borde del techo, frente del capó sobre la parrilla y el faro, línea de la ventanilla), cada luz con su brillo y titilando a su ritmo; nieve con relieve y carámbanos; moño con lazos y cintas. Fechas patrias: la bandera flamea y la escarapela tiene cintas. Halloween: calabazas talladas con gajos y una vela que parpadea adentro, sombrero de bruja con hebilla. Corazones que laten y suben; banderines que se mueven con el viento; flores con hojas. Los contornos se midieron sobre la foto del auto. Con el modo liviano quedan quietos.

### Tests
- `IntroAndFestiveTest` +1: reparto parejo de luces y adornos a lo largo de un contorno (`Festive.along`).

## [v0.66.1] - 2026-11-04

### Fixed
- Adornos del techo (nieve, luces, sombrero, gorro, guirnaldas y flores) un poco más arriba: tapaban el borde del parabrisas. Ubicación verificada dibujándolos sobre la foto del auto.

## [v0.66.0] - 2026-11-04

### Changed
- **Temas festivos sobre el auto (pedido del autor):** las fiestas decoran el Onix de Inicio en vez de saludar. Navidad: nieve en el techo, luces de colores y un moño en la parrilla. Fechas patrias: bandera argentina en la antena y escarapela en el capó. Halloween: sombrero de bruja en el techo y calabazas junto a las ruedas. Año Nuevo: guirnalda y gorro de fiesta. Carnaval, Reyes y Día del Amigo: guirnalda de colores. San Valentín, Día del Padre y de la Madre: corazones. Pascua y Primavera: guirnalda de flores. El cielo mantiene su detalle (nieve, fuegos, papel picado, murciélagos…).
- Se sacaron el saludo festivo de la franja y las 17 frases grabadas de las fiestas (el saludo vuelve a ser "Buenos días / tardes / noches").

## [v0.65.0] - 2026-11-04

### Added
- **Sonido de inicio:** con la presentación suena un sonido propio (tres notas tipo campana, compuesto en el código: no es el de ningún auto) y la voz grabada dice el slogan "Find new roads". Opciones en la ventana del saludo: voz y sonido / solo sonido / apagado. No suena con Android Auto o CarPlay conectados ni más de una vez cada 30 s.
- **Temas festivos:** en Inicio, decoración y saludo según la fecha de Argentina: Navidad (nieve y luces), Año Nuevo (fuegos artificiales), Reyes, San Valentín, Carnaval, Pascua, Malvinas, 25 de Mayo, Día del Padre, Día de la Bandera, Independencia, Día del Amigo, San Martín, Primavera, Día de la Madre, Halloween (murciélagos) y Soberanía. Las fechas patrias llevan celeste y blanco y el Sol de Mayo. Pascua, Carnaval y los días del Padre y de la Madre se calculan cada año. El saludo de la fiesta también se dice con voz al iniciar. Con el modo liviano la decoración queda quieta. En la ventana del saludo: prender/apagar y "Ver tema" para probar cada uno.
- 18 frases grabadas nuevas (`inicio` y `fiesta_*`). La Prueba completa informa el sonido de inicio y la fiesta de hoy.
- **Releases con descripción (regla de oro 3, pedido del autor):** cada release de GitHub muestra las novedades de `docs/releases/vX.Y.Z.md` y el resultado real de los tests (paso "Notas de la versión" de `build.yml`).

### Tests
- `IntroAndFestiveTest` (5, nuevo): sonido sin saturar y terminado en silencio, opciones, fechas fijas y fechas que cambian (Pascua, Carnaval, Día del Padre y de la Madre).

## [v0.64.1] - 2026-11-04

Correcciones de la auditoría 3 (`docs/audits/AUDIT-2026-11-04b.md`).

### Fixed
- **AUD-058:** con Android Auto, CarPlay u otra app encima, Onix congelaba la última velocidad: Mi auto seguía sumando km con el auto parado y el aviso de velocidad o de luces podía repetirse. Ahora Mi auto y los avisos usan la velocidad en vivo del OBD2 o del GPS (`GpsSpeed.live`), que no depende de la pantalla.
- **AUD-059:** el lint completo (`lint.yml`) analiza solo el código de Onix, con más memoria y corte a los 45 min.
- **AUD-060:** las voces se generan solo con la voz argentina registrada; si no baja, el workflow se frena.
- Lint completo (primera corrida que termina, 4 min 23 s): 120 avisos, 0 de compatibilidad con Android 7, 4 errores. Arreglado `LocalContextConfigurationRead` en Diagnóstico; el resumen del lint ahora escribe en el log el archivo y la línea de cada error y aviso de seguridad ; los 3 `RestrictedApi` estaban en `MainActivity` (registro de teclas con `dispatchKeyEvent`, interno de AndroidX): ahora usa `onKeyDown`. Los 6 `TrustAllX509TrustManager` son de librerías de terceros, no del código de Onix (aceptado).

### Added
- `docs/INSTRUCTIVO.md`: qué tiene que hacer el autor, paso a paso.

### Tests
- `CarCareTest` +1: velocidad en vivo (OBD2 primero, GPS solo si es reciente).

## [v0.64.0] - 2026-11-04

Ideas de LiteDash 1.4.53–1.4.55 (GPL-3.0), con código propio.

### Added
- **Salud del auto:** la ventana de códigos de falla muestra arriba ocho sistemas (motor y encendido, aire y combustible, enfriamiento, batería y carga, caja, escape y emisiones, frenos y ruedas, carrocería y red) con un punto verde, naranja (pendiente o aviso), rojo (código guardado o recalentado) o gris (sin datos). Cada código se ubica por los grupos de la norma SAE J2012.
- **Música por Bluetooth sin controles:** si la app que suena no publica su sesión (algunas de Bluetooth de la radio), ⏮ ⏯ ⏭ mandan la tecla de música como lo haría un botón de volante.

### Tests
- `CarHealthTest` (3, nuevo): sistema de cada código y estado de cada sistema.

## [v0.63.0] - 2026-11-04

### Added
- **Aviso de velocidad:** en Mi auto se elige un máximo (no, 60, 80, 100, 110, 120 o 130 km/h; apagado de fábrica). Si se pasa por más de 3 km/h durante 5 s, cartel naranja, pitido y voz ("Atención: estás pasando la velocidad máxima que elegiste"); repite cada 2 min mientras siga. Usa la velocidad del OBD2 o del GPS. La Prueba completa informa el máximo elegido.
- Frase grabada nueva `velocidad_alta`.
- **Cobertura de los tests:** la CI mide con JaCoCo qué parte del código recorren los tests de lógica y la informa en `tests.txt` (antes "no medida"). No frena la compilación si falla.

### Tests
- `SafetyAlertsTest` +1: aviso de velocidad (apagado, tolerancia, elección y texto del botón).

## [v0.62.0] - 2026-11-04

### Added
- **Prueba completa más útil:** nuevas revisiones de **Android Auto / CarPlay** (estado del motor, permisos que faltan, autorización de VPN, otra app de proyección), **memoria de cada proceso** (la app y el motor de Android Auto `:aa`, AUD-041), **modo liviano**, **Mi auto** (kilometraje, viajes, recordatorios) y **teclas del volante** (cada tecla distinta que llegó, en su propia sección, R-45). "Voces grabadas" ahora dice cuáles faltan.
- Las teclas y avisos de teclas de la radio se guardan aparte (hasta 60) para que no se pierdan entre los otros avisos.
- Guía `docs/PRUEBA_COMPLETA.md`: qué revisa y cómo hacer las tres pruebas para que el informe sirva.

### Tests
- `FullReportTest` +2: voces faltantes y teclas vistas sin repetir.

## [v0.61.0] - 2026-11-04

### Added
- **Voces grabadas:** la radio no tiene TextToSpeech, así que los avisos y la navegación solo hacían un pitido. Ahora hay 31 frases grabadas en castellano rioplatense (avisos de luces y motor, "Recalculando", "Llegaste a destino" y las indicaciones de navegación: "En doscientos metros," + "girá a la derecha.", rotondas, bifurcaciones). Bajan la música mientras hablan. Si el equipo tiene TextToSpeech, se sigue usando ese (dice también el nombre de la calle).
- Las genera el workflow **Voces grabadas** (`voces.yml`) con Piper (MIT) desde `tools/voces/frases.txt` y las guarda en `app/src/main/assets/voz/` (~10 KB cada una). Voz **es_AR-daniela-high** (castellano argentino, corpus OpenSLR 61, CC BY-SA 4.0): ficha en `assets/voz/LEEME.txt`, crédito en Acerca de y en `THIRD_PARTY_LICENSES.md`.
- Prueba completa: "Voces grabadas" (si están en el APK).

### Tests
- `VoiceClipsTest` (3, nuevo): toda frase que usa la app está en `frases.txt`, maniobras y aviso previo con distancia.

## [v0.60.1] - 2026-11-04

### Changed
- **Compilación más rápida (pedido del autor):** el lint completo (~25 min con los motores de Android Auto y CarPlay) frenaba la publicación del APK. Pasó a un workflow aparte, **Lint completo** (`lint.yml`), que corre los lunes y a pedido. La compatibilidad con Android 7.1 (`NewApi`) se sigue revisando en cada compilación y ahora **frena** la release si falla (`fatal += NewApi`). Cada build pasa de ~25 a ~11 minutos.
- README: versiones de Media3 al día (1.11.1).

## [v0.60.0] - 2026-11-04

Correcciones de la segunda auditoría (`docs/audits/AUDIT-2026-11-04.md`).

### Fixed
- **AUD-050:** con el FM sonando por la antena, al conectar Android Auto o CarPlay no se escuchaba el celular: ahora se corta el FM y el audio vuelve a Android.
- **AUD-048:** al pasar de la radio por internet al FM por la antena, el display quedaba vacío.
- **AUD-056:** sin señal de GPS la última velocidad quedaba fija (velocímetro, Mi auto, aviso de luces): se olvida a los 5 s.
- **AUD-049:** con Android Auto o CarPlay conectados, Onix ya no retoma Spotify o la radio al prender la pantalla.
- **AUD-053:** Spotify se podía desconectar solo al renovar el token dos veces a la vez.
- **AUD-055:** navegando sin señal, la guía se apagaba si fallaba el recálculo: sigue con la ruta anterior y reintenta cada 30 s.
- **AUD-051:** "Borrar códigos" solo con el lector conectado y el auto parado; un pedido viejo no se ejecuta al reconectar.
- **AUD-052:** los km de un viaje se guardan a medida que se maneja (antes se perdían si Android cerraba la app).
- **AUD-054:** una ruta vieja ya no se dibuja si se eligió otro destino. **AUD-057:** los pedidos de permisos no se superponen.

### Tests
- `CarCareTest` (+1): velocidad del GPS sin señal. `TunerMathTest` (+1): la antena no pierde su estado.

## [v0.59.0] - 2026-11-03

Correcciones de la auditoría del 2026-11-03 (`docs/audits/AUDIT-2026-11-03.md`).

### Fixed
- **AUD-036 (crítico):** Android Auto y CarPlay no habrían arrancado: el motor exige micrófono y ubicación y Onix nunca pedía el micrófono. Ahora se piden al abrir Onix (una vez) y, si el motor se frena por permisos, la pantalla Auto/CarPlay se abre sola con **Dar permisos**.
- **AUD-037:** CarPlay por cable necesita la autorización de VPN de Android. La pantalla Auto/CarPlay se abre sola y tiene **Autorizar**.
- **AUD-038:** el ruido del GPS con el auto estacionado armaba viajes y sumaba km: un viaje arranca desde 10 km/h.
- **AUD-040:** se quita el permiso `REQUEST_INSTALL_PACKAGES` que traía el motor (sin uso).

### Added
- **AUD-039:** la pantalla Auto/CarPlay avisa si hay otra app de proyección instalada (por ejemplo CarLink) que se puede quedar con el USB.
- **AUD-044:** la CI escribe `tests.txt` y `lint.txt` también en el log.
- **AUD-047 (abierto):** lint con `checkDependencies = false` y 25 min; todavía no termina con los motores adentro (builds 86 y 87). Apagarlo en los módulos del motor rompía la release (build 88) y se volvió atrás.

### Tests
- `CarCareTest` (+1): ruido del GPS estacionado. `ProjectionTest` (+1): cuándo se abre sola la pantalla.

## [v0.58.0] - 2026-11-03

### Added
- **Modo liviano** (pedido del autor): en Automático se prende solo en radios de gama baja (sin 64 bits, 4 núcleos o menos, o 2 GB de RAM o menos; la del autor lo es). Sin lluvia animada ni giro del emblema, la presentación solo al abrir la app (no en cada encendido) y el mapa arranca en el clásico si nunca se eligió el 3D. Diagnóstico → **Liviano** (automático / sí / no).
- **CarPlay sin hacer nada (pedido del autor):** si no hay secretos cargados, la CI baja el APK público de CarLink 1.0.7, verifica su huella SHA-256 (la que publica CarLink: `16e8b984…3e77`) y saca de ahí la identidad de CarPlay. Sigue sin estar en el repositorio. Los secretos de v0.57.0 quedan como opción.

### Tests
- `LiteModeTest` (2, nuevo): gama baja y elección.

## [v0.57.0] - 2026-11-03

Etapa 4 de ADR-0012: **CarPlay con su identidad**, sin guardarla en el repositorio.

### Added
- La CI arma la identidad de CarPlay desde dos GitHub Secrets (`CARPLAY_IDENTITY_PK8` y `CARPLAY_CERTIFICATE_P7B`, en base64) y la mete en el APK (`DIPLAY_AUTH_ASSETS_DIR`). Sin los secretos, CarPlay avisa que le falta y Android Auto anda igual.
- `docs/CARPLAY.md`: cómo sacar los dos archivos del APK de CarLink o DiPlay y cargarlos en GitHub.

### Changed
- El aviso de CarPlay sin identidad indica `docs/CARPLAY.md`.

## [v0.56.0] - 2026-11-03

Etapa 3 de ADR-0012: **Android Auto y CarPlay dentro de Onix**, con el motor de CarLink. **Onix pasa a licencia AGPL-3.0** (decisión del autor).

### Added
- **Motor de CarLink 1.0.7** (F-e-n-y-x/CarLink, commit `cfba714`, AGPL-3.0) dentro del proyecto: `carlink-core/` (detección del celular, sesiones, registro) y los dos motores en `vendor/`: DiAuto (Android Auto, proceso `:aa`) y DiPlay (CarPlay). Sin cambios en su código.
- **Al enchufar el celular por USB arranca solo:** Android → Android Auto, iPhone → CarPlay, en la pantalla del motor. Al desenchufar vuelve a Onix.
- **Pantalla Auto/CarPlay** (botón del dock): estado en castellano (esperando, conectando paso a paso, conectado, por qué falló) y botón Cortar.
- `OnixApp`: clase Application que hereda de la del motor de Android Auto e instala el motor (`CarLinkCore.install`). El celular ve al auto como "Onix".
- CarPlay todavía no conecta: le falta su identidad (llega en v0.57.0 desde GitHub Secrets). La pantalla lo dice.

### Changed
- **Licencia: AGPL-3.0** (`LICENSE`, `COPYRIGHT.md`, `README.md`, `CLAUDE.md`, Acerca de).
- Media3 1.3.1 → 1.11.1 y Conscrypt 2.5.2 → 2.6.1 (las mismas que el motor).
- R8: sin renombrar clases (`-dontobfuscate`), como CarLink; las reglas de cada motor vienen con él.
- CI con **JDK 21** (antes 17): el lint de AGP 9.3 se cae con JDK 17 al revisar el motor de Android Auto (`List.removeLast`, build 81). El código sigue compilando para Java 17.
- La app pide los permisos del motor (USB, Bluetooth, Wi-Fi, micrófono, llamadas): ver README → Permisos.
- `.gitignore` bloquea los archivos de identidad de CarPlay (`*.pk8`, `*.p7b`).

### Tests
- `ProjectionTest` (+2): estado en palabras y que Onix no se cuente como app de proyección aparte.
- Los tests con Robolectric usan una Application simple (`robolectric.properties`): los motores no corren en la CI.

## [v0.55.0] - 2026-11-03

Etapa 2 de Android Auto y CarPlay dentro de Onix (ADR-0012): herramientas de compilación al día. Sin cambios visibles.

### Changed
- **Herramientas de compilación** iguales a las de CarLink: Gradle 8.7 → **9.5.0**, Android Gradle Plugin 8.5.2 → **9.3.0** (compila Kotlin por su cuenta), Kotlin 1.9.24 → **2.2.10** (con el plugin de Compose), compileSdk 34 → **37**, Compose BOM 2024.06.00 → **2026.02.01**. targetSdk sigue en 34.
- **Android mínimo: 7.1** (minSdk 24 → 25), lo que pide CarLink. La radio del autor tiene 7.1.1.
- Lint: se desactiva el aviso de targetSdk viejo (la app no se publica en Google Play).

### Tests
- Sin tests nuevos: las capturas de la CI tienen que verse iguales que en el build 79 (R-47).

## [v0.54.0] - 2026-11-03

### Added
- **Auto/CarPlay en el dock:** abre la app de Android Auto / CarPlay instalada (CarLink, que al autor le funciona por USB; también reconoce open-headunit, DiPlay, ZLink, AutoKit, EasyConn y otras por el nombre). Si no hay ninguna, avisa cuál instalar. Es el puente hasta tener Android Auto y CarPlay dentro de Onix (ADR-0012, por etapas).

### Changed
- Dock: **Auto/CarPlay** reemplaza a **Ajustes** una sola vez (Ajustes sigue en el cajón de Apps y se puede volver a poner en el dock).

### Tests
- `ProjectionTest` (4, nuevo): prefiere CarLink, reconoce otras por el nombre, sin app de proyección, cambio del dock.

## [v0.53.0] - 2026-11-03

### Added
- **Arranque más rápido:** `androidx.profileinstaller` instala los perfiles de arranque que traen Compose y las demás librerías. En un APK instalado a mano (sin Play Store) Android no los aplica solo; con esto la radio precompila esas partes y el launcher abre y se mueve más rápido en el procesador T3 (por medir: R-20 y Diagnóstico → Fluidez).
- **Botones del volante, primer paso:** el informe anota cada tecla que llega al launcher (código, nombre, código de hardware y fuente), junto con el aviso `ACTION_KEY_VALUE` de la radio. Con una Prueba completa después de tocar cada botón del volante se sabe qué manda cada uno, y en la versión siguiente se pueden configurar (play, siguiente, Radio, Navegación…). No se cambió lo que hace ningún botón.

### Tests
- `AmbientAndMediaTest` (+1): texto de la tecla en el informe.

## [v0.52.0] - 2026-11-03

### Added
- **Mi auto** (nuevo acceso del dock, con una llave): 
  - **Viajes** anotados solos con la velocidad del OBD2 (o del GPS): empiezan al andar y terminan a los 5 minutos parado o con el contacto apagado. Se guardan los últimos 30 con km, tiempo, velocidad máxima y promedio, temperatura máxima del motor, litros y km/l (con el OBD2).
  - **Kilometraje**: se carga una vez el del tablero y después suma lo que se maneja.
  - **Recordatorios**: cambio de aceite y service (por fecha y por km, lo primero que llegue; al marcar **Hecho** se programa el siguiente: 12 meses o 10.000 km), VTV, seguro, patente u otro. Verde, naranja (faltan 15 días o 500 km) o rojo (vencido).
  - Aviso en Inicio una vez por día si hay un recordatorio cerca o vencido (cartel azul, sin sonido).
- El OBD2 calcula también los litros por hora (para los litros del viaje, aun parado).
- **Flujo de trabajo** en `CLAUDE.md`, adaptado de gentle-ai (MIT): estado del proyecto en `docs/tasks/ESTADO.md` para retomar entre sesiones, archivo de tarea para el trabajo grande, tests primero y commits chicos.

### Changed
- Dock: **Mi auto** reemplaza a **Diagnóstico** (una sola vez; Diagnóstico sigue en la franja de abajo de Inicio, tocando el sol o la luna, y se puede volver a poner en el dock).

### Tests
- `CarCareTest` (7, nuevo): viaje de 10 km con parada, maniobras cortas, huecos sin datos, urgencia y textos de los recordatorios, el siguiente al marcar hecho, meses y día de hoy, y el cambio del dock.
- `ScreenshotTest` (+1): pantalla Mi auto.

## [v0.51.0] - 2026-11-03

### Added
- **Radio FM por la antena del auto (por confirmar en el auto):** en la radio NWD, la pantalla de radio tiene un interruptor **ANTENA / INTERNET**. Con ANTENA, el dial maneja el sintonizador FM real de la radio: suenan las ~77 emisoras de Tucumán sin internet (por internet andan menos de la mitad), cualquier frecuencia del dial se puede sintonizar, ⏮ ⏭ buscan en el aire la siguiente emisora con señal y el display muestra el nombre que transmite la emisora (RDS). Inicio y la pantalla de música controlan igual la radio (play/pausa, siguiente, anterior).
- `NwdTuner.kt`: cliente propio del servicio `com.nwd.radio.service` (Binder, interfaz `RadioFeature`) y del cambio de fuente de audio de la radio (FM = 4, Android = −128). Los datos de la interfaz salen de CleanLauncher; el código es propio.
- Al dar play a Spotify (o a otra app) mientras suena el FM, el audio vuelve a Android solo.
- Diagnóstico ("Radio FM (antena)") y Prueba completa ("Sintonizador FM de la radio") muestran si hay sintonizador, el modo, la frecuencia, el RDS y el resultado de la última orden.
- Manifiesto: `<queries>` para el paquete `com.nwd.radio.service` (no es un permiso).

### Tests
- `TunerMathTest` (3, nuevo): frecuencia en el formato del servicio (MHz × 100) y de vuelta, bandas FM/AM y fuente de audio.

## [v0.50.0] - 2026-11-03

Ideas de LiteDash (GPL-3.0: solo ideas, código propio) y pedidos del autor.

### Added
- **Códigos de falla del motor (OBD2):** se leen la luz del motor (01 01), los códigos guardados (modo 03) y los pendientes (modo 07) al conectar el lector, cada 5 minutos y a pedido. Cada código se explica en castellano (unos 60 genéricos frecuentes; del resto, el sistema: encendido, emisiones, caja…). Al tocar la línea de códigos del panel de motor se abre una ventana con la lista, **Leer de nuevo** y **Borrar códigos** (modo 04: solo con el auto parado y confirmando dos veces).
- **Alertas del motor:** motor recalentado (110 °C o más durante 8 s), batería que no carga (menos de 12,3 V con el motor andando durante 1 min), voltaje demasiado alto (más de 15,3 V) y batería baja con el motor parado (menos de 11,9 V). Cartel rojo arriba de cualquier pantalla, pitido y voz.
- **Consumo estimado en km/l** al lado de la batería: el Onix Joy 1.0 no tiene sensor MAF, así que se calcula con la presión de admisión (01 0B), la temperatura del aire (01 0F) y las rpm; si el auto tiene MAF (01 10), se usa ese. Es una estimación para comparar.
- **Aviso de luces apagadas (pedido del autor):** con la señal de luces de la radio, si el auto anda a 20 km/h o más con las luces apagadas de noche, al atardecer o con lluvia (o a 60 km/h o más de día, en ruta) durante 15 s: cartel naranja, pitido y la voz "Atención: las luces están apagadas. Encendelas para un viaje más seguro." No se repite antes de 10 minutos. La voz necesita "Servicios de voz de Google" en la radio (HU-011); sin eso suena solo el pitido.
- Diagnóstico y Prueba completa muestran códigos de falla, consumo, alertas y el estado del aviso de luces; la prueba cruda del OBD2 pide también 01 01, 01 0B, 01 0F, 01 10, 03 y 07.
- `THIRD_PARTY_LICENSES.md`: LiteDash como referencia sin código copiado.

### Changed
- **Luces sin selector en Inicio (pedido del autor):** la franja de abajo solo muestra "Luces prendidas" o "Luces apagadas", según el auto. El modo manual (encendidas, apagadas, por hora) pasó a Diagnóstico → **Luces**, para casos raros.
- README: se quitó de "Todavía no implementado" lo que ya funciona (luces reales y OBD2).

### Tests
- `ObdCodesTest` (13, nuevo): códigos en CAN de una y varias líneas y en protocolos viejos, letras P/C/B/U, luz del motor, borrado, descripciones, alertas y su espera, consumo, y el lector completo contra **`FakeElm327`**, un ELM327 simulado (con y sin MAF, con códigos, borrado aceptado y rechazado).
- `SafetyAlertsTest` (5, nuevo): cuándo avisa de las luces, que no repita en cada semáforo y el texto de la franja.
- `ScreenshotTest` (+2): ventana de códigos y aviso de luces.

## [v0.49.0] - 2026-10-30

### Added
- **Luces sin el sensor de iluminación (por confirmar):** CleanLauncher, un launcher para la misma radio NWD, lee las luces con el aviso `com.nwd.ACTION_ILL_STATE_CHANGE` y con el bit 2 del ajuste `mcu_no_source_device_state` (en nuestro informe de las 11:41 ese ajuste pasó de 0 a 2 al prender las luces). `RadioIll` ahora escucha esas dos vías además de `high_beam_state`. Si llegan con el sensor apagado, se evita que la radio baje el brillo.
- El informe registra también los avisos de luces, contacto (`ACTION_MCU_STATE_CHANGE`), marcha atrás y fuente de audio de la radio.
- `THIRD_PARTY_LICENSES.md`: referencias sin código copiado (CleanLauncher, Horizon-Maps).

### Tests
- `AmbientAndMediaTest` (+1): luces en el bit 2 del estado de la radio.

## [v0.48.0] - 2026-10-29

Primeras entregas de la auditoría integral (B1 y C1) y brillo.

### Changed
- **Sin deslizar entre pantallas (C1):** la pantalla de navegación y música se abre solo con los botones (Navegación, Música); se vuelve con **Inicio** o con el botón **Atrás** de la radio. Ya no se cambia de pantalla por accidente al mover el mapa o una lista.
- **Menos trabajo de fondo (B1):** el registro completo de ajustes y propiedades de la radio (v0.44.0) corría siempre: con cada cambio leía las tres tablas de ajustes en el hilo principal y ejecutaba `getprop` cada 3 s. Ahora corre solo con Diagnóstico abierto o durante la Prueba completa, en un hilo aparte. La señal de luces (`high_beam_state`) sigue escuchándose siempre, que es liviana.

### Added
- **Brillo siempre al máximo:** con el sensor de iluminación prendido, la radio baja el brillo al prender las luces. El launcher pide brillo máximo para su propia ventana (sin permisos). Se puede desactivar en Diagnóstico → **Brillo**. Si la radio baja la luz de fondo por su microcontrolador, puede no alcanzar (por confirmar en el auto).

### Fixed
- Lint `RememberReturnType` en `HomeScreen.kt` (carga de favoritas).

### Tests
- `AmbientAndMediaTest` (+1): valor de brillo de la ventana.

## [v0.47.0] - 2026-10-28

### Added
- **Mapa 3D (etapa 1, ADR-0011):** el mapa de la segunda pantalla ahora es vectorial con MapLibre 10.3.7 (anda con el OpenGL ES 2.0 de la radio) y mosaicos gratis de OpenFreeMap. Estilo oscuro propio, edificios en 3D, nombres de calles, vista inclinada (45°, 60° navegando) que sigue y gira con el auto, botón **2D/3D**. La ruta, el destino y mi ubicación se dibujan como capas del mapa. Mantener apretado sigue eligiendo un punto.
- `Map3D.kt`: interfaz `NavMap` con el mapa 3D (`LibreNavMap`) y el clásico (`OsmNavMap`). Si el 3D no arranca o no carga, se vuelve solo al clásico y se avisa.

### Changed
- APK solo para procesadores ARM (radio de 32 bits y celulares de 64 bits).
- Acerca de y el mapa muestran la atribución de OpenFreeMap / OpenMapTiles / OpenStreetMap.

### Tests
- `NavApiTest` (+1): estilo del mapa 3D (JSON válido, edificios 3D, nombres) e inclinación.

## [v0.46.0] - 2026-10-28

### Fixed
- **Búsqueda de direcciones:** "9 de julio 550 tafi viejo" no daba resultados. Ahora, si Nominatim no encuentra nada, se prueba con Photon (antes solo si Nominatim no contestaba), y si tampoco aparece, se busca otra vez sin la altura (muchas calles de Tucumán no tienen numeración en OpenStreetMap); esos resultados dicen "(sin la altura)". El "9" de "9 de Julio" no se toma como altura.

### Added
- **Prueba completa:** prueba de conexión segura con los servidores de rutas en cada dirección IP (IPv4/IPv6), con el TLS de la app y el del sistema, para encontrar por qué fallan en la radio si en el celular andan (HU-013).

### Tests
- `NavApiTest` (+1): búsqueda sin la altura.

## [v0.45.0] - 2026-10-28

### Fixed
- **Luces de la app según las luces del auto (HU-009):** la Prueba completa de las 11:42 encontró la señal. Con el "sensor de iluminación" de la radio prendido, el ajuste `high_beam_state` pasa a 1 al prender las luces del auto y a 0 al apagarlas. El modo "según la radio" ahora la usa (`RadioIll`, se entera al instante por aviso de cambio). Diagnóstico y el informe muestran la señal.
- **OBD2:** el clon del lector tardó más de 3 s en reiniciarse y sus respuestas quedaron con eco; ATZ espera 5 s y ATE0 se repite.

### Changed
- El registro de cambios del informe ignora lo que cambia todo el tiempo (app en pantalla, hora de la radio, procesos).

### Known
- Rutas (OSRM y FOSSGIS) siguen fallando en la radio también con TLS 1.3 (HU-013): no es la versión de TLS. Falta probar la dirección desde el celular con el mismo Wi-Fi.

### Tests
- `AmbientAndMediaTest` (+2): señal de luces y ruido del registro.

## [v0.44.0] - 2026-10-28

### Added
- **Búsqueda de la señal de luces (HU-009):** con las luces apagadas y prendidas no cambió nada de lo que se miraba. El autor encontró en los ajustes de la radio un "sensor de iluminación" (apagado de fábrica) que sí detecta las luces. Ahora el informe anota los cambios de **todos** los ajustes del sistema (system, global y secure, no solo los que parecen de luces) y de las propiedades de la radio (`getprop`: `sys.nwd.*`, `persist.*` con luz/noche/CAN, etc., revisadas cada 3 s), y lista esas propiedades en la Prueba completa.

### Tests
- `AmbientAndMediaTest` (+1): lectura de `getprop`.
- Los builds 66 (v0.43.0) y 67 fallaron: `conscrypt-android` no carga en los tests de Robolectric (`UnsatisfiedLinkError`). Se excluye de los tests; la app lo sigue usando. v0.43.0 no se publicó por separado: sus cambios salen con v0.44.0.

## [v0.43.0] - 2026-10-28

### Fixed
- **Rutas en la radio (HU-013):** OSRM y su respaldo FOSSGIS seguían fallando con "Handshake failed" aun con las raíces de Let's Encrypt (Prueba completa 11:20 y 11:21): el problema es el TLS de Android 7.1, no el certificado. Ahora, en Android 7 a 9, todas las conexiones HTTPS usan Conscrypt (TLS 1.3 y curvas modernas, dentro de la app), con la misma confianza del sistema y de `network_security_config.xml`.

### Added
- Dependencia `org.conscrypt:conscrypt-android` 2.5.2 (Apache 2.0) y reglas de R8 para conservarla.
- La Prueba completa informa qué TLS se usa ("Conexiones seguras").

### Changed
- Acerca de: créditos de Photon, FOSSGIS y Conscrypt.

## [v0.42.0] - 2026-10-28

Arreglos a partir de la primera Prueba completa en la radio (26 OK · 3 FALLA · 2 N/D).

### Fixed
- **Rutas en la radio (OSRM "Handshake failed"):** se agregaron como raíces de confianza los certificados públicos de Let's Encrypt (ISRG Root X1 y X2), que Android 7.1 no trae completos. Si OSRM igual no contesta, la ruta se pide al servidor de respaldo de FOSSGIS (mismo formato).
- **Búsqueda (Nominatim sin respuesta):** si Nominatim no contesta, se busca con Photon (komoot), en la misma zona.
- **Nombre del lugar:** el `Geocoder` de la radio no tiene servicio; ahora el nombre sale de OpenStreetMap (Nominatim y, si no, Photon) y ya no llena el registro de avisos.
- **Informe:** los permisos que no existen en Android 7.1 (Bluetooth cercano, servicio en primer plano, notificaciones) dicen "no aplica" en vez de "no concedido".

### Added
- **Luces:** el informe anota cada cambio de los ajustes del sistema que parecen de luces (por ejemplo `mcu_back_light_state`), para encontrar cuál cambia al prender las luces del auto (HU-009).
- **Voz:** si la radio no tiene motor de voz, el botón del mapa dice "Sin voz" y el informe explica qué instalar (HU-011).
- La Prueba completa también revisa los respaldos (Photon y FOSSGIS).

### Tests
- `NavApiTest` (+3): Photon, localidad y ruta de respaldo. `AmbientAndMediaTest` (+1): cambios de ajustes. `FullReportTest` (+1): permisos que no aplican.

## [v0.41.0] - 2026-10-28

### Changed
- **Diagnóstico: un solo botón, Prueba completa (`FullReport.kt`).** Reemplaza a "Probar radio", "Probar OBD2" y "Copiar informe". Revisa todas las funciones en hasta 2 minutos (red y servicios de internet con tiempo de respuesta, GPS, clima, luces, OBD2 con respuestas crudas, radio, música y Spotify, voz y mapa, permisos, espacio, interfaz) y marca cada una como OK, FALLA o N/D, con un resumen arriba. Sigue aunque se cierre Diagnóstico.
- **Informe en archivo:** se guarda como `onix_informe_AAAA-MM-DD_HH-mm.txt` en Descargas/OnixLauncher y además se copia. No lleva tokens, claves, el Client ID de Spotify ni coordenadas.
- La voz de navegación informa su estado (`Voice.state`) para la prueba.

### Added
- Permiso `WRITE_EXTERNAL_STORAGE` solo hasta Android 9 (en 7.1 se pide la primera vez); desde Android 10 no hace falta.

### Removed
- `buildReport` (lo reemplaza el informe de la prueba completa).

### Tests
- `FullReportTest` (4): resumen, respuestas HTTP, radio con audio, nombre del archivo.

## [v0.40.0] - 2026-10-28

### Added
- **Cambiar el nombre del saludo:** tocando "Hola, Axel" se abre un cuadro para elegir uno de los últimos nombres usados o escribir otro (por ejemplo, Atalia). Queda guardado (`GreetingName`, SharedPreferences `saludo`).
- **Vuelta del Wi-Fi (`NetWatch`):** si la red se corta y vuelve, la radio retoma sola la emisora (si se había cortado por la red, no si la pausaste) y el clima se pide de nuevo enseguida. Spotify se reconecta solo. Permiso nuevo: `ACCESS_NETWORK_STATE` (automático).

### Fixed
- **OBD2 que no conectaba al dar contacto:** el lector podía conectarse antes de que la computadora del auto estuviera lista y quedarse contestando solo el voltaje, hasta apagar y prender el auto. Ahora, al conectar, espera a que el lector encuentre el protocolo del auto, y si el auto no contesta 5 lecturas seguidas reinicia el lector solo (en 3 s).

### Tests
- `ObdParseTest`: reinicio del lector (`ObdWatch`). `AmbientAndMediaTest`: nombre del saludo (`GreetingName`).

## [v0.39.0] - 2026-10-28

### Added
- **Lluvia según la hora:** con lluvia, el fondo sigue el momento del día: lluvia de día, de atardecer y de noche (`bg_lluvia_atardecer.jpg` y `bg_lluvia_noche.jpg`, derivados con `tools/make_bg_rain.py`). Antes había un solo fondo de lluvia de día.
- **Saludo:** "Hola, Axel · Buenos días" (6 a 12 h), "Buenas tardes" (12 a 20 h) o "Buenas noches", centrado en la franja de abajo de Inicio.
- **Presentación animada (`Intro.kt`):** emblema de Chevrolet que gira hasta quedar de frente, con halo y brillo, línea dorada y "FIND NEW ROADS"; dura unos 3,5 s y se saltea tocando. Aparece al abrir el launcher y cada vez que la pantalla se prende (volver de apagado o suspensión), esto último solo si el launcher es la pantalla de inicio.

### Tests
- `AmbientAndMediaTest`: 3 tests nuevos (lluvia por hora, saludo, tramos de la presentación). Capturas nuevas: `11_lluvia_noche`, `12_lluvia_atardecer`, `13_presentacion`.

### CI
- La compilación verifica que el APK esté firmado con la clave de siempre (huella pública en `.github/signing-cert.sha256`); si no, falla antes de publicar. Avisos del origen de la firma. Decisión de firma: opción A (ADR-0010).

## [v0.38.1] - 2026-10-28

Revisión técnica pedida por el autor (hallazgos en `AUDIT_REPORT.md` sección 20).

### Fixed
- **AUD-028:** `RadioDirectory.merge()` cerraba la app si una emisora del dial no tenía frecuencia (`d.freq!!`); ahora compara frecuencias solo si las dos existen.
- **AUD-029:** el servicio de radio (exportado porque lo exigen los controles del sistema) aceptaba órdenes de cualquier app, incluso reproducir un enlace cualquiera. Ahora las órdenes propias llevan una clave al azar del proceso; los controles del sistema siguen igual.
- **AUD-030:** retomar al encender (v0.38.0) se activaba también en el celular y podía fallar al arrancar la radio en segundo plano. Ahora solo actúa si el launcher es la pantalla de inicio, con manejo de errores.

### Changed
- **AUD-033:** el panel de motor dice "sin GPS", "sin OBD2" y "OBD2 sin conectar" en lugar de solo "--".
- **AUD-001 (preparado, sin cambios):** la compilación acepta la firma desde GitHub Secrets con la **misma clave**; si no están cargados, usa el archivo como siempre (`docs/SIGNING.md`, ADR-0010).

### Docs
- **AUD-034:** README (características, hoja de ruta, seguridad de red exacta), VERSION, ARCHITECTURE y HEAD_UNIT al día; compatibilidad mínima Android 7.0 aclarada.

### Tests
- `AuditRegressionTest`: 3 tests de regresión (merge sin frecuencia ×2, órdenes del servicio de radio): 73 de lógica.

## [v0.38.0] - 2026-10-28

### Added
- **Regla de oro 2** (`CLAUDE.md`, pedido del autor): todo funciona dentro del launcher; solo se sale a otra app para configurar algo la primera vez o cuando Android no deja otra opción. Las salidas que quedan están en `README.md` → "Salidas a otras apps".
- **Retomar al encender** (informado por el autor: al prender la radio había que volver a entrar a Spotify): el launcher recuerda la última fuente que sonaba (Spotify o una emisora) y la retoma sola al abrir la app y al prenderse la pantalla. Si el usuario pausó desde el launcher, no la retoma.

### Changed
- Spotify ya no se abre: si estaba cerrado, se le manda la tecla PLAY y arranca en segundo plano (tarjeta de música, pestaña Spotify, botón Spotify).
- El acceso **Música** del dock va a la pantalla de música del launcher en lugar de abrir Spotify.

### Tests
- 1 test de qué se retoma: 70 de lógica.

### Sin probar
- Que Spotify arranque en segundo plano con la tecla PLAY en la radio, y que la radio avise "pantalla prendida" al poner contacto.

## [v0.37.0] - 2026-10-27

### Added
- **Navegación giro a giro propia, sin Google Maps ni Waze** (pedido del autor): **Iniciar** sigue la ruta con el GPS; cartel arriba con flecha, distancia a la próxima maniobra e indicación en castellano ("Girá a la derecha en Laprida", "En la rotonda, tomá la 2.ª salida"); distancia restante y hora de llegada; **avisos por voz** con la voz del equipo (TextToSpeech, sin internet) a 300 m y al llegar a la maniobra; **recálculo** si el auto se sale del camino; el mapa sigue y gira con el auto. Botones Voz sí/no y Terminar.
- **Elegir un punto en el mapa:** mantener apretado cualquier lugar lo toma como destino (con su nombre de Nominatim).
- Diagnóstico muestra la versión de **OpenGL ES** y el procesador (para decidir el mapa 3D).

### Changed
- Se quitan Google Maps y Waze: la tarjeta de navegación de Inicio abre el mapa propio y tiene **Casa** y **Trabajo**; el acceso Navegación del dock abre el mapa; el destino ya no tiene "Maps"/"Waze".

### Fixed
- **No se podía volver deslizando desde la pantalla del mapa** (informado por el autor): el deslizamiento estaba desactivado en esa pantalla. Ahora se desliza desde la música o la barra de arriba; sobre el mapa, el dedo sigue moviendo el mapa.

### Tests
- `GuidanceTest` (3) y 1 más en `NavApiTest` (maniobras e inverso): 69 de lógica.

### CI
- Build 56 falló (dos intentos): la flecha del cartel llamaba a `drawscope.rotate` con el nombre completo, que Kotlin no acepta para funciones de extensión; corregido. Las capturas figuraban "success" porque ese paso no bloquea. Desde ahora, si los tests o las capturas fallan, las líneas de error se publican como avisos de la CI.

### Sin probar
- Navegación con el auto en movimiento y la voz del equipo (requiere la radio).

## [v0.36.0] - 2026-10-27

### Added
- **Lugares en el mapa** (pedido del autor, como en Maps y Waze): accesos rápidos **Casa**, **Trabajo**, **★ Favoritos** y **Recientes** debajo del buscador; en el destino, **☆/★ Favorito**, **Guardar como Casa** y **Guardar como Trabajo**. Los recientes son los últimos 10 destinos. Todo se guarda en el equipo.

### Changed
- **Mapa sin conexión:** la caché de mosaicos pasa a una carpeta permanente de hasta 500 MB (antes estaba en la caché que Android puede borrar). Sin internet se ve lo que ya se recorrió y la ubicación por GPS; buscar y calcular la ruta necesitan internet. No se descarga el mapa en masa: la política de OpenStreetMap no lo permite.

### Tests
- `SavedPlacesTest`: 2 tests (guardar y leer lugares, recientes sin repetir): 65 de lógica.

## [v0.35.2] - 2026-10-27

### Fixed
- **Pedidos de ubicación sin parar** (log del head unit enviado por el autor: los servicios de Google procesaban cientos de entregas de ubicación por minuto). El mapa de navegación pedía ubicación por GPS y por red sin límite; ahora solo GPS, como mucho cada 2 s o 5 m, y se apaga al salir de la pantalla.

### Added
- La app escucha los avisos propios de la radio (`com.nwd.action.ACTION_SYSTEM_VALUE_CHANGE`, `ACTION_KEY_VALUE`, `ACTION_SET_SYSTEM_PROP`; el control de brillo del sistema escucha el primero) y el informe muestra los últimos 40 distintos, para encontrar el aviso del cable de luces (HU-009).

### Tests
- 1 test del texto de los avisos: 63 de lógica.

### Del log del head unit
- `com.android.launcher` (launcher original) dibuja la barra de volumen de la radio: no hay que desactivarlo. Se cerró por un error propio (`FloatBarCallbackManager`), no del Onix Launcher.
- El head unit tiene 160 dpi: el lienzo de 1024 × 600 dp ocupa la pantalla sin escalar.

## [v0.35.1] - 2026-10-27

### Fixed
- **La radio andaba lenta** (informado por el autor): el emblema animado de Chevrolet usaba una animación infinita que redibujaba la pantalla en cada cuadro, aunque estuviera quieto. Ahora espera sin dibujar y solo anima durante la vuelta y el brillo (≈2,5 s cada 9 s); los valores se leen al dibujar, sin recomponer.
- Imágenes más livianas: fondos de 1536 × 900 a 1280 × 750 (la pantalla de la radio es de 1024 × 600) y emblema de 721 × 241 a 216 × 72. Menos memoria y decodificación más rápida.

## [v0.35.0] - 2026-10-27

### Added
- **Spotify dentro del launcher** (pedido del autor, cuenta Premium), en la pestaña Spotify de la pantalla de música:
  - Arriba, lo que suena con tapa, barra y controles.
  - Abajo, **Listas**, **Recientes** y **Buscar** (temas, artistas, álbumes y listas). Tocar algo lo reproduce en la radio con Spotify Connect, sin abrir Spotify; si la radio de la app sonaba, se pausa.
  - Conexión con PKCE: el autor pega el Client ID de su app de developer.spotify.com y da permiso en la página de Spotify; vuelve al launcher por `onixlauncher://spotify`. Client ID y tokens solo en el equipo; "Desconectar Spotify" los borra.
- `Net.request`: pedidos con método, cuerpo y encabezados (no registra encabezados).

### Tests
- `SpotifyTest`: 3 tests (desafío PKCE del RFC 7636, listas, recientes, búsqueda y dispositivos): 62 de lógica.

### Sin probar
- Con la cuenta del autor (necesita su Client ID y la radio con Spotify abierto una vez).

## [v0.34.1] - 2026-10-27

### Fixed
- **La barra del sistema de arriba quedaba fija** en el head unit (informado por el autor con foto): ahora se esconde al abrir, cada vez que la app vuelve al frente y, si aparece, a los 3 s. En Android 7.1 se usan las banderas clásicas de pantalla completa. Para verla, deslizar desde el borde.

### Added
- El informe de Diagnóstico incluye los **ajustes de luces y brillo del sistema** (todas las claves con ill, light, night, dim, bright… de las tablas system, global y secure, y el uiMode), para encontrar cómo avisa esta radio que se prendieron las luces: en el auto del autor el modo noche no cambia con las luces (HU-009).

### Probado en el auto (informado por el autor, foto)
- La pantalla se ve como en las capturas. El OBD2 lee datos reales: 49 °C de motor, 16 % de nafta y 12,1 V con el motor apagado.

## [v0.34.0] - 2026-10-27

### Changed
- **La segunda pantalla ahora es de navegación y música** (pedido del autor), en lugar del Tablero:
  - **Mapa** (OpenStreetMap con osmdroid, en modo oscuro) con tu ubicación, **buscador** de destinos (Nominatim) y la **ruta dibujada** con distancia y tiempo (OSRM). Los botones **Maps** y **Waze** abren la guía por voz hacia ese destino (solo esas apps dan la voz giro a giro).
  - **Música** con dos pestañas: **Spotify** (tapa, tema, barra, aleatorio, anterior, play/pausa, siguiente y me gusta) y **Radio** (emisora actual con controles y la lista de emisoras para elegir, favoritas primero).
  - En esta pantalla no se desliza (el mapa usa el dedo): se vuelve con **Inicio** en el dock.

### Removed
- El Tablero (`DashboardScreen.kt`): velocímetro, tacómetro y tarjetas grandes. Los datos del motor siguen en el panel de Inicio.

### Added
- Dependencia `org.osmdroid:osmdroid-android` 6.1.20 (Apache 2.0). Servicios: mosaicos de OpenStreetMap (ODbL, atribución en el mapa y en Acerca de), Nominatim y OSRM.

### Tests
- `NavApiTest`: 3 tests (búsqueda, ruta y resumen): 59 de lógica. La captura 5 pasa a ser la pantalla de navegación (el mapa no se dibuja en las capturas automáticas).

## [v0.33.0] - 2026-10-27

### Added
- **Dock personalizable** (pedido del autor): mantener apretado cualquier ícono entra en modo edición. Cada ícono muestra una × para quitarlo y el lugar queda con un **+**; tocando el + se elige cualquier app instalada o un acceso propio (Navegación, Música, Radio, Teléfono, Apps, Diagnóstico, Ajustes). Inicio queda fijo y en modo edición pasa a ser **Listo**. Se guarda en el equipo.

### Tests
- `DockTest`: 3 tests (guardar y leer los lugares del dock): 56 de lógica.

## [v0.32.0] - 2026-10-27

### Added
- **Cambiar entre la radio y Spotify desde la tarjeta de música** (pedido del autor):
  - Con la radio en la tarjeta, el botón **Spotify** pausa la radio y le da play a Spotify (si Spotify no está abierto, lo abre).
  - Con Spotify en la tarjeta, el botón **Radio** pausa Spotify y sigue la radio (si no hay emisora sintonizada, abre el panel de radios).

## [v0.31.0] - 2026-10-27

### Added
- **OBD2 con el lector ELM327 por Bluetooth** (AUD-010, `Obd.kt`): la app busca el lector entre los equipos emparejados (nombres con OBD, ELM, V-LINK, Vgate…), se conecta por puerto serie Bluetooth (también en modo "inseguro", que piden muchos clones), lo inicializa con comandos AT y lee cada ~1 s:
  - temperatura del motor (01 05), RPM (01 0C), velocidad (01 0D), nivel de nafta (01 2F, si el auto lo informa) y voltaje de batería (AT RV).
  - Si se corta, reintenta cada 10 s. Recuerda el último lector usado.
- Los datos llegan al panel de motor de Inicio y al Tablero. La velocidad del OBD2 tiene prioridad sobre la del GPS.
- Diagnóstico: estado del OBD2 con los datos en vivo, y botón **Probar OBD2** que agrega al informe la respuesta cruda de cada comando (01 00, 01 05, 01 0C, 01 0D, 01 2F, 01 42, AT RV, AT DP).
- Permisos `BLUETOOTH` (hasta Android 11) y `BLUETOOTH_CONNECT` (Android 12+, se pide una vez).

### Tests
- `ObdParseTest`: 5 tests de lectura de respuestas del ELM327 (53 de lógica).

### Sin probar
- Con el lector y el auto: no hay hardware en la CI (HU-010). El nivel de nafta puede no estar disponible en autos GM.

## [v0.30.1] - 2026-10-27

### Changed
- **Emblema de Chevrolet animado** en la franja inferior (pedido del autor): cada 9 s da una vuelta en 3D (1,2 s) y le pasa un brillo cromado; el resto del tiempo queda quieto, para no distraer al manejar.

## [v0.30.0] - 2026-10-27

### Changed
- **Las luces del auto siguen a la perilla de luces, no a la hora** (pedido del autor). Por defecto, las luces de la app (faros del Onix, haces sobre el asfalto, indicador de la franja y Tablero) siguen el **modo noche de la radio**, que estas radios activan con el cable de iluminación (ILL) conectado a las luces del auto. El fondo de día, atardecer, noche o lluvia sigue cambiando por la hora y el clima.

### Added
- Tocando "Luces" en la franja inferior se elige de dónde salen: **según la radio** (por defecto), **encendidas**, **apagadas** o **por hora** (como antes). Se guarda en el equipo (`Lights.kt`).
- Diagnóstico muestra el estado de las luces con su modo y "Modo noche (cable de luces)".

### Tests
- 2 tests nuevos de las luces: 48 de lógica.

### Sin probar
- Que la radio del auto pase a modo noche al prender las luces: depende de que el cable ILL esté conectado y de la radio (HU-009 en `docs/HEAD_UNIT.md`).

## [v0.29.4] - 2026-10-27

### Changed
- La franja inferior de Inicio dice "ONIX JOY", sin el año (pedido del autor).

## [v0.29.3] - 2026-10-27

### Changed
- **Noche más iluminada** (pedido del autor): pueblo con luces en el fondo del valle, en barrios a lo largo del valle (más chicas y juntas a lo lejos), casas sueltas en las laderas y un resplandor tenue, como en su imagen de noche (`valley_town` en `tools/make_bg_mockups.py`).

## [v0.29.2] - 2026-10-27

### Changed
- **La noche tiene la misma ruta que los otros climas** (pedido del autor). La imagen de noche tenía otra ruta y otro encuadre; ahora el fondo de noche se arma desde el del atardecer: escena oscura y fría, cielo con estrellas (el cielo se ubica con la imagen de día), luces cálidas del valle y la luz de los faros sobre el asfalto.
- El auto vuelve a tener la misma caja en todos los climas (380, 182, 391 × 256 dp); el tono de noche medido en v0.29.1 se mantiene.

## [v0.29.1] - 2026-10-27

### Fixed
- **Noche** (informado por el autor: "se ve raro"):
  - Alrededor del auto quedaba una mancha gris: el auto de la imagen de noche es el mismo de la app pero estirado. Ahora se lo ubica con puntos SIFT y una transformación afín (en las tres imágenes), se borra solo su contorno y la ruta, el pasto y la luz de los faros de la imagen quedan a la vista.
  - De noche el auto de la app usa la misma caja que en la imagen (un poco más alto) y cambia suave al pasar de un ambiente a otro.
  - El auto se veía azulado y apagado: el tono de noche y de atardecer se midió en las imágenes (`carTintFor`).
  - Los haces de los faros se veían como triángulos grises: se quitan los conos. De noche la luz ya está en el fondo; al atardecer y con lluvia queda solo una mancha suave en el piso.

## [v0.29.0] - 2026-10-27

### Changed
- **Fondos de las imágenes nuevas del autor** (valle con ruta de día, atardecer y noche, hechas con otra IA): se borran los paneles, los textos y el auto de cada imagen y se rellena lo que queda tapado (`tools/make_bg_mockups.py`, reemplaza a `make_bg_scene.py`). La lluvia se deriva del día.
- El auto de Inicio se ubica exactamente donde está en esas imágenes (380, 182, 391 × 256 dp), así tapa el lugar del que se borró.
- **Panel de motor como en las imágenes:** velocímetro (0–240 km/h) y cuentavueltas (0–8 mil rpm) redondos con aro azul y zona roja/naranja, marcas y números; barras de segmentos H/C para la temperatura y F/E para el combustible a los costados del auto visto desde arriba; batería abajo. El régimen pasa al cuentavueltas.

### Added
- **Haces de luz de los faros** sobre el asfalto cuando las luces están prendidas, con fundido.

## [v0.28.0] - 2026-10-27

### Changed
- **Fondos generados con la composición de la imagen de referencia** (`tools/make_bg_scene.py`, reemplaza a `make_bg_reference.py`): en lugar de espejar recortes, se dibujan dos cordones de cerros sin repeticiones, la ciudad con miles de luces en perspectiva y con brillo, una fila de árboles y el piso.
  - Atardecer: con las nubes de la imagen de referencia.
  - Día: cielo celeste con nubes alargadas y ciudad con bruma.
  - Noche: cielo estrellado y la ciudad más iluminada.
  - Lluvia: cielo cubierto, pocas luces y reflejo en el piso mojado.

## [v0.27.1] - 2026-10-27

### Fixed
- De noche el estado del clima se cortaba ("Noche des…"); ahora usa el texto corto ("Despejado"). Visto en las capturas del build 37.

## [v0.27.0] - 2026-10-27

### Changed
- **Fondo de la imagen de referencia** (pedido por el autor): cielo del atardecer, cerros y luces de la ciudad tomados de la imagen, con piso de asfalto. Como la imagen tiene paneles y un auto encima, el fondo se reconstruye con las partes libres (`tools/make_bg_reference.py`). Versiones de día, atardecer, noche (con las luces de la ciudad prendidas) y lluvia, para seguir cambiando según la hora y el clima.
- **Auto más chico** (380 × 249 en lugar de 450 × 295), con la sombra y la luz de los faros ajustadas.
- Fondos en 1536 × 900 (antes 1024 × 600): más nítidos en pantallas grandes.

### Removed
- La ruta con su línea central animada (`road_ground.png`, `RoadCenterLine`): el piso ahora es parte del fondo, como en la imagen.

## [v0.26.0] - 2026-10-27

### Changed
- **Inicio igual a la imagen de referencia** (pedido por el autor):
  - Reloj en formato 12 h con AM/PM, fecha con año; clima con ícono grande, estado, mínima/máxima y **nombre del lugar** (geocodificador del equipo; sin ubicación, San Miguel de Tucumán, que es donde se pide el clima).
  - Pronóstico de 5 días con mínima / máxima.
  - Música: ícono y nombre de la app que suena, tapa cuadrada, barra con degradé, botón play con aro y botones **aleatorio** y **me gusta**. Funcionan si la app que suena ofrece esas acciones; si no, se ven apagados. En la radio, el corazón marca la emisora como favorita.
  - Navegación: ícono de Google Maps y de Waze tomados de las apps instaladas.
  - Telemetría como en la imagen: velocidad arriba, temperatura y combustible a los costados del auto visto desde arriba, batería y régimen abajo. Reemplaza al medidor de arco en Inicio (el Tablero no cambia).
  - Dock de 8 accesos con separadores: Inicio, Navegación, Música, Radio, Teléfono, **Apps**, Diagnóstico y Ajustes.
  - Franja inferior: emblema, "ONIX JOY 2018", faro (azul cuando las luces están prendidas), "Luces: Automáticas", sol o luna, y acceso a Diagnóstico.
- Se quitaron de Inicio la sensación térmica, humedad, viento y probabilidad de lluvia (no están en la imagen) y los indicadores GPS/OBD2 (siguen en el Tablero y en Diagnóstico).

### Added
- **Cajón de apps** (botón Apps): todas las apps con ícono, para abrirlas desde el launcher. El manifiesto declara la consulta de apps con ícono (`<queries>`, no es un permiso).

### Sin cambios
- Fondos por hora y clima, luces del auto, lluvia, radio, edición de paneles y Tablero. Sin dependencias nuevas; sin datos simulados (sin OBD2 se ve "--").

## [v0.25.0] - 2026-10-27

### Changed
- **Rediseño visual de Inicio** (pedido por el autor, con imagen de referencia): misma distribución y funciones, con aspecto de equipo de fábrica.
  - Paleta grafito y azul profundo; todos los paneles usan el mismo vidrio (`card()`), incluido el de motor.
  - Encabezado único con reloj, fecha con año, GPS/OBD2 y el clima de ahora (mínima/máxima, sensación, humedad, viento y lluvia).
  - Pronóstico de **5 días** (antes 4) con máxima y mínima, en su propio panel.
  - Música: tapa cuadrada, barra de progreso más fina, botones secundarios más discretos.
  - Navegación: "¿A dónde vamos?", Maps destacado y Waze con borde.
  - Dock: botón **Inicio** resaltado en la pantalla principal (desde el Tablero vuelve a Inicio); íconos y textos alineados en 7 columnas iguales.
  - El emblema de Chevrolet pasa de arriba al centro a una franja inferior con "ONIX JOY 2018", los indicadores de GPS y OBD2 y el estado de las luces ("automático por hora", porque se calcula por la hora y no lee los faros).
- Los paneles "clima" y "pronóstico" cambiaron de forma: el pronóstico usa una posición nueva (`pronostico`); si habías movido el panel de clima, vuelve a su lugar.

### Sin cambios
- Fondos por hora y clima, luces del auto, lluvia, ruta, velocímetro, radio, edición de paneles y Tablero funcionan igual. No se agregaron dependencias.

## [v0.24.0] - 2026-10-26

### Added
- **Cadenas con señal nacional**, como La 100 (aprobado por el autor): Mitre (AM790 de StreamTheWorld), Radio 10 y Vale (HLS de stweb.tv, mismo formato que Mega 98.3; sin confirmar), Rivadavia (señal nacional de respaldo). Cadena 3, Radio María y las demás cadenas buscan su señal nacional en el directorio.
- La revisión de radios cuenta como "funciona" una cadena cuya señal nacional responde, y lo anota: "(señal nacional)".
- Mi Radio: segundo enlace (`/;`) además del que respondía HTTP 401.

### Changed
- La búsqueda por nombre en el directorio prefiere entradas con el mismo nombre (evita tomar otra radio parecida).
- Se quitaron los enlaces de `buecrplb01.cienradios.com.ar` (no conecta).

### Tests
- 1 test nuevo (cadenas con señal nacional, sin enlaces muertos): 46 de lógica.

## [v0.23.0] - 2026-10-26

### Added
- **Enlaces de audio para 19 radios de Tucumán** sacados de radioarg.net: El Palomar, Activa, Aspen, Mi Radio, Metropolitana 93.5, Del Plata, FM Sensaciones, Rivadavia, FMI, Metropolitana FM, Mía, Los Ralos, Contacto, Rock & Pop, La Compañera, y enlaces nuevos para Continental, LV7 (Zeno) y LV12. Los enlaces anteriores quedan como segunda opción. Ahora 22 radios del dial tienen enlace propio (antes 6); la revisión en el equipo confirma cuáles responden.

### Changed
- **La 100:** según el informe del celular del autor, el reproductor de Cienradios para Tucumán ya no existe (HTTP 404) y StreamTheWorld no tiene ninguna de las 12 señales probables de Tucumán (todas 404). Ningún directorio publica una señal de Tucumán. Se quitaron esos enlaces y el viejo de cienradios.com.ar (no conecta): La 100 arranca directo con la única señal que existe por internet, la de Buenos Aires, marcada "SEÑAL NACIONAL".

### Tests
- 2 tests nuevos (enlace de La 100, radios con enlace propio); se reemplazó el del orden de señales de La 100: 45 de lógica.

## [v0.22.2] - 2026-10-26

### Fixed
- **La 100 desaparecía de la radio.** Si la revisión decía que su señal local no respondía y no estaba en favoritas, se ocultaba. Ahora La 100 (95.5) **nunca se oculta**.
- **Con la radio puesta no se podía usar Spotify desde el inicio.** La tarjeta de música quedaba fija en la radio aunque estuviera en pausa. Ahora muestra la radio solo mientras suena o conecta; si suena otra app (Spotify, YouTube Music…), muestra esa; si no suena nada, la radio en pausa. Igual en el tablero. Ver Spotify en la tarjeta necesita el acceso a notificaciones.

### Changed
- Las radios del dial dicen "FM 95.5 · San Miguel de Tucumán" (antes "· Tucumán").

### Tests
- 2 tests nuevos (tarjeta de música con radio y Spotify, La 100 siempre visible): 44 de lógica.

## [v0.22.1] - 2026-10-26

### Added
- **Detalle de la revisión de radios en el informe:** una línea por radio con el resultado y el motivo (qué enlace anduvo, o por qué falló cada uno: "directorio: sin señal de Tucumán", "HTTP 404", "no es audio", "sin respuesta (SocketTimeoutException)", lo que devolvió la página oficial). Se guarda con la revisión, así aparece en el informe aunque se reinicie la app.
- **Seguimiento en vivo:** mientras revisa, la radio muestra "revisando… 23 de 77 · \<radio\>", y Diagnóstico tiene la fila "Revisión de radios". Inicio y fin de cada revisión quedan en los eventos.

### Why
- En el celular del autor la revisión dejó solo 10 radios; el detalle permite ver la causa de cada una.

## [v0.22.0] - 2026-10-26

### Added
- **Pruebas desde el celular, sin computadora** (Diagnóstico):
  - **Probar \<radio\>**: revisa uno por uno los enlaces de la radio elegida (página oficial, señales guardadas, directorio) y anota qué devuelve cada uno (código HTTP, tipo, título de la página, enlaces de audio encontrados, redirecciones). Para La 100 además pregunta a StreamTheWorld si existen 12 señales probables de Tucumán.
  - **Copiar informe**: copia un texto con el equipo, los sensores, el estado de la radio, la prueba y los últimos 80 eventos de la app, para pegarlo en el chat con Claude.
- Registro de eventos (`AppLog.info`): qué enlace de radio se prueba y desde dónde se reproduce.

### Changed
- Los pedidos HTTP mandan `Accept` y `Accept-Language` como un navegador (la página de Cienradios no respondía al celular: ver Diagnóstico del 2026-10-26).
- El texto de Diagnóstico pide copiar el informe en lugar de sacar foto.

## [v0.21.0] - 2026-10-26

### Added
- **Logos para casi todo el dial:** 71 de las 77 emisoras traen el logo que publica World Radio Map para su frecuencia. Las 6 restantes (Radio Belén, Nuevo Tiempo, Los 40, Radio Nacional, Continental, Radio Urquiza) lo buscan en el directorio. El logo del directorio queda como segunda opción si el primero no carga. Caché de logos renovada (`logos_v2`) para que se vean los nuevos.
- Páginas web de Radio Activa, Radio Universidad, Radio Del Jardín y Radio Municipal (de ahí sale su ícono si hace falta).
- **La 100 LOCAL:** además de la página oficial, se prueban tres señales probables de StreamTheWorld para Tucumán (`FM955_56`, `FM955_56AAC`, `FM955AAC`) antes de pasar a la nacional. **No están confirmadas**: no se encontró la señal de La 100 Tucumán publicada en ningún sitio.
- LV12: enlace de audio conocido.
- **Regla de oro** (`CLAUDE.md`): la documentación se actualiza en el mismo cambio que el código, y la CI lo verifica (`check_docs.py`).

### Documentation
- `ARCHITECTURE.md` al día (estaba en v0.17.0): módulos de radio (`RadioCatalog`, `RadioLogos`, `RadioNow`), flujos de datos, reconexión, CI en tres trabajos.
- Notas de las versiones v0.18.0 a v0.20.0 en `docs/releases/`.

### Tests
- 2 tests nuevos (logos del dial, orden de las señales de La 100): 42 de lógica.

## [v0.20.0] - 2026-10-26

### Added
- **La radio en la pantalla de inicio y en el tablero.** Cuando suena una radio de la app, la tarjeta de música muestra su logo, nombre, frecuencia, estado en vivo (o por qué está en pausa) y controles: ⏮ ⏭ cambian de emisora (entre favoritas si hay dos o más) y ⏯ pausa o sigue. Tocar la tarjeta abre la radio. Se actualiza sola y no necesita el acceso a notificaciones.
- **Solo radios que funcionan.** La app revisa en el equipo, de a 4 por vez, qué radios responden con audio en su señal de Tucumán (la señal nacional no cuenta) y oculta las que no. Las favoritas se muestran siempre. La revisión se guarda, se repite cada 3 días y se puede pedir con **REVISAR**; mientras corre, el encabezado muestra el avance. Si ninguna responde (sin internet), no se guarda nada.
- **Categorías:** Noticias, Deportes, Música, Religiosa, Cultura y Otras, con botones para filtrar (junto a TODAS y ★ FAVORITAS). Las cadenas nacionales y emisoras conocidas tienen su categoría fija; el resto sale de las etiquetas del directorio de radios.

### Fixed
- **No aparecía la notificación de la radio ni se mantenía en segundo plano.** El servicio no registraba su sesión de medios hasta que se conectaba un control externo; ahora la registra al crearse, con lo que muestra la notificación con controles y queda como servicio en primer plano mientras suena.

### Changed
- Pantalla de radio: display y dial un poco más bajos para sumar la fila de categorías.

### Tests
- 2 tests de lógica (categorías, detección de audio) y 1 captura nueva (`10_inicio_radio.png`): 40 de lógica y 10 capturas.

## [v0.19.2] - 2026-10-26

### Fixed
- **Más radios con logo.** Antes solo se usaba el ícono del directorio cuando la radio aparecía en la lista de Tucumán, y fallaban los íconos `.ico`, los de más de 300 KB y los que redirigían entre http y https. Ahora:
  - Se prueba también el ícono de la página web de la emisora (`apple-touch-icon.png`, `favicon.ico`), salvo redes sociales y directorios.
  - Si la radio del dial no trae logo, se busca por nombre en el directorio: se acepta la entrada de Tucumán o, para cadenas nacionales (Los 40, Mitre, Aspen, Cadena 3, etc.), la de la cadena. El resultado se recuerda; si no hay logo, se reintenta a la semana.
  - Se leen íconos `.ico` (PNG o BMP adentro), hasta 1 MB, con redirecciones.
  - Como mucho 3 descargas de logos a la vez.
- **La 100 LOCAL:** la lectura del reproductor oficial reconoce más formas de declarar la señal ("mount" en JavaScript, `tritonMount`, etc.) y prefiere la que dice "tuc", "tucuman" o "955".

### Added
- Diagnóstico → **Radio (página)**: cuántos enlaces de audio encontró en la página del reproductor, de qué servidores y cuál eligió. Los valores largos se ven en dos líneas.

### Tests
- 5 tests nuevos: 38 de lógica.

## [v0.19.1] - 2026-10-26

### Fixed
- **La radio se pausaba sola.** Causas posibles que se corrigieron:
  - Con la pantalla apagada, el equipo podía dormir la CPU o el Wi-Fi y cortar el audio: ahora la radio los mantiene activos mientras suena (permiso `WAKE_LOCK`).
  - Cuando el servidor cerraba la conexión, la radio quedaba en pausa; ahora reconecta sola a la misma señal (hasta 4 intentos: 1, 3, 6 y 10 s).
  - Un corte de red en medio de la transmisión hacía pasar a la señal siguiente (por ejemplo, de LOCAL a NACIONAL); ahora primero reintenta la misma.
- Si la pausa la provoca otra app que toma el audio, o la desconexión del Bluetooth o los auriculares, el display lo dice ("EN PAUSA · OTRA APP" / "EN PAUSA · AUDIO DESCONECTADO") y queda en Diagnóstico → Último error.

### Tests
- 1 test nuevo (esperas de reconexión): 33 de lógica.

## [v0.19.0] - 2026-10-26

### Added
- **Logos de las radios** en el display y en las presintonías. Son los íconos que cada emisora publica en el directorio Radio Browser: se descargan una vez, se guardan achicados (128 px) en la caché de la app y no se incluyen en el repositorio. Las radios sin logo muestran una insignia de color con sus iniciales.
- **Favoritas:** estrella en el display para marcar la radio que suena; mantener apretada una presintonía también la marca o desmarca. Las favoritas aparecen primero y llevan una estrella. Se guardan en el equipo; la primera vez arranca con La 100.
- Botón **★ FAVORITAS**: muestra solo las favoritas en las presintonías y hace que ⏮ ⏭ salten solo entre ellas.

### Changed
- La búsqueda de radios de Tucumán en el directorio trae hasta 100 resultados (antes 40), para encontrar más logos.
- Las presintonías son más anchas (logo + frecuencia + nombre en dos líneas).

### Tests
- 3 tests nuevos (logo en la mezcla del dial, iniciales, favoritas): 32 de lógica.

### Technical
- (Desde el build 23, todavía con v0.18.1) Compilación más rápida: los tests y la APK corren en paralelo con las capturas y el lint (tres trabajos), Gradle queda abierto entre pasos, con caché de compilación, compilación en paralelo y 4 GB de memoria. Los cambios solo de documentación ya no compilan.

## [v0.18.1] - 2026-10-26

### Fixed
- **La 100 no sonaba ni en LOCAL ni en NACIONAL.** Cienradios (La 100, Mitre) pasó a transmitir por StreamTheWorld (Triton Digital) y los enlaces viejos `buecrplb01.cienradios.com.ar` quedaron sin uso. Además, desde v0.17.0 los enlaces sacados de la página oficial solo se aceptaban de cienradios, así que los de StreamTheWorld se descartaban. Ahora:
  - Se aceptan enlaces de audio de `streamtheworld.com`, también sin extensión y como "mount" en la configuración del reproductor.
  - Señal nacional de La 100: `FM999_56` (StreamTheWorld); de Mitre: `AM790_56AAC_SC`. Los enlaces viejos quedan como último recurso.
  - El reproductor sigue redirecciones entre https y http (StreamTheWorld las usa); antes ExoPlayer las cortaba.
- En modo NACIONAL, la búsqueda por nombre en el directorio buscaba primero la señal de Tucumán.

### Added
- Soporte de radios en HLS (`.m3u8`) con `media3-exoplayer-hls`.
- El error de la radio en Diagnóstico dice qué servidor falló.

### Tests
- 4 tests nuevos de enlaces de StreamTheWorld (29 de lógica en total).

## [v0.18.0] - 2026-10-26

### Changed
- Inicio: el velocímetro del panel de motor, al lado del auto, pasa de un número suelto a un **medidor de arco** de 240° (0 a 200 km/h) con degradé cian → azul → violeta → rojo, brillo, marcas cada 10 km/h, punto luminoso en la punta y movimiento suave entre lecturas del GPS. Sin GPS muestra "--" y "SIN GPS".

### Technical
- La CI crea el tag `vX.Y.Z` de cada versión la primera vez que la compila (`.github/scripts/tag_versions.sh`). `v0.18.0` es el primero. Las versiones anteriores no pueden etiquetarse desde Actions (GitHub lo rechaza con HTTP 403 porque esos commits tienen otro workflow); se identifican por su release `build-N`.
- La CI corre Android Lint (no bloqueante) y publica `lint.txt` con los avisos de compatibilidad con Android 7 (`NewApi`).

### Fixed
- Builds 19 y 20: falló el paso de tags al intentar etiquetar versiones viejas (la app y las releases se publicaron bien). `v0.18.0` quedó creado en el build 20; desde el build 21 el paso solo etiqueta la versión actual.

### Documentation
- `docs/HEAD_UNIT.md`: compatibilidad con Android 7.1, interfaz en 1024 × 600, instalación, actualización, recuperación, diferencias celular/CI/radio y hallazgos HU-001 a HU-007.
- `docs/TEST_PLAN.md`: plan de pruebas con estados (Aprobada, Fallida, Bloqueada, No ejecutada).

## [v0.17.0] - 2026-10-26 — build 18 (código en `06409c5`, build de verificación 17)

Correcciones de la auditoría del 2026-10-26. Los IDs remiten a `AUDIT_REPORT.md`.

### Added
- Pantalla **Acerca de** (desde Diagnóstico): versión, "Desarrollado por Axel Hernán González", copyright y créditos de terceros, incluida la atribución de Open-Meteo (CC BY 4.0) (AUD-022, AUD-024).
- **Último error** en Diagnóstico y logs con la etiqueta `OnixLauncher` (AUD-009).
- Diagnóstico muestra la versión como `0.17.0 · build N`.
- 25 tests de lógica (radio, clima, ambiente, música, autoría) que bloquean la release (AUD-016, AUD-017).
- Capturas de Acerca de y Diagnóstico (9 en total) y `tests.txt` con los resultados en cada release.
- Gradle wrapper 8.7 con validación en la CI (AUD-018).

### Changed
- La música se actualiza **por eventos** (cambio de canción, pausa, reproductor nuevo) en lugar de consultarse cada segundo; la barra de progreso se calcula localmente (AUD-005).
- El reloj de la pantalla se actualiza una vez por minuto (cada segundo solo con Diagnóstico abierto).
- La animación de la ruta ya no se reinicia con cada lectura del GPS (AUD-006).
- Frecuencias, voltaje y RPM con punto decimal fijo, sin depender del idioma del equipo (AUD-013).
- El permiso de notificaciones se pide una sola vez (AUD-025).
- Versión SemVer explícita en `version.properties`; `versionName = 0.17.0` (AUD-020).
- Acciones de GitHub actualizadas: checkout v6, setup-java v5 (AUD-019).
- `LauncherApp.kt` (930 líneas) dividido en `HomeScreen`, `Dock`, `Diagnostics`, `Location`, `About`, `Ui` y `Net` (AUD-015).

### Removed
- Botón **Cámara** del dock, que solo mostraba un aviso; vuelve cuando se conozca la app de cámara del head unit (AUD-011).
- Código muerto: `drawScene`, `drawCar`, `AmbientColors`/`colorsFor`, `Speedometer`/`drawGauge`/`LabelPaint`, `ObdCard`/`GaugeBar`, `StatusChip`, `iconCamera`, `RadioDirectory.featured` (AUD-014).

### Fixed
- Radio: al cambiar rápido de emisora o apagar mientras se buscaba un enlace, podía sonar la emisora anterior. Ahora cada orden tiene un número de generación y las respuestas viejas se descartan (AUD-007).
- Las conexiones HTTP se cierran siempre y se verifica el código de respuesta (AUD-008).
- Listas `.m3u` con `=` en el enlace se leían mal.

### Security
- `network_security_config`: clima, directorio y reproductores oficiales siempre por HTTPS; HTTP solo para el audio (AUD-002).
- De la página del reproductor solo se aceptan enlaces de `cienradios.com` / `cienradios.com.ar` (AUD-003).
- Solo se confía en los certificados del sistema.

### Technical
- `Net.get` (HTTP con cierre garantizado y límite de tamaño), `AppLog`, `MediaWatcher`, `parseWeather`, `radioCandidates`, `firstStreamInPlaylist`.
- `buildConfig` habilitado (`BUILD_NUMBER`). Tests de JVM con `isReturnDefaultValues`.
- Ubicación por defecto como constantes `DEFAULT_LAT` / `DEFAULT_LON` (AUD-026).

### Documentation
- README completo de proyecto: SDKs, dependencias, módulos, permisos, servicios, estructura, compilación, tests, CI y seguridad, con capturas.
- Equivalencia versión ↔ build documentada (cada versión tiene su release `build-N`).

### Pendiente de la auditoría
- AUD-001 (clave de firma en el repo): requiere cargar secretos en GitHub desde la web; queda como riesgo aceptado mientras el repo sea privado.
- AUD-010 (OBD2) y AUD-027 (prueba en el head unit): funcionalidad y prueba pendientes.

## Documentación posterior a v0.16.0 - 2026-10-26 — `c7aa8f2` (sin build, sin cambio de versión)

### Documentation
- Sistema de documentación: CHANGELOG, VERSION, ARCHITECTURE, TODO, COPYRIGHT, THIRD_PARTY_LICENSES, ADRs y notas de release.
- Primera auditoría integral: `AUDIT_REPORT.md`, `PROJECT_AUDIT.md`, `docs/audits/AUDIT-2026-10-26.md`.
- Paneles en Claude (Dashboard y Auditoría) registrados en `docs/ARTIFACTS.md`.

## [v0.16.0] - 2026-10-26 — build 16 (`fff2181`)

### Added
- Interruptor LOCAL / NACIONAL en la radio. Se guarda la elección y re-sintoniza la emisora que está sonando.
- Señal nacional de Mitre como alternativa.

## [v0.15.0] - 2026-10-26 — build 15 (`5dbfdab`)

### Changed
- Radio: prioridad a la señal local de Tucumán. La 100 (95.5) toma el enlace del reproductor oficial; la nacional queda como último recurso y se avisa en el display.
- La búsqueda en el directorio prefiere resultados de Tucumán.

## [v0.14.0] - 2026-10-26 — build 14 (`474cfa0`)

### Added
- Radio con interfaz de FM: display LCD, dial de 87.5 a 108 MHz con aguja, búsqueda, encendido y presintonías.
- Dial con ~75 emisoras FM de San Miguel de Tucumán en su frecuencia real.
- Soporte de listas `.m3u` y `.pls`, y aviso de "sin transmisión online".

## [v0.13.0] - 2026-10-26 — build 13 (`52b29e7`, incluye `661eece`)

### Added
- Ruta alineada con el auto y línea central animada según la velocidad del GPS.
- Paneles movibles y escalables (mantener apretado el fondo); se guardan y se pueden restablecer.
- Diagnóstico ampliado: cuadros por segundo, memoria, RAM, resolución, Android, equipo y versión.
- Botón para usar o quitar la app como pantalla de inicio.

### Changed
- La compilación pasa a ser release optimizada (R8 y reducción de recursos), con firma fija. El APK baja de ~8,9 MB a ~2,9 MB.
- El número de versión sale del número de build (`versionName 0.<build>`).

### Fixed
- Las capturas automáticas se quedaban trabadas por la animación de la ruta (el build 12 se canceló). Se apagan las animaciones en las capturas y el paso tiene un límite de 15 minutos.

### Technical
- Clave de firma guardada en el repo privado (ver AUD-001 en `AUDIT_REPORT.md`).

## [v0.12.0] - 2026-10-26 — build 12 (`661eece`) — no publicada

Compilación cancelada: las capturas automáticas se quedaron trabadas. Su contenido salió en v0.13.0.

## [v0.11.0] - 2026-10-26 — build 11 (`198ee17`)

### Fixed
- La tarjeta del clima de hoy ya no corta el texto.

## [v0.10.0] - 2026-10-26 — build 10 (`930e528`)

### Added
- Pantalla de radios por internet (La 100 y radios de Tucumán) con reproducción en segundo plano (Media3).
- Botón Radios en el dock.

## [v0.9.0] - 2026-10-26 — build 9 (`683ed6d`)

### Added
- Llanura y ruta bajo el auto, con luz de faros sobre el asfalto.

### Fixed
- Textos del encabezado legibles sobre el fondo claro (velo y sombra).

## [v0.8.0] - 2026-10-26 — build 8 (`6773a98`)

### Changed
- Estado corto del clima en la tarjeta de hoy; tablero sin líneas sobrantes.

## [v0.7.0] - 2026-10-26 — build 7 (`a20bc4f`)

### Added
- Pantalla Tablero (deslizando): velocímetro y tacómetro grandes, música, auto y tarjetas de motor.
- Clima con pronóstico de 4 días, sensación térmica, humedad, viento y probabilidad de lluvia.

## [v0.6.0] - 2026-10-26 — build 6 (`2647978`)

### Changed
- Foto del auto sin reflejos de árboles: vidrios polarizados y capó liso.

## [v0.5.0] - 2026-10-26 — build 5 (`98935f4`)

### Added
- Panel de motor con medidores enfrentados (refrigerante, combustible, RPM, batería).

### Changed
- Faros encendidos desde adentro de la óptica.

## [v0.4.0] - 2026-10-26 — build 4 (`0db60d0`)

### Changed
- Auto más grande, sombra en el piso y tono nocturno más oscuro.

## [v0.3.0] - 2026-10-26 — build 3 (`b860694`)

### Added
- Emblema de Chevrolet en el encabezado.

## [v0.2.0] - 2026-10-25 — build 2 (`1397643`)

### Added
- Foto real del Onix con patente AAA y capa de faros.
- Fondos fotográficos según la hora y el clima (día, atardecer, noche, lluvia).
- Clima en vivo (Open-Meteo) y lluvia animada.

### Tests
- Primeras capturas reales de la pantalla en la CI (Robolectric + Roborazzi).

## [v0.1.0] - 2026-10-25 — build 1 (`4d067d2`)

### Added
- Primera versión: hora y fecha reales, fondo según la hora, música de la sesión activa, velocidad GPS, accesos a Maps, Waze, Spotify, teléfono y ajustes, diagnóstico.
- Compilación automática en GitHub Actions y publicación del APK en Releases.
