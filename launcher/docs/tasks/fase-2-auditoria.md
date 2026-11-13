# Fase 2 de la auditoría 2026-11-05 (tests primero, refactor controlado)

**Origen:** `docs/audits/AUDIT-2026-11-05.md`, sección J (Fase 2). Aprobada por el autor el 2026-11-06 ("Arranca nomas").
**Rama:** `audit/fase-2`. Publicada como **v0.79.6** en `main` el 2026-11-06, con el visto bueno del autor ("publica nomas"), aun sin haber probado v0.79.5 en el auto (R-87): el autor decidió publicar igual. El resto (pasos 5 a 9) salió como **v0.79.7** el 2026-11-06 ("publica nomas"), también sin probar las anteriores en el auto.
**Regla:** una mejora por vez, con su test, compilación y documentación. Nada que cambie el comportamiento del OBD2 antes de leer R-84 en el auto.
**Pruebas en la PC:** `:app:testDebugUnitTest`. Línea base: 215/215 (build 147).

## Pasos

- [x] 1. Red de seguridad (AUD-085): tests de la lógica con estado que hoy no se prueba. **Queda sin cubrir** (más pesado, con fakes de Media3 y sockets): reconexión de `RadioService`, el bucle de `PhoneShare.start` y `NwdTuner`.
  - [x] 1a. Lector del ELM327 (`ElmLink`): leer hasta el prompt, descartar restos, tiempo máximo, respuesta tardía. Cambio mecánico: `ElmLink` recibe los flujos (el socket pasa a un constructor secundario).
  - [x] 1b. Lectura de respuestas (`ObdParse`): errores del lector, otro PID, varias ECU, con encabezados.
  - [x] 1c. Token de Spotify (`SpotifyAuth`): vigente, renovar, error de red o del servidor sin desconectar, 400 que desconecta, dos pedidos a la vez (AUD-053), redirección. Cambio mecánico: los pedidos pasan por `SpotifyAuth.http`, que los tests reemplazan.
  - [x] 1d. `Net`: `getBytes` (redirecciones, tope de tamaño, errores), `get`, `request` y que no registre encabezados. **Encontró AUD-118** (enlace roto → excepción → cierra la app) y se arregló.
- [ ] 2. Estado de la navegación fuera del pager (AUD-069). **EN ESPERA** (el autor decidió esperar, 2026-11-06): antes hay que confirmar en el auto que la guía se corta al ir a Inicio (navegar, tocar Inicio, esperar 20 s, volver al mapa). No se toca hasta tener ese resultado.
- [ ] 3. Tanda chica.
  - [x] 3a. `RadioService.onDestroy` invalida lo pendiente (AUD-100).
  - [x] 3b. `state` en el permiso de Spotify (AUD-093). Falta probar la conexión real (R-88).
  - [ ] 3c. Control externo de la radio (AUD-091): **en espera**. Si el filtro de apps que se conectan queda muy estricto puede cortar los controles de la propia radio del auto o del sistema, y eso no se puede probar en la PC. Hacerlo después de probar en el auto, mirando primero qué apps se conectan hoy.

- [x] 4. A pedido del autor (2026-11-06): el Client ID de Spotify desde el secreto `SPOTIFY_CLIENT_ID`, para no pegarlo a mano en la radio. Opcional: sin el secreto todo sigue igual. Falta que el autor cargue el secreto.

- [x] 5. Resto del punto 3 (2026-11-06, "Dale nomas"), rama `audit/fase-3`, sin publicar: tests del bucle de los destinos del celular (AUD-085) y MAC enmascarada en la Prueba completa (AUD-096). **AUD-092 revisado, sin cambio.** Saltados a propósito: AUD-097 (lint de `NewApi` de los motores: workflow nuevo y resultado incierto), AUD-115 (caché de configuración y NDK: unos 30 s de CI y riesgo de romper la CI sin poder probarlo acá) y AUD-116 (catálogo de versiones: higiene sin beneficio medible). **Al publicar:** subir la versión, y actualizar el panel Dashboard (AUD-085, AUD-092 y AUD-096 cambiaron de estado) y `docs/ARTIFACTS.md`.

- [x] 6. GPS del mapa (2026-11-06, a partir de lo que contó el autor andando: "es lento en actualizar… no está en tiempo real y usa lo de mi casa"): punto azul de los dos mapas cada 1 s (AUD-119), cartel "Buscando GPS… / Sin señal de GPS hace N s" y rutas "desde la última posición conocida" (AUD-120), y línea de GPS en la Prueba completa. **Cambié lo que había propuesto:** la posición vieja se sigue usando (casi siempre es la correcta: el auto estaba estacionado), pero con aviso. **Falta R-89 en el auto:** si la radio entrega las posiciones lento, es del GPS de la radio (ahí iría el GPS del celular por Bluetooth).
- [x] 7. Idiomas del APK, castellano e inglés (AUD-086, recomendado y aceptado): −3,30 MB (24,43 → 21,13 MB).
- [x] 8. Aviso de luces apagadas a cualquier velocidad y hora (AUD-121, pedido del autor): desde 2 km/h, a los 5 s. **Falta R-90 en el auto.**
- [x] 9. Ubicación por defecto = la casa del autor (AUD-105, decisión suya: "usá la de mi casa"): `-26.7368, -65.258` ("9 de Julio 550, Tafí Viejo", posición que dio TomTom), un solo punto para el clima, el mapa, la búsqueda y "Mis lugares"; el clima dice "Tafí Viejo".
- Decisiones del autor: voces de Piper, no (AUD-087); ubicación por defecto, su casa (AUD-105). **Siguen abiertas:** AUD-070 (relojes: medir primero) y la firma (AUD-001: primero las copias de seguridad).

