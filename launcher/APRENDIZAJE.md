# Registro de Aprendizaje — Onix Launcher

---

## Sesión 1 — 2026-11-08: Conmutación de fuente de audio (MCU) e Hilo Principal en Android

### 1. Conceptos que aprendí hoy
- **Arquitectura de doble cerebro en estéreos Android (MCU vs. Placa Android):**
  La placa donde corre Android (y apps como Spotify) está separada del microcontrolador físico (**MCU**) del estéreo NWD, que maneja la llave física de sonido entre Radio FM (`SOURCE_RADIO = 4`) y Android (`SOURCE_ANDROID = -128`).
- **Estado colgado en hardware y "Toggle" (conmutación ida y vuelta):**
  Cuando el auto vuelve del *sleep* (sueño), el MCU cree que ya está en la fuente `-128` (`SOURCE_ANDROID`) e ignora un nuevo pedido a `-128`. Forzar un cambio breve a `SOURCE_RADIO (4)` y volver a `SOURCE_ANDROID (-128)` obliga al hardware a mover la llave sin llegar a despertar el ruido de la FM (que recién se activa a los `800 ms` en `wake()`).
- **El Hilo Principal (*Main Thread* / *UI Thread*) y por qué no usar `sleep`:**
  A diferencia de un script simple en Python donde `time.sleep()` detiene todo sin consecuencias visuales, en una app con interfaz gráfica hay un único "mozo" (*Main Thread*) que dibuja la pantalla y atiende toques. Frenarlo congela la app y provoca el error *Application Not Responding (ANR)*.
- **Programación asíncrona con `Handler.postDelayed` y Lambdas (`{ ... }`):**
  `postDelayed({ accion() }, milisegundos)` agenda una tarea para el futuro y deja que el código siga de largo inmediatamente (por ejemplo, imprimiendo `A, C, B` cuando `B` tiene demora).
- **Condición de Carrera (*Race Condition*):**
  Si dos acciones dependen una de otra en el tiempo (como cambiar la fuente de hardware a los `500 ms` y darle Play a la música con `LastAudio.resume(c)`), disparar la segunda al instante (`0 ms`) falla porque el hardware todavía no terminó de conmutar. Coordinar la línea de tiempo (`500 ms` → `600 ms`) evita la condición de carrera.

### 2. Decisiones que tomamos y por qué
- **Crear `NwdTuner.bounceToAndroid(context)` en `NwdTuner.kt`:**
  En vez de pedir directamente `SOURCE_ANDROID` al dar contacto (que el MCU ignoraba), pedimos `SOURCE_RADIO` en `0 ms` y agendamos `SOURCE_ANDROID` a los `500 ms` (antes de los `800 ms` en que `select()` despertaría el chip FM), automatizando lo que antes había que hacer a mano tocando "Modo Antena".
- **Escalonar `LastAudio.resume(c)` a `600 ms` en `IgnitionAudio.restore` (`Media.kt`):**
  Para garantizar que el comando de Play llegue `100 ms` después de que la fuente de audio ya volvió a Android (`500 ms`), reutilizando una sola instancia de `Handler`.

### 3. Qué practicar después
- Reemplazar "números mágicos" (`500L`, `600L`) por constantes con nombre descriptivo (`const val BOUNCE_MS = 500L`, `const val RESUME_DELAY_MS = 600L`).
- Escribir un test unitario en `IgnitionAudioTest.kt` para verificar el comportamiento ante distintos estados de inicio.
- Cómo funcionan las **Corrutinas de Kotlin** (`suspend`, `delay`) como evolución moderna de `Handler.postDelayed`.

---

## Sesión 2 — 2026-11-08: Precalentamiento de procesos (`:aa`), Re-enumeración USB y Interpolación en Kotlin

### 1. Conceptos que aprendí hoy
- **Procesos múltiples en Android (`:aa` vs. proceso principal):**
  Android Auto (`DiAuto`) corre en un proceso de sistema operativo separado llamado `:aa` (que tarda 4,5–6 segundos en arrancar en frío en una radio de gama baja), mientras que CarPlay (`DiPlay`) corre dentro del mismo proceso principal de Onix.
