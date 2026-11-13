# Head unit — compatibilidad, instalación y recuperación

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González

**Actualizado:** 2026-10-26 · v0.21.0
**Equipo objetivo:** radio Android genérica (JYT/K20N según la info del equipo), Android 7.1, pantalla táctil horizontal de 1024 × 600.
**Estado de las pruebas en la radio:** ninguna ejecutada todavía. Todo lo que dice "verificado" acá se verificó en el código o en la CI, no en el equipo.

---

## 1. Tecnología real del proyecto (inspeccionada)

| Ítem | Valor | Dónde |
|---|---|---|
| Lenguaje / UI | Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01) | `build.gradle.kts`, `app/build.gradle.kts` |
| minSdk / targetSdk / compileSdk | **25** / 34 / 37 | `app/build.gradle.kts` |
| Build | AGP 9.3.0, Gradle 9.5.0 (wrapper), JDK 21 | `gradle/wrapper/`, CI |
| Dependencias con código nativo | Ninguna | — |
| Módulos | 1 (`:app`) | `settings.gradle.kts` |

minSdk 25 (Android 7.1, desde v0.55.0, lo mínimo de CarLink: ADR-0012) es justo la versión del head unit.

## 2. Compatibilidad con Android 7.1

### Verificado en el código

| Elemento | API mínima | Estado |
|---|---|---|
| Compose, Activity Compose, Core KTX | 21 | Compatible |
| Media3 ExoPlayer / Session 1.3.1 | 21 | Compatible |
| MapLibre 10.3.7 (mapa 3D) | 21 | Compatible: usa OpenGL ES 2.0, el de la radio (MapLibre 11 pide 3.0). Si no carga, se usa el mapa clásico |
| `network_security_config` | 24 | Compatible |
| `MediaSessionManager.addOnActiveSessionsChangedListener`, `MediaController.Callback` | 21 | Compatible |
| `LocationListener` (los 4 métodos implementados para API 24) | 1 | Compatible |
| `Settings.ACTION_HOME_SETTINGS` | 21 | Compatible |
| `POST_NOTIFICATIONS` | 33 | Protegido con `SDK_INT >= 33`; en 7.1 no se pide |
| `BLUETOOTH` (OBD2) | 1 | Declarado hasta API 30; en 7.1 se concede al instalar |
| `ACCESS_NETWORK_STATE` (vuelta de la red) | 1 | Permiso normal, se concede al instalar; `registerDefaultNetworkCallback` es de API 24 |
| Conscrypt 2.5.2 (TLS 1.3) | 9 | Trae su propio código nativo (incluye armeabi-v7a); solo se usa con `SDK_INT < 29` |
| `WRITE_EXTERNAL_STORAGE` (informe en Descargas) | 23 (pedido en uso) | Declarado hasta API 28; en 7.1 se pide la primera vez que se toca Prueba completa. Desde Android 10 se usa `MediaStore.Downloads` (API 29, protegido con `SDK_INT >= 29`) |
| `BLUETOOTH_ADMIN` (OBD2, v0.73.0) | 1 | Declarado hasta API 30: prender el Bluetooth si está apagado y cortar una búsqueda de equipos antes de conectar (el reinicio automático se quitó en v0.81.13); en 7.1 se concede al instalar |
| `BLUETOOTH_CONNECT` (OBD2) | 31 | Se pide solo con `SDK_INT >= 31`; en 7.1 no existe |
| `REQUEST_INSTALL_PACKAGES` (Actualizaciones, v0.81.0) | 23 | En Android 7.1 se usa el ajuste general de "orígenes desconocidos"; desde Android 8 (`SDK_INT >= 26`) se verifica `canRequestPackageInstalls()` antes de abrir el instalador |
| `WAKE_LOCK` (radio con pantalla apagada) | 1 | Compatible |
| Media3 HLS (`.m3u8`) y `MediaSessionService.addSession` | 21 | Compatible |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `foregroundServiceType` | 34 | Se ignoran en 7.1 (no hacen falta) |
| `java.time`, `Optional`, streams | 26/24 | No se usan |

### Permisos del motor de Android Auto y CarPlay (v0.56.0)

El motor de CarLink (ADR-0012) suma sus permisos al manifiesto final: Bluetooth (`BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`), Wi-Fi y red (`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_LOCAL_NETWORK`), `ACCESS_COARSE_LOCATION`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `READ_PHONE_STATE`, `MANAGE_OWN_CALLS`, servicios en primer plano (`FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_LOCATION`), `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`, `BROADCAST_STICKY`, `WRITE_SETTINGS`, `PACKAGE_USAGE_STATS`, `REQUEST_INSTALL_PACKAGES` y `ACCESS_SUPERUSER`. En Android 7.1 los peligrosos (micrófono, teléfono, ubicación) se piden al conectar. El USB no necesita permiso propio: Android pregunta al enchufar qué app abre el celular.