**Queda afuera (decisión o medición pendiente):** relojes del motor (AUD-070), Conscrypt en el arranque (AUD-078), `largeHeap` (AUD-076), cambios de comportamiento del OBD2 (R-84).

## Evidencia

| Paso | Qué se verificó | Resultado |
|---|---|---|
| 1a, 1b | `ElmLinkTest` 6/6 y `ObdParseTest` 10/10 a la primera. **Mutación:** sin la línea que descarta restos (`Obd.kt`), fallan `descarta_lo_que_quedo…` y `una_respuesta_tardia…` (restaurada; sin rastros) | OK |
| 1c | `SpotifyTokenTest` 10/10. **Mutación:** sin `@Synchronized` en `token()`, `dos_pedidos_a_la_vez…` falla con "una sola renovación: esperaba 1, hubo 2" (el bug de AUD-053; restaurado) | OK |
| 1d | `NetTest` 12 tests: **RED** en `getBytes_con_un_enlace_invalido…` (`MalformedURLException: no protocol`); **GREEN** al leer el enlace dentro del `try`. Quien la llama (`StationLogo`, en un `LaunchedEffect`) no atrapa excepciones | OK |
| Suite | `:app:testDebugUnitTest` **245/245** (215 + 30). Cobertura de los tests de lógica 22,3 % (antes 20,4 %): `Spotify.kt` 72,1 % (36,2), `Obd.kt` 31,7 % (24,9), `Net.kt` 54,5 % | OK en la PC; falta la CI |

| 3a | `RadioServiceLifecycleTest` con el servicio real (Robolectric). **RED:** con el servicio destruido, la reconexión pendiente igual corrió ("Radio de Prueba" quedó como emisora) y la generación no cambiaba (0 → 0). **GREEN** con `generation++` y `removeCallbacksAndMessages(null)` | OK |
| 3b | **RED:** 3 de los 4 tests nuevos fallaban (el pedido no llevaba `state`; una redirección ajena llegaba a pedir tokens). **GREEN** con el `state` al azar de 24 bytes, de un solo uso, y la redirección que no coincide se ignora sin tocar el mensaje. Los 3 tests viejos de redirección se ajustaron para llevar el `state` | OK en la PC; falta R-88 |
| 4 | **RED:** `SpotifyClientIdTest` no compilaba. **GREEN** 4/4. A mano: sin el secreto `BuildConfig.SPOTIFY_CLIENT_ID = ""`; con `ab"cd;ef 12` queda `"abcdef12"` (se limpia a letras y números). Workflow válido y el valor no se imprime | OK en la PC; falta R-88 con el secreto cargado |
| Suite | `:app:testDebugUnitTest` **255/255** en la PC. **CI (build 148, run 42158999805):** los 4 trabajos OK; `tests.txt`: 255 ejecutados, 255 exitosos, 0 fallidos, cobertura 23,1 % (1934 de 8355); firma verificada; nota "Spotify: Client ID cargado"; en el APK de la release: Client ID y control del `state` dentro del DEX, identidad de CarPlay presente | OK |
| 5 | **RED:** `PhoneShareStreamTest` y `ObdParseTest` no compilaban. **GREEN:** `PhoneShareStreamTest` 6/6 y `ObdParseTest` 11/11. **Mutación:** mirar la hora una sola vez en `consume` hace fallar `la_hora_se_mira_en_cada_mensaje` (restaurado). AUD-092: se leyó `AdbClusterActivity.kt`: `finish()` si el ajuste está apagado o el token no coincide. Suite local **262/262** | OK en la PC; falta la CI |
| 6 | **RED:** `GpsFixTest` no compilaba. **GREEN:** 11/11. El resto (1 s en los dos mapas, cartel, nota en las rutas, línea de la Prueba completa) depende del GPS real de la radio: no se prueba en la PC → R-89 | OK en la PC; falta R-89 |
| 7 | Compilación release local: APK 21.134.693 B contra 24.434.526 B de la CI del build 148 (**−3.299.833 B**); `resources.arsc` 4.232.440 → 933.256 B; única otra diferencia: los 2 archivos de `offline-mfi/` que agrega la CI | OK en la PC |
| 8 | **RED:** los tests nuevos de `LightsReminder` no compilaban (la función todavía pedía la hora del día). **GREEN:** `SafetyAlertsTest` con la regla nueva (2 km/h de día y de noche, a los 5 s). Suite local **273/273** | OK en la PC; falta R-90 |
| 9 | **RED:** `WeatherParseTest` no compilaba (faltaba `DEFAULT_PLACE_NAME`). **GREEN:** 6/6 (la consulta del clima usa `-26.7368, -65.258`; sin ubicación el lugar es "Tafí Viejo"). Suite local **274/274** | OK en la PC |
| CI v0.79.7 | Run 42165227425 (build 149): los 4 trabajos OK; `tests.txt`: 274 ejecutados, 274 exitosos, 0 fallidos, cobertura 23,6 % (1995 de 8439); firma verificada; nota "Spotify: Client ID cargado". En el APK de la release: Client ID, casa por defecto, cartel "Buscando GPS" y voz del aviso de luces dentro del DEX; identidad de CarPlay presente; 21.135.342 B | OK |
| Panel | Dashboard actualizado a la versión 54 del panel (estado, historial, funcionalidades, roadmap, archivos y hallazgos AUD-001 a AUD-121) y `docs/ARTIFACTS.md` | OK. Falta que el autor lo mire |

## Siguiente paso

Paso 2 (AUD-069): estado de la navegación fuera del pager. **Antes, que el autor confirme en el auto** que la guía se corta al ir a Inicio (navegar, tocar Inicio, esperar 20 s, volver al mapa).