- **Precalentamiento (*Prewarming*) y políticas por memoria RAM (`EnginePrewarmPolicy`):**
  Para ahorrar esos ~5 segundos al enchufar el cable USB, `Connection.prewarm` despierta el proceso `:aa` a los 2,5 segundos de abrir el launcher. En equipos con poca RAM (`<= 3 GB`), solo lo hace si `KnownPhones` tiene registrado un teléfono `ANDROID`.
- **Por qué CarPlay demora unos segundos por cable (Re-enumeración USB de Apple):**
  Al enchufar un iPhone, primero aparece como cámara/iPod; el estéreo le envía un comando USB para cambiar de configuración (`SelectingConfiguration`), el iPhone se desconecta virtualmente del bus USB y vuelve a aparecer ~2 segundos después como placa de red (`WaitingForReenumeration` → `NCM`).
- **Interpolación de Strings (`"${...}"`) y Operador Elvis (`?:`) en Kotlin:**
  Equivalen a los `f-strings` (`f"{...}"`) y al operador `or` por defecto en Python: `"usb:${phoneName ?: kind.name}"` evalúa `phoneName` y, si es `null`, usa `kind.name` como respaldo.

### 2. Decisiones que tomamos y por qué
- **Estrategia de commits separados vs. una sola prueba en el auto:**
  Mantener cada problema aislado conceptualmente para tener control si algo falla, pero agrupar la subida a la CI cuando tengamos listos los cambios para ir al auto una sola vez.
- **Registrar teléfonos USB dinámicamente en `SessionManager.onUsbPhone` (`SessionManager.kt`):**
  Como el módulo Bluetooth de la radio NWD está separado por hardware y se conecta por USB, `KnownPhones` (que antes solo guardaba direcciones MAC de Bluetooth) quedaba vacío y desactivaba el `prewarm` de Android Auto. Ahora guardamos `"usb:${phoneName ?: kind.name}"` al enchufar por USB para que recuerde el S25 Ultra (o cualquier celular que se conecte) y precaliente `:aa` en cada arranque.

### 3. Qué practicar después
- Diferenciar cuándo un problema ocurre en el **estéreo** (host de proyección) vs. dentro del **celular** (como el corte de buffer de TuneIn al perder 4G en ruta).
- Documentar los cambios en módulos de terceros (`carlink-core/`) dentro de `vendor/ONIX_CHANGES.md` según las reglas del proyecto.

---

## Sesión 3 — 2026-11-08: Reconexión de audio en Android Auto tras corte de red e Idempotencia

### 1. Conceptos que aprendí hoy
- **Puente de teclas de medios (`MediaSession` y `AudioManager.dispatchMediaKeyEvent`):**
  Cuando Android Auto (`AapService`) o CarPlay están activos, registran una `MediaSession` en el estéreo que escucha teclas físicas o de sistema (`KEYCODE_MEDIA_PLAY`, `PAUSE`, `NEXT`) y las reenvía por el cable USB a la app que esté sonando en el teléfono (como TuneIn).
- **Operaciones Idempotentes (`KEYCODE_MEDIA_PLAY` vs. `KEYCODE_MEDIA_PLAY_PAUSE`):**
  Una acción es *idempotente* cuando aplicarla una o varias veces produce siempre el mismo estado deseado sin efectos secundarios indeseados. Enviar `KEYCODE_MEDIA_PLAY` al volver la red reanuda TuneIn si se había detenido por falta de buffer, y si seguía sonando no la corta (mientras que `PLAY_PAUSE` la pausaría por error).

### 2. Decisiones que tomamos y por qué
- **Enviar `KEYCODE_MEDIA_PLAY` en `LastAudio.resumeRadioAfterNetwork` (`Media.kt`) cuando `Projection.sessionActive(c)` es `true`:**
  Antes, al volver el Wi-Fi/Hotspot tras un corte en ruta, `resumeRadioAfterNetwork` salía con `return` sin hacer nada si Android Auto/CarPlay estaba activo, dejando a TuneIn detenido esperando un Play manual. Ahora despacha `KEYCODE_MEDIA_PLAY` a través de `mediaKey` antes de retornar.