**Android Auto / CarPlay inalámbrico (2026-11-04):** el autor cuenta que la radio parece tener Wi-Fi Direct pero no funciona. Además, la conexión inalámbrica apaga el hotspot del celular, que es el internet de la radio (CarLink lo marca como problema). Decisión: **solo por cable**; no se activa el modo inalámbrico.

Desde v0.74.0 (CarLink 1.0.9) DiPlay suma además un permiso propio con nivel `signature`, `<applicationId>.USB_PERMISSION_RESULT`, que protege la respuesta del permiso de USB en Android 12 o anteriores; solo lo puede usar la misma app.

### Verificación automática

Desde v0.18.0 la CI corre **Android Lint** y publica `lint.txt` en cada release, con los avisos `NewApi` / `InlinedApi` (APIs más nuevas que Android 7). El paso no bloquea la release.

**Resultado (build 20):** 0 avisos `NewApi` / `InlinedApi`. Otros 11 avisos: 1 error `CoarseFineLocation` (HU-008), 5 `GradleDependency` (hay versiones más nuevas; no se actualizan sin verificar compatibilidad), y 1 de cada uno: `OldTargetApi`, `DiscouragedApi`, `ExportedService` (= AUD-004), `InsecureBaseConfiguration` (HTTP para el audio, riesgo aceptado en AUD-002) y `DataExtractionRules`.

### Pendiente (requiere la radio)

- Que el equipo tenga la pantalla de **acceso a notificaciones**. Algunos firmwares genéricos la quitan; sin ella la tarjeta de música no puede leer Spotify.
- Que el equipo permita elegir otra **pantalla de inicio** (`ACTION_HOME_SETTINGS`). Algunos firmwares fuerzan su propio launcher.
- La densidad real que reporta la pantalla (Diagnóstico → Pantalla).
- El GPS interno: que dé velocidad (`hasSpeed`).
- Funciones propietarias (cámara de retroceso, luces, CAN, volante): **no se usan ni se suponen**.

## 3. Interfaz en 1024 × 600

La interfaz se dibuja en un lienzo fijo de 1024 × 600 dp y se escala para ocupar la pantalla, así que **en 1024 × 600 px ocupa toda la pantalla con cualquier densidad** y en otras resoluciones mantiene la proporción con bordes negros. Las capturas de la CI se generan exactamente en 1024 × 600 (`w1024dp-h600dp`, mdpi).

| Control | Estado |
|---|---|
| Orientación | `sensorLandscape`; cambios de configuración manejados sin recrear la Activity |
| Pantalla completa | Barras del sistema ocultas; se muestran deslizando desde el borde |
| Botones del dock | 56 × 56 dp, separados; tamaño adecuado para tocar manejando |
| Textos chicos | Hay etiquetas de 10 a 12 sp en el panel de motor (números de los relojes, HU-004) |
| Estados sin datos | Sin GPS "--" / "SIN GPS"; sin clima "Clima sin datos"; sin música "Nada sonando"; app no instalada → aviso |
| Animaciones | Ruta (solo en movimiento), lluvia (solo con lluvia), transición de fondo, velocímetro (0,45 s al cambiar la velocidad) |

## 4. Compilación

Requisitos: JDK 21 y Android SDK 37 (con NDK y CMake para los motores de Android Auto y CarPlay). En la práctica todas las compilaciones se hacen en GitHub Actions.

```bash
./gradlew :app:assembleRelease
```

| Salida | Ubicación |
|---|---|
| APK de la CI | Release `build-N` → `onix-launcher.apk` |
| APK local | `app/build/outputs/apk/release/app-release.apk` |
| Resultados | `tests.txt` y `lint.txt` en cada release |

## 5. Instalación, actualización, desinstalación y recuperación

### Instalar
1. Descargar `onix-launcher.apk` de la release más reciente (desde el celular o un pendrive).
2. Abrirlo en la radio y permitir "orígenes desconocidos" si lo pide.
3. Abrir la app, aceptar ubicación y tocar **Dar acceso** (música).

### Usar como pantalla de inicio
Ajustes → Sistema → **Usar como inicio** → elegir Onix Launcher y "Siempre". La fila muestra el resultado (v0.81.11): "Onix es la pantalla de inicio", "Falta elegir" o "La radio abre <paquete> con el botón Inicio"; lo mismo sale en Diagnóstico → Pantalla de inicio y en la Prueba completa. Si después de elegir sigue figurando otro paquete, el firmware de la radio fuerza su propio launcher.

### Actualizar
Instalar el APK nuevo encima. Se conservan los paneles y el modo de radio. Si dice "conflicto con un paquete existente": venís de v0.12.0 o anterior, o cambió la clave de firma → desinstalar una vez.

### Volver al launcher original (recuperación)
- Desde la app: Ajustes → Sistema → **Quitar**; la próxima vez que toques Inicio, Android pregunta cuál usar.
- Si la app no abre o se cierra sola: Ajustes de Android → Aplicaciones → Onix Launcher → **Borrar valores predeterminados** (o **Forzar detención**). Si no se llega a Ajustes, mantener el botón Inicio o usar el acceso a Ajustes del menú del fabricante.
- Último recurso: desinstalar desde Ajustes → Aplicaciones → Onix Launcher → **Desinstalar**. El launcher del fabricante no se toca nunca, así que siempre queda disponible.

### Reiniciar la configuración de la app
Mantener apretado el fondo → **Restablecer** (paneles). Para todo lo demás: Ajustes → Aplicaciones → Onix Launcher → Almacenamiento → **Borrar datos**.

### Versiones anteriores
Cada release `build-N` queda publicada; para volver atrás, instalar un APK anterior (desde v0.13.0 tienen la misma firma; Android solo permite bajar de versión desinstalando primero).

## 6. Diferencias conocidas: celular / capturas de la CI / radio

| Aspecto | Celular | Capturas (Robolectric) | Radio |
|---|---|---|---|
| Resolución | Variable, con bordes | 1024 × 600 exactos | 1024 × 600 (a confirmar densidad) |
| Animaciones | Sí | Apagadas | Sí; rendimiento sin medir |
| GPS / clima / música | Reales | Valores de ejemplo | Reales, sin probar |
| Fluidez y memoria | No representativas | No aplica | Sin medir (HU-003) |
| Pantalla de inicio | Probable | No aplica | Sin probar |

## 7. Radio en la head unit

La radio usa la notificación de medios y el servicio en primer plano de Android; en firmwares genéricos conviene verificar (R-17) que siga sonando al abrir Maps o Waze y que los controles del volante la manejen. Las radios que no responden se ocultan tras una revisión en el equipo (necesita internet).

## 8. Problemas específicos del firmware

Verificados en la radio (registro `logcat` por ADB sobre WiFi, 2026-11-11):

- **El Bluetooth es un módulo externo.** `BluetoothSocket` está reemplazado (etiqueta `goc`) y habla con `/system/bin/gocsdk_xr829` (etiqueta `nwdbt`; servicio `init.svc.gocsdk_xr829`, con un proceso padre que lo vuelve a levantar si se cae). `connect()` vuelve al instante aunque no haya enlace con el equipo; el enlace real aparece como `spp_socket_on_data spp Connected <dirección>`.
- **Los lugares de conexión no se liberan.** Cada socket abierto ocupa un `client_fd` del programa, que no lo suelta al cerrarlo ni al morir la app. Agotados, contesta `spp_socket error:all clients are connected` y ninguna app conecta hasta que el programa se reinicia. Por eso la app abre un solo socket por intento y reintenta espaciado (v0.81.14).
- **Launcher protegido.** `persist.nwd.launcher.default` decide a qué launcher vuelve la radio. Se cambió a `com.axel.onixlauncher` con `setprop` desde una consola con permisos de administrador; se deshace poniendo `com.android.launcher`. Falta confirmar que se mantiene al apagar y prender.

## 9. Hallazgos del control técnico (2026-10-26)