### 3. Qué practicar después
- Trazar el flujo de eventos entre componentes (`NetWatch` → `LastAudio` → `AudioManager` → `AapService` → USB).
- Verificar con la suite de tests y el script de documentación (`check_docs.py`) antes de publicar una nueva versión en Git.


---

## Sesión 4 — 2026-11-08: Reconexión confiable del lector OBD2 ELM327 al dar contacto (`Obd.kt`)

### Qué construimos
- En [`ElmLink.send`](./app/src/main/java/com/axel/onixlauncher/Obd.kt#L336-L363): agregamos la bandera `@Volatile var answered: Boolean` que registra si el lector envió al menos un byte (incluido el prompt `'>'`) antes de que venciera el tiempo máximo.
- En [`ObdRecovery.connectVerified`](./app/src/main/java/com/axel/onixlauncher/Obd.kt#L190-L220):
  1. Subimos el tiempo de espera de `"ATZ"` de `3_000` a `5_000` ms para que los clones ELM327 lentos terminen de reiniciarse sin desfasar las respuestas siguientes.
  2. Exigimos `if (!opened.answered) throw java.io.IOException("el lector no contestó")` para descartar canales Bluetooth RFCOMM "mudos" (que dejaban escribir en el buffer de Android pero nunca respondían) y pasar de inmediato a la siguiente manera de conectar.
  3. Agregamos `wayOrder(count, preferred)` y `preferredWay` para probar primero la última manera de conectar (1, 2 o 3) que funcionó, evitando perder hasta 15 segundos en cada contacto.
- En [`initElm`](./app/src/main/java/com/axel/onixlauncher/Obd.kt#L489-L495): quitamos el segundo `"ATZ"` redundante (ya enviado por `connectVerified`) para no reiniciar el chip dos veces seguidas.
- En [`rememberObd` / `ObdRecovery.shouldDropActiveLink`](./app/src/main/java/com/axel/onixlauncher/Obd.kt#L144-L148): cuando llega el aviso de contacto (`ACTION_MCU_STATE_CHANGE` estado `1`) o un corte Bluetooth del propio lector (`ACTION_ACL_DISCONNECTED`), cerramos en el acto el socket `activeLink` viejo sin esperar los 20 s del vigía, pero sin cortar una conexión sana si solo se apaga y prende la pantalla (`ACTION_SCREEN_ON`).

### Conceptos nuevos que aparecieron
1. **Diferencia entre "escribir en un socket" y "recibir respuesta del dispositivo":** `output.write()` solo deposita bytes en el buffer local del sistema operativo; recién cuando `input.read()` devuelve bytes reales (`answered = true`) sabemos que el canal RFCOMM llegó al chip ELM327.
2. **Idempotencia y tiempos de reinicio en hardware embebido (`ATZ`):** Mandar dos comandos de reset (`ATZ`) seguidos a un microcontrolador lento hace que la respuesta tardía del primer reset se mezcle con los comandos de configuración (`ATE0`, `0100`) del segundo.
3. **Invalidación selectiva de conexiones (`shouldDropActiveLink` vs `isWakeSignal`):** Distinguir entre un evento que solo debe despertar una espera (`SCREEN_ON`) y uno que invalida el socket físico anterior (`MCU_ACTION == 1` o `ACL_DISCONNECTED`).

### Qué me costó o dónde dudé
- Cómo distinguir en `ElmLink.send` entre una respuesta vacía porque el lector solo devolvió el prompt `'>'` (que `send` recorta) y una respuesta vacía porque el socket quedó totalmente mudo.

### Próximo paso
- Empaquetar los cambios en la versión `v0.79.18`, validar toda la documentación con `check_docs.py` y subir a `main`.


---

## Sesión 5 — 2026-11-08: Spotify con portadas, búsqueda en vivo, letras sincronizadas y mapa expandible (`Spotify.kt`, `NavMusicScreen.kt`, `EnginePanel.kt`, `RadiosScreen.kt`, `MapNav.kt`)

### Qué construimos
- **Bloque 1 — Portadas y buscador en vivo de Spotify ([`Spotify.kt`](./app/src/main/java/com/axel/onixlauncher/Spotify.kt), [`NavMusicScreen.kt`](./app/src/main/java/com/axel/onixlauncher/NavMusicScreen.kt)):**
  - Agregamos `imageUrl` a `SpotifyItem` y `SpotifyCovers` (caché `LruCache` en memoria) para mostrar las tapas de listas, temas recientes y resultados de búsqueda.
  - Implementamos búsqueda en vivo con *debounce* de `350 ms` (`LaunchedEffect(tab, query)`) sin necesidad de apretar Enter, más chips de búsquedas recientes (`SpotifyApi.recentQueries`) y sugerencia automática de lo último escuchado en la cuenta (`SpotifyApi.recent`) al abrir la pestaña **Buscar**.
- **Bloque 2 — Letras sincronizadas en vivo ([`Spotify.kt`](./app/src/main/java/com/axel/onixlauncher/Spotify.kt), [`NavMusicScreen.kt`](./app/src/main/java/com/axel/onixlauncher/NavMusicScreen.kt)):**
  - Creamos `Lyrics` (`parseLrc`, `parseResponse`, `activeLineIndex`, `fetch` contra `lrclib.net`) y la 4ª pestaña **"Letra"** (`LyricsPanel`) sincronizada con el milisegundo actual del tema (`rememberPlaybackPosition`).
- **Bloque 3 — Reproductor minimizable y pulido visual ([`NavMusicScreen.kt`](./app/src/main/java/com/axel/onixlauncher/NavMusicScreen.kt), [`EnginePanel.kt`](./app/src/main/java/com/axel/onixlauncher/EnginePanel.kt), [`RadiosScreen.kt`](./app/src/main/java/com/axel/onixlauncher/RadiosScreen.kt), [`MapNav.kt`](./app/src/main/java/com/axel/onixlauncher/MapNav.kt)):**
  - Agregamos `musicCollapsed` en `NavMusicScreen`: al tocar `"❯"`, el reproductor se minimiza a una píldora en la barra superior (`▶/❚❚` + `"❮ Música"`) y el mapa se estira de `604 dp` a `984 dp` (ancho completo).
  - Ajustamos `labelStep = 40f` en el velocímetro (`EnginePanel.kt`), pusimos una cápsula oscura detrás del nombre de la emisora sobre la aguja en `RadiosScreen.kt` y alineamos los 4 chips de categorías a `140.dp` en `MapNav.kt`.

### Conceptos nuevos que aparecieron
1. **Debounce con corrutinas (`LaunchedEffect(key)` + `delay`):** Cómo cancelar automáticamente pedidos de búsqueda intermedios mientras el usuario sigue escribiendo.
2. **Parseo de formatos de tiempo (`.LRC`) y búsqueda por rango (`indexOfLast`):** Transformar `[mm:ss.xx]` en milisegundos para sincronizar texto con el reloj de reproducción.
3. **Layouts adaptativos por estado en Compose:** Cambiar dinámicamente el ancho de un panel (`if (musicCollapsed) 984 else 604`) conservando el estado interno del mapa.

### Qué me costó o dónde dudé
- Cómo evitar que la búsqueda en vivo dispare una llamada HTTP por cada tecla presionada y cómo sincronizar cada línea de la letra con el progreso de la canción.

### Próximo paso
- Publicar la versión `v0.79.19` en `main` con toda su documentación actualizada.


---

## Sesión 6 — 2026-11-08 — Forense de logs reales del auto: chip de radio dormido (`sin usar`), rescate de Spotify en pausa y bloqueo JNI en `BluetoothSocket` (`v0.79.20`)

### Qué construimos
- En `NwdTuner.kt` (`bounceToAndroid`): cambiamos el rebote superficial de 500 ms por una activación real de `com.nwd.radio.service` (`select(context)` + `wake(context)` + `transact(TX_SET_FREQUENCY)` en la frecuencia recordada o `95.5 MHz`) y recién a los `1_800 ms` devolvemos la fuente a `SOURCE_ANDROID (-128)` con `release(context)`.
- En `Media.kt` (`IgnitionAudio`):
  - Separamos el horario de `LastAudio.resume(c)` a `+2_500 ms` (para que no choque con el cierre del rebote de radio a `+1_800 ms`) y `kickSpotify(c)` a `+5_000 ms`.
  - Cambiamos `shouldKickSpotify` para que evalúe `lastAudio == "spotify"` aunque `spotifyPlaying` sea `false` (porque el cambio de fuente del MCU pausa la sesión).
  - En `needsForeground(valid, playing, sessionPlaying)`, exigimos también que `sessionPlaying` sea `true`; cuando está en `false` o el PID de Spotify no figura en `sys.nwd.playing.pids`, abrimos Spotify en primer plano, disparamos `spotifyPlay(c)` a los `+1_500 ms` con Spotify visible y volvemos a `MainActivity` a los `+4_000 ms`.
- En `Obd.kt` (`closeBluetoothSocket`, `ElmLink`, `ObdRecovery`):
  - Agregamos `closeBluetoothSocket(socket)` que usa reflexión sobre el campo privado `mSocket` (`android.net.LocalSocket`) de `BluetoothSocket` para invocar `shutdownInput()` y `shutdownOutput()` antes de `socket.close()`, destrabando cualquier hilo colgado en `output.write("ATZ\r")` dentro de JNI en Android 7.1.
  - Hicimos `ElmLink.close()` idempotente con `AtomicBoolean(false)` y sumamos el paso exacto (`paso: ${link?.step ?: "abriendo"}`) al registro de fallos de `connectVerified`.
- En `ExternalBluetoothProbe.kt` (`carlink-core`): desactivamos la sonda activa a `/dev/socket/zj_bt_socket` (`PortState.NOT_CHECKED`) para que al iniciar la app no le haga `shutdownInput`/`shutdownOutput`/`close` al daemon Bluetooth externo del estéreo NWD.

### Conceptos nuevos que aparecieron
- **Lectura forense de líneas de tiempo en logs de producción**: cruzar marcas de tiempo al segundo (`13:24:32` sonda BT, `13:24:56` arranque de contacto, `13:24:57` cambio de fuente y pérdida de `sonando=true`) para encontrar colisiones de concurrencia entre el hardware y nuestra app.
- **Bloqueo en llamadas nativas JNI (`LocalSocket.write`) en Android 7.1**: por qué `Future.get(timeout)` no mata un hilo bloqueado en el kernel esperando buffer RFCOMM, y cómo `shutdownInput()`/`shutdownOutput()` sobre el descriptor subyacente fuerza `EPIPE`/`EBADF` para liberar el canal antes del siguiente intento.

### Lo que Axel resolvió solo
- Trajo dos informes diagnósticos completos del auto (`onix_informe_2026-11-08_13-27.txt` y `13-28.txt`) con el estado exacto del hardware NWD en frío y después del arranque.

### Lo que costó o necesitó guía
- Conectar por qué `kickSpotify` nunca dejaba rastro en el log: la guarda `shouldKickSpotify` exigía `spotifyPlaying == true`, pero el propio rebote tardío del MCU a `+500 ms` pausaba a Spotify justo después del `play` de `+600 ms`.

### Próximo paso sugerido
- Probar la `v0.79.20` en el Onix y verificar en el próximo informe diagnóstico:
  1. Que al dar contacto aparezca la línea `Al encender: audio válido=..., sonando=..., sesion=...` en el log.
  2. Que el ELM327 responda `ATZ` sin quedar trabado los 8 segundos en escritura.


---

## Sesión 7 — 2026-11-08 — Menú de Ajustes por categorías, Paleta reactiva en Compose (Oscuro / Claro / Automático con cable ILL) y Registro de fallos (`v0.80.0`)

### Qué construimos
- **Menú de Ajustes propio (`SettingsScreen.kt`)**:
  - Tres categorías claras (**Apariencia**, **Sonido y avisos**, **Sistema**) dentro del lienzo de `1024×600 dp`, accesible desde el botón **Ajustes** del dock (que antes abría los Ajustes de Android y ahora cumple la Regla de Oro 2), desde el cajón de **Apps** y desde **Diagnóstico**.
- **Tema Oscuro / Claro / Automático y 6 colores de acento (`Ui.kt` / `ThemePrefs`)**:
  - En lugar de editar los 243 lugares donde se usan `Accent`, `TextMain`, `Muted`, `Track`, `CardBorder`, `GlassTop` y `GlassBottom`, transformamos esas constantes `val` en **propiedades con `get()`** que leen `mutableStateOf` dentro de `ThemePrefs`.
  - En modo **Automático** (`ThemeMode.AUTO`), `ThemePrefs.resolveLight` usa modo claro únicamente de día (`Ambient.DIA`) cuando las luces del auto (`lightsOn`, cable ILL) están apagadas; en cuanto prendés los faros o cae la tarde/noche, pasa a oscuro automáticamente.
  - Sumamos 6 colores de acento (**Azul**, **Ámbar**, **Verde**, **Rojo**, **Violeta** y **Celeste**) que actualizan toda la interfaz en vivo.
- **Registro de fallos (`CrashLog`)**:
  - En `OnixApp.onCreate()` instalamos un `UncaughtExceptionHandler` que formatea la excepción con fecha, hora y línea de código (`CrashLog.format`), la guarda con `.commit()` sincrónico en `SharedPreferences("fallos")` antes de que muera el proceso y la muestra en **Ajustes → Sistema**.

### Conceptos nuevos que aparecieron
- **Propiedades calculadas (`val X get() = ...`) + Estado Reactivo (*Snapshot State*) en Jetpack Compose**:
  En Python o Kotlin, una constante `val X = ...` se evalúa una sola vez al arrancar. En cambio, `val X get() = ThemePrefs.accent.value.color` ejecuta el `get()` cada vez que una función `@Composable` dibuja la pantalla, suscribiéndose automáticamente al `mutableStateOf`. Cuando cambiás el tema o el color de acento, Compose redibuja solo los componentes que leyeron esa propiedad.
- **`UncaughtExceptionHandler` y `.commit()` vs `.apply()`**:
  Normalmente guardamos preferencias con `.apply()` (asíncrono en memoria). Pero cuando la app está a punto de cerrarse por un crash, el proceso muere en milisegundos: si usáramos `.apply()`, Android mataría el proceso antes de escribir en disco. Por eso en un manejador de fallos siempre se usa `.commit()` (sincrónico).

### Lo que Axel propuso y decidió
- Analizó `StradaOS` y propuso traer la personalización de temas (oscuro/claro/automático), el menú de configuraciones ordenado y las actualizaciones desde la radio.

### Próximo paso sugerido
- Resolver cómo queremos alimentar el **actualizador en la radio (`T3`)** dado que el repositorio `AGonzalez-97/onix-launcher` es privado en GitHub (`AUD-067`).

---

## Sesión 8 — 2026-11-08: Actualizador integrado, Spotify (Recientes/Buscar y retorno de Wi-Fi), controles en píldora y ventana de arranque Bluetooth del OBD2 (v0.81.0)

### Qué construimos
1. **Actualizador en Ajustes → Sistema (`Updates` en `SettingsScreen.kt`):** opción para pegar una sola vez desde el portapapeles un token de solo lectura de GitHub (`SharedPreferences("actualizaciones")`, nunca en el código ni en los logs), consultar `/releases/latest`, descargar `onix-launcher.apk` siguiendo la redirección de `api.github.com` a `objects.githubusercontent.com` sin reenviar el encabezado `Authorization` al CDN firmado, verificar que el certificado de firma coincida con el de la app instalada (`GET_SIGNATURES`) y abrir el instalador de Android con `FileProvider` (`REQUEST_INSTALL_PACKAGES`).
2. **Canciones de Recientes y Buscar en Spotify (`Spotify.kt`):** `SpotifyItem` guarda el `contextUri` del álbum de cada canción y `SpotifyApi.playBody` arma `{"context_uri":"spotify:album:...","offset":{"uri":"spotify:track:..."}}` (el mismo esquema con contexto que sí funcionaba en las listas).
3. **Botones Anterior (`⏮`) y Siguiente (`⏭`) en el reproductor minimizado (`NavMusicScreen.kt`):** al contraer el panel de música para ver el mapa a `984 dp`, la píldora superior permite ahora retroceder y avanzar de tema o de emisora de radio sin desplegar el panel.
4. **Spotify al encender y cuando vuelve el Wi-Fi (`Media.kt`):** el informe `19:02` mostró que el estéreo tardó 69 s en conectar al Wi-Fi tras dar contacto (`18:57:33` → `18:58:42 · Red: volvió`). `LastAudio.resumeRadioAfterNetwork` retoma ahora también Spotify cuando vuelve la red, `spotifyPlay` manda siempre el broadcast `KEYCODE_MEDIA_PLAY` además de `transportControls.play()`, y `kickSpotify` refuerza el `play` a los `1500 ms` y `3200 ms`.
5. **OBD2 sincronizado con el reinicio Bluetooth de la radio NWD (`Obd.kt`):** el informe `19:02` reveló por qué el lector conectó a las `18:21:18` y `18:21:47` (siempre por la **manera 3**: `createRfcommSocket(1)`) pero se colgó a las `18:57:44`: al recibir `ACTION_MCU_STATE_CHANGE` `estado=1` (`18:57:33`), el estéreo NWD tarda ~12 s en terminar de reiniciar su chip Bluetooth (`18:57:45 rw.zlink.bt.name=Chevrolet`). Abrir sockets RFCOMM antes de ese momento hacía que `output.write("ATZ")` cayera justo en medio del reinicio del chip. Ahora `ObdRecovery` espera `WAKE_SETTLE_MS = 15_000L` tras el contacto, empieza siempre por `DEFAULT_PREFERRED_WAY = 2` (manera 3), pausa los intentos con el contacto apagado (`estado=0`) y cierra también el `ParcelFileDescriptor` (`mPfd`) del socket.

### Conceptos nuevos aprendidos
- **Por qué un CDN pre-firmado rechaza el encabezado `Authorization`:** cuando la API de GitHub (`api.github.com`) responde `302 Found` hacia `objects.githubusercontent.com` con un enlace firmado por query string, reenviar el `Authorization: Bearer ...` al segundo host provoca un error `400/403` porque el almacenamiento en la nube no acepta dos mecanismos de autenticación simultáneos.
- **Diferencia entre `uris` sueltos y `context_uri + offset` en Spotify Connect:** en varios clientes integrados de Spotify para auto, mandar una lista con `context_uri` reemplaza la cola y arranca enseguida, mientras que mandar un tema aislado con `{"uris":[...]}` sin contexto es ignorado; usar el álbum del tema como `context_uri` y el tema como `offset.uri` unifica ambos caminos.
- **Ventana de estabilización de hardware al despertar (`WAKE_SETTLE_MS`):** cuando un sistema embebido despierta de suspensión (`MCU_STATE = 1`), los buses periféricos (como el chip Bluetooth UART/SDIO de la radio NWD) pueden reiniciarse varios segundos después de que la CPU ya está corriendo; abrir un socket antes de que termine ese reinicio deja el descriptor de archivo colgado en el kernel.

### Qué me costó y cómo lo destrabamos
- En los logs del auto parecía que el OBD2 y Spotify fallaban al azar al dar contacto, pero cruzando la línea de tiempo segundo a segundo (`18:57:33` contacto → `18:57:42` aviso de voz + Spotify sin red → `18:57:45` reinicio de `rw.zlink.bt.name=Chevrolet` → `18:58:42` vuelve el Wi-Fi) encontramos las causas exactas de tiempo y estado en el hardware real.

### Próximo foco
- Probar la `v0.81.0` en el auto: pegar el token de lectura en **Ajustes → Sistema → Actualizaciones**, verificar que al tocar una canción en **Recientes** o **Buscar** arranque enseguida, usar `⏮` y `⏭` con el mapa expandido, y confirmar con una **Prueba completa** que el OBD2 conecte directo por la manera 3 pasados los 15 s del contacto.