| ID | Severidad | Hallazgo | Evidencia | Propuesta | Estado |
|---|---|---|---|---|---|
| HU-001 | MEDIUM | La clave de firma está en Git (mismo hallazgo que AUD-001) | `app/release.jks`, `app/keystore.properties` | Pasarla a GitHub Secrets desde la web y quitarla del repo | Abierto, requiere tu acción |
| HU-002 | LOW | Como pantalla de inicio, el botón Atrás cierra el launcher y Android lo vuelve a abrir (parpadeo) | `MainActivity.kt` no maneja Atrás | Ignorar Atrás en la pantalla principal | Propuesto, sin aplicar |
| HU-009 | MEDIUM | Luces de la app según las luces del auto | `Lights.kt` | Con el "sensor de iluminación" prendido: `high_beam_state` = 1 y `mcu_no_source_device_state` 0 → 2 con luces (28/10 11:41), pero la radio baja el brillo. CleanLauncher (mismo equipo NWD) usa el aviso `com.nwd.ACTION_ILL_STATE_CHANGE` y el bit 2 de `mcu_no_source_device_state`; v0.49.0 escucha las tres vías. Con el sensor APAGADO funciona (R-38, confirmado por el autor el 2026-11-03) | Resuelto (v0.49.0) |
| HU-011 | MEDIUM | La radio no tiene motor de voz (TextToSpeech): la navegación no habla (Prueba completa, v0.41.0) | `Navigation.kt` | Instalar "Servicios de voz de Google" desde Play Store y elegir español; el botón del mapa dice "Sin voz" mientras falte | Requiere al autor |
| HU-012 | LOW | El `Geocoder` de la radio no tiene servicio ("Service not Available") | `Location.kt` | Resuelto en v0.42.0: el nombre del lugar sale de OpenStreetMap (Nominatim / Photon) | Resuelto |
| HU-013 | MEDIUM | OSRM y FOSSGIS fallan en la conexión segura en la radio (Android 7.1), también con Conscrypt TLS 1.3 (v0.44.0: "Read error … protocol error"); las demás conexiones HTTPS andan. El celular sí obtiene la ruta de OSRM (28/10 11:58): el servidor anda. v0.46.0 agrega a la Prueba completa una prueba TLS por cada IP (IPv4/IPv6, TLS de la app y del sistema) | `network_security_config.xml`, `MapNav.kt`, `Net.kt` | v0.42.0: raíces ISRG y respaldos (Nominatim y Photon ya andan; las rutas siguieron fallando, no era el certificado). v0.43.0: Conscrypt (TLS 1.3 y curvas modernas) para todas las conexiones HTTPS. Prueba completa 04/11 14:59: el saludo TLS 1.3 de la app anda con los dos servidores, falla el pedido por `HttpURLConnection` (su OkHttp interno deja el socket en TLS 1.2). v0.71.0: rutas por TLS 1.3 directo (`DirectHttps`). v0.75.0: rutas y búsqueda con TomTom (ADR-0014), sin OSRM | Por confirmar (Prueba completa con v0.75.0) |
| HU-014 | HIGH | La radio NWD cierra apps al apagar el contacto o con poca memoria (`/nwdappconfig/app/OneClearConfig.xml`, `BackgroundManager.ini`). Confirmado 31/10: Spotify y Onix no están en esas listas; la consola de Bugjaeger es root y los archivos se pueden escribir | — | Aplicado por el autor el 05/11 (copia, las tres listas y reinicio; `docs/RADIO_NWD.md`). Al apagar y prender el contacto, `pidof` muestra Spotify (12360) y Onix (5779) abiertos | **Resuelto** |
| HU-010 | HIGH | OBD2 con ELM327: probado en el auto (Prueba completa 28/10, lector "OBDII" ELM327 v2.1, protocolo ISO 15765-4 CAN 11/500, motor 87 °C, nafta 23 %, 12,4 V) | `Obd.kt` | Seguir probando el arranque (R-32) | Funciona |
| HU-003 | MEDIUM | Animaciones continuas (lluvia; la ruta se quitó en v0.27.0; el emblema animado redibujaba todo el tiempo hasta v0.35.1) sin medir en la radio; el autor nota la radio lenta | `HomeScreen.kt` | Medir con Diagnóstico → Fluidez; si baja de ~30 cuadros/s, simplificarlas | Requiere la radio |
| HU-004 | LOW | Etiquetas de 9 a 11 sp, chicas para leer manejando | 7 textos en `EnginePanel.kt`, `DashboardScreen.kt`, `HomeScreen.kt` | Subir a 12–13 sp después de verlo en la radio | Requiere la radio |
| HU-005 | LOW | Paneles guardados con valores inválidos (NaN, escala fuera de rango) no se corrigen al leerlos | `PanelLayout.kt` `LayoutStore.get` | Limitar posición y escala al cargar | Propuesto, sin aplicar |
| HU-006 | INFO | Sin el acceso a notificaciones, la música no funciona y no hay alternativa | `Media.kt` | Si la radio no tiene esa pantalla, evaluar otra vía | Requiere la radio |
| HU-007 | INFO | Tiempo de inicio no medido | — | Medir en la radio | Requiere la radio |
| HU-008 | LOW | Lint: se pide ubicación precisa sin declarar la aproximada; en Android 12+ el usuario puede elegir solo la aproximada. No afecta a Android 7.1 | `lint.txt` build 20, `AndroidManifest.xml` | Declarar también `ACCESS_COARSE_LOCATION` | Propuesto, sin aplicar |

Ningún hallazgo bloquea instalar y probar en la radio.
