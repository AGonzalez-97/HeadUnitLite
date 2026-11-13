# Reporte de auditoría — Onix Launcher

**Fecha:** 2026-10-26
**Versión auditada:** v0.16.0 (build 16, commit `fff2181`)
**Tipo:** Auditoría integral (solo análisis, sin cambios funcionales)
**Autor del proyecto:** Axel Hernán González

---

## 1. Resumen ejecutivo

Onix Launcher es un launcher para el head unit Android 7.1 de un Chevrolet Onix. Está escrito en Kotlin con Jetpack Compose y se compila en GitHub Actions. Hoy funciona en celular, con compilación release optimizada y capturas de pantalla generadas en cada build. **Nunca se probó en el head unit real.**

| Severidad | Cantidad |
|---|---|
| CRITICAL | 0 |
| HIGH | 1 |
| MEDIUM | 10 |
| LOW | 12 |
| INFORMATIONAL | 4 |

**Hallazgo más importante:** la clave de firma y sus contraseñas están versionadas dentro del repositorio (AUD-001).

**Estado para una release ("release gate"):** no hay hallazgos críticos. Hay un hallazgo HIGH, así que la release requiere una decisión explícita del desarrollador: aceptar el riesgo mientras el repo siga privado, o corregirlo antes.

**Cómo se hizo la auditoría:** lectura completa del código y de la configuración, búsquedas con `grep` para ubicar cada hallazgo por línea, revisión del historial de commits y de las compilaciones en GitHub Actions, y consulta de los términos de las APIs externas. **No se pudo compilar ni ejecutar tests localmente**, porque el entorno de auditoría no tiene el Android SDK. Los resultados de tests provienen de la compilación 16 en GitHub Actions.

---

## 2. Visión general del proyecto

| Ítem | Valor |
|---|---|
| Plataforma | Android, minSdk 24, targetSdk 34, compileSdk 34 |
| Lenguaje / UI | Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00), Material 3 |
| Audio | AndroidX Media3 1.3.1 (ExoPlayer + MediaSession) |
| Build | AGP 8.5.2, Gradle 8.7 (sin wrapper en el repo), GitHub Actions |
| Distribución | APK release firmado, publicado en GitHub Releases (repo privado) |
| Tamaño | ~3.900 líneas de Kotlin en 15 archivos, 5 scripts Python de assets |
| Dispositivo objetivo | Head unit genérico JYT/K20N, Android 7.1, pantalla de 1024x600 (resolución no confirmada) |

---

## 3. Arquitectura

La app tiene una sola Activity (`MainActivity`). La UI usa un lienzo de diseño fijo de 1024x600 dp que se escala a la pantalla (`LauncherApp`). Tiene dos páginas deslizables (inicio y tablero) y superposiciones encima (radio y diagnóstico). Hay dos servicios: `MediaListenerService` (vacío, solo para el permiso de notificaciones) y `RadioService` (Media3).

**Observaciones:**
- **No hay separación en capas:** la UI llama directamente a fuentes de datos (GPS, clima, sesiones de medios) desde composables. No existe ViewModel ni repositorio. Para el tamaño actual es aceptable, pero ya se nota en `LauncherApp.kt` (ver AUD-015).
- **No se detectaron dependencias circulares**, porque todo vive en un solo paquete.
- **Punto único de falla:** el bucle de `LaunchedEffect` en `LauncherApp` (líneas 137–142) alimenta la hora y la música de toda la UI.
- **Documentación de arquitectura:** antes de esta auditoría no había (el README solo describía la versión 0.1).

---

## 4. Análisis funcional

| Funcionalidad | Estado | Evidencia |
|---|---|---|
| Hora/fecha del sistema | Implementada | `LauncherApp.kt` `now` |
| Fondo según hora y clima | Implementada | `Ambient.kt`, `Weather.kt` |
| Clima real con pronóstico | Implementada | `Weather.kt` (Open-Meteo) |
| Música (sesión activa) | Implementada | `Media.kt` |
| Velocidad GPS | Implementada | `rememberGpsSpeed()` |
| Ruta animada por velocidad | Implementada | `Road.kt` |
| Paneles movibles/escalables | Implementada | `PanelLayout.kt` |
| Tablero con relojes | Implementada (RPM sin datos) | `DashboardScreen.kt` |
| Radio por internet con dial FM | Implementada, no verificada en dispositivo | `Radio.kt`, `RadiosScreen.kt`, `TucumanFm.kt` |
| Modo pantalla de inicio | Implementada | `HomeAlias` + botón en Diagnóstico |
| OBD2 (ELM327) | **No implementada** (la UI muestra "--") | AUD-010 |
| Cámara de retroceso | **No implementada** (solo un aviso) | AUD-011 |
| Luces reales del auto | **No implementada** (se calcula por la hora) | AUD-012 |

---

## 5. Calidad del código

- **Nombres y comentarios:** claros y en español, consistentes. Los comentarios explican el porqué.
- **Archivo demasiado grande:** `LauncherApp.kt` tiene 930 líneas (AUD-015).
- **Código muerto:** hay varias funciones sin uso, que quedaron de diseños anteriores (AUD-014).
- **Números mágicos:** las coordenadas del diseño (por ejemplo `Modifier.at(24, 140, 270, 214)`) son intencionales, porque es un lienzo fijo, pero no están centralizadas. Las coordenadas por defecto de Tucumán (`-26.83, -65.22`) están escritas en el código (AUD-026).
- **Excepciones:** hay 11 `catch (e: Exception)` sin registro de logs (AUD-009).

---

## 6. Bugs

| ID | Clasificación | Resumen |
|---|---|---|
| AUD-007 | POSIBLE BUG | Condición de carrera en la cadena de enlaces de `RadioService` al re-sintonizar la misma emisora |
| AUD-006 | POSIBLE BUG | La animación de la ruta se reinicia en cada actualización de GPS |
| AUD-013 | POSIBLE BUG | Formato decimal según el idioma del equipo: "95,5" en el display y "95.5" en el detalle |
| AUD-008 | RIESGO POTENCIAL | Conexiones HTTP que no se cierran si hay error |

No hay bugs confirmados por ejecución: la app no se pudo ejecutar en el entorno de auditoría.

---

## 7. Seguridad

| ID | Severidad | Resumen |
|---|---|---|
| AUD-001 | HIGH | Clave de firma y contraseñas versionadas en el repo |
| AUD-002 | MEDIUM | Tráfico HTTP sin cifrar habilitado para toda la app |
| AUD-003 | LOW | Se reproduce cualquier enlace de audio encontrado en una página de terceros |
| AUD-004 | INFORMATIONAL | `RadioService` exportado (requerido por Media3) |

No se encontraron inyección SQL (no hay base de datos), ejecución de comandos, ni rutas de archivos construidas con datos externos. Los logs no exponen secretos porque la app no escribe logs.

---

## 8. Dependencias

| Dependencia | Versión | Observación |
|---|---|---|
| Compose BOM | 2024.06.00 | Versión de mediados de 2024; hay versiones más nuevas |
| Kotlin / compilador Compose | 1.9.24 / 1.5.14 | Coherentes entre sí |
| AGP | 8.5.2 | — |
| Media3 | 1.3.1 | — |
| activity-compose / core-ktx | 1.9.0 / 1.13.1 | — |
| Robolectric / Roborazzi (tests) | 4.12.2 / 1.20.0 | — |

No se verificaron vulnerabilidades conocidas contra una base de datos (CVE), así que no se reporta ninguna. No hay dependencias sin uso.

---

## 9. Testing

```text
Tests ejecutados: 7 (ScreenshotTest, en la compilación 16 de GitHub Actions)
Tests totales:    7
Tests exitosos:   7
Tests fallidos:   0
Cobertura:        no disponible (no se mide)
Estado:           los tests solo renderizan pantallas; no tienen aserciones ni comparación contra una referencia
```

| Clasificación | Funcionalidad sin tests |
|---|---|
| IMPORTANT WITHOUT TESTS | Cadena de enlaces de radio (`RadioService.tryNext`), unión del dial con el directorio (`RadioDirectory.merge`), lectura de frecuencia (`Station.freq`) |
| IMPORTANT WITHOUT TESTS | Lectura de la respuesta de Open-Meteo (`fetchWeather`), etiquetas de clima (`Weather.label`/`shortLabel`) |
| LOW RISK WITHOUT TESTS | `ambientFor`, `PanelPos`/`LayoutStore` |

Ver AUD-016 y AUD-017.

---

## 10. Rendimiento

| ID | Severidad | Resumen |
|---|---|---|
| AUD-005 | MEDIUM | Consulta de la sesión de música cada segundo en el hilo principal, y re-composición de toda la UI cada segundo |
| AUD-006 | LOW | Reinicio de la animación de la ruta en cada actualización de GPS |

El rendimiento real en el head unit no está medido. La pantalla de Diagnóstico ya mide cuadros por segundo y memoria, para hacerlo en el dispositivo.

---

## 11. Configuración

- **Development / Test / Production:** solo existe la configuración release (CI) y la debug (tests). No hay variables de entorno, salvo `GITHUB_RUN_NUMBER` para el número de versión.
- **Secretos:** `app/keystore.properties` (AUD-001).
- **Red:** `usesCleartextTraffic="true"` (AUD-002).
- **Ubicación por defecto del clima:** San Miguel de Tucumán, escrita en el código (AUD-026).

---

## 12. Documentación

| Documento | Estado antes de la auditoría |
|---|---|
| README.md | Desactualizado e incorrecto (AUD-021) |
| CHANGELOG / VERSION / ARCHITECTURE / TODO | No existían |
| Copyright / licencias de terceros | No existían (AUD-024) |

---

## 13. Copyright y autoría

- No había autor, copyright ni licencia declarados en el repo ni en la app (AUD-024).
- Los commits incluyen la línea "Co-Authored-By: Claude", es decir, código asistido por IA. Esto no cambia la autoría del proyecto, pero conviene dejarlo documentado.
- Hay recursos de terceros que no pertenecen al autor (AUD-022, AUD-023).

---

## 14. Deuda técnica

| Prioridad | Deuda | Motivo de la prioridad |
|---|---|---|
| HIGH | Secretos en el repo (AUD-001) | Si el repo se hace público o se filtra, cualquiera puede firmar actualizaciones que el equipo aceptaría como legítimas |
| MEDIUM | Falta de tests de lógica (AUD-016) | La radio tiene la lógica más compleja y cambia seguido |
| MEDIUM | `LauncherApp.kt` monolítico (AUD-015) | Cada cambio toca el mismo archivo y aumenta el riesgo de regresión |
| MEDIUM | Lectura de música por sondeo (AUD-005) | Puede afectar la fluidez en el head unit, que tiene poca potencia |
| LOW | Código muerto (AUD-014) | Confunde, pero no afecta el funcionamiento |

---

## 15. Hallazgos

### AUD-001 — Clave de firma versionada en el repositorio
- **Categoría:** Security · **Severidad:** HIGH
- **Archivo:** `app/keystore.properties` (1–4), `app/release.jks`, `app/build.gradle.kts` (signingConfigs)
- **Problema:** las contraseñas del keystore y el keystore mismo están en el control de versiones.
- **Evidencia:** `git ls-files` lista `app/keystore.properties` y `app/release.jks`.
- **Impacto:** quien acceda al repo puede firmar APKs que Android instalaría como actualización de la app.
- **Mitigación actual:** el repo es privado y la app se instala a mano en un solo equipo.
- **Recomendación:** mover el keystore y las contraseñas a GitHub Secrets (se cargan desde la web), sacarlos del repo y no hacer público el repo sin rotar la clave. Rotarla obliga a desinstalar la app una vez.
- **Esfuerzo:** bajo.

### AUD-002 — Tráfico HTTP sin cifrar para toda la app
- **Categoría:** Security · **Severidad:** MEDIUM
- **Archivo:** `app/src/main/AndroidManifest.xml` línea 21
- **Problema:** `android:usesCleartextTraffic="true"` permite HTTP en cualquier dominio.
- **Evidencia:** hace falta para algunas radios (Mitre `http://buecrplb01…`, LV7 `http://01.solumedia…`), pero se aplica a toda la app.
- **Impacto:** cualquier conexión futura podría salir sin cifrar sin que se note.
- **Recomendación:** `network_security_config.xml` que permita HTTP solo para los dominios de las radios.
- **Esfuerzo:** bajo.

### AUD-003 — Reproducción de enlaces sacados de una página de terceros
- **Categoría:** Security · **Severidad:** LOW
- **Archivo:** `Radio.kt` líneas 136–151 (`scrapeStream`)
- **Problema:** se toma el primer enlace de audio que aparezca en el HTML de cienradios.com y se reproduce.
- **Impacto:** acotado a reproducir audio con ExoPlayer. Si la página cambia, puede sonar otra señal.
- **Recomendación:** aceptar solo dominios esperados (`cienradios.com.ar`) y avisar cuando se usa un enlace sacado de una página.
- **Esfuerzo:** bajo.

### AUD-004 — Servicio de radio exportado
- **Categoría:** Security · **Severidad:** INFORMATIONAL
- **Archivo:** `AndroidManifest.xml` (servicio `.RadioService`, `exported="true"`)
- **Observación:** Media3 lo requiere para que otras apps (por ejemplo los controles del volante o del sistema) puedan controlar la reproducción. Cualquier app podría pausar o cambiar la radio. Es aceptable.

### AUD-005 — Música consultada cada segundo en el hilo principal
- **Categoría:** Performance · **Severidad:** MEDIUM
- **Archivo:** `LauncherApp.kt` líneas 137–142
- **Problema:** `readMedia(context)` hace llamadas entre procesos a `MediaSessionManager` cada segundo desde el hilo de UI. Además, los estados `now` y `media` cambian cada segundo y re-componen toda la pantalla.
- **Impacto:** posibles tirones en el head unit, que tiene poca potencia. **No medido.**
- **Recomendación:** escuchar cambios con `OnActiveSessionsChangedListener` y `MediaController.Callback`, calcular la posición localmente y separar el tic del reloj (cada minuto) del progreso de la canción.
- **Esfuerzo:** medio.

### AUD-006 — Animación de la ruta reiniciada en cada actualización de GPS
- **Categoría:** Bug (posible) · **Severidad:** LOW
- **Archivo:** `Road.kt` línea 42
- **Problema:** `LaunchedEffect(moving, speedKmh)` se reinicia con cada cambio de velocidad (aproximadamente cada segundo), y `last = 0L` hace saltear un cuadro.
- **Impacto:** un pequeño tirón por segundo en la línea central.
- **Recomendación:** usar `moving` como clave y leer la velocidad con `rememberUpdatedState`.
- **Esfuerzo:** bajo.

### AUD-007 — Condición de carrera en la cadena de enlaces de radio
- **Categoría:** Bug (posible) · **Severidad:** MEDIUM
- **Archivo:** `Radio.kt` líneas 285–310 (`tryNext`)
- **Problema:** el resultado de un hilo de fondo se descarta solo si cambió el **nombre** de la emisora. Al re-sintonizar la misma emisora (por ejemplo, al cambiar LOCAL/NACIONAL), un hilo viejo puede arrancar un enlace anterior. Además, `idx` es compartido, así que puede saltear candidatos.
- **Impacto:** en casos puntuales puede sonar la señal equivocada (nacional en vez de local) o fallar un intento.
- **Recomendación:** un contador de "generación" que se incremente en cada `ACTION_PLAY` y se compare en `main.post`.
- **Esfuerzo:** bajo.

### AUD-008 — Conexiones HTTP sin cerrar ante errores
- **Categoría:** Reliability · **Severidad:** LOW
- **Archivo:** `Weather.kt` 75–79; `Radio.kt` 83–88, 140–145, 159–163
- **Problema:** `disconnect()` solo se llama si todo sale bien. Tampoco se valida el código de respuesta.
- **Impacto:** se pueden acumular conexiones si la red falla seguido. Es leve.
- **Recomendación:** `try/finally { conn.disconnect() }` y verificar `responseCode`.
- **Esfuerzo:** bajo.

### AUD-009 — Errores silenciosos y ausencia de logs
- **Categoría:** Error handling · **Severidad:** LOW
- **Archivos:** `LauncherApp.kt` 490, 758, 773, 885, 910, 925; `Radio.kt` 90, 149, 166; `Weather.kt` 63, 110
- **Problema:** 11 `catch (e: Exception)` sin registro de logs. La app no usa `android.util.Log`.
- **Impacto:** cuando algo falla en el head unit (clima, radio, GPS) no queda rastro para diagnosticar.
- **Recomendación:** `Log.w` con una etiqueta común y mostrar el último error en Diagnóstico.
- **Esfuerzo:** bajo.

### AUD-010 — OBD2 no implementado
- **Categoría:** Functional · **Severidad:** MEDIUM
- **Archivo:** `LauncherApp.kt` línea 184 (`DashboardScreen(…, null, …)`); `HomeScreen(obd = null)` por defecto
- **Problema:** el panel de motor y el tablero muestran "--". Los datos de las capturas son de ejemplo.
- **Recomendación:** implementar la conexión Bluetooth al ELM327 como próxima funcionalidad.

### AUD-011 — Botón de cámara sin función
- **Categoría:** Functional · **Severidad:** LOW
- **Archivo:** `LauncherApp.kt` línea 451
- **Problema:** solo muestra un aviso.
- **Recomendación:** abrir la app de cámara del head unit cuando se conozca su paquete, u ocultar el botón.

### AUD-012 — Luces calculadas por la hora
- **Categoría:** Functional · **Severidad:** INFORMATIONAL
- **Observación:** `lightsOn` depende del ambiente (hora y clima), no del auto. Es una limitación conocida mientras no haya CAN u otra señal.

### AUD-013 — Formato decimal según el idioma del equipo
- **Categoría:** Bug (posible) / i18n · **Severidad:** LOW
- **Archivos:** `RadiosScreen.kt` 226 y 414; `DashboardScreen.kt` 142 y 200; `EnginePanel.kt` 108
- **Problema:** `String.format("%.1f", …)` sin `Locale`. En un equipo en español da "95,5", mientras el detalle de la emisora dice "FM 95.5". Las capturas usan inglés, por eso no se ve.
- **Recomendación:** usar `Locale.US` para las frecuencias y decidir el criterio para voltaje y RPM.
- **Esfuerzo:** bajo.

### AUD-014 — Código muerto
- **Categoría:** Code quality · **Severidad:** LOW · **Clasificación:** NO UTILIZADO (no hay uso dinámico posible: son funciones Kotlin privadas o de nivel superior sin reflexión)
- **Evidencia:** sin referencias, salvo dentro de la misma cadena muerta:
  - `Drawing.kt`: `drawScene` (línea 23) y `drawCar` (línea 88), que dibujaban el auto y el fondo antes de las fotos.
  - `Ambient.kt`: `AmbientColors` y `colorsFor` (línea 39), usados solo por `drawScene`.
  - `LauncherApp.kt`: `Speedometer` (608), `drawGauge` (621) y `LabelPaint` (110); `ObdCard` (656) y `GaugeBar` (667); `StatusChip` (496).
  - `RadioDirectory.featured`: solo se usa en `ScreenshotTest`.
- **Recomendación:** eliminar en un cambio aparte (PATCH), sin tocar comportamiento.

### AUD-015 — `LauncherApp.kt` monolítico
- **Categoría:** Maintainability · **Severidad:** MEDIUM
- **Archivo:** `LauncherApp.kt` (930 líneas)
- **Problema:** mezcla estado de la app, GPS, sondeo de clima, pantalla de inicio, dock, diagnóstico y utilidades.
- **Recomendación:** dividirlo en `HomeScreen.kt`, `Dock.kt`, `Diagnostics.kt` y `Location.kt`, sin cambiar comportamiento. Conviene hacerlo antes de sumar OBD2.

### AUD-016 — Sin tests de la lógica
- **Categoría:** Testing · **Severidad:** MEDIUM
- **Archivo:** `app/src/test/.../ScreenshotTest.kt` (único archivo de tests)
- **Problema:** los 7 tests solo renderizan; no verifican nada. La lógica de radio y clima no tiene tests.
- **Recomendación (tests concretos):**
  - `Station.freq`: "La 100" con "FM 95.5 · Tucumán" da 95.5; "Radio 9 de Julio" sin frecuencia da null.
  - `RadioDirectory.merge`: une "FM Sensaciones 95.1" con el 95.1 del dial; una emisora sin frecuencia queda como "WEB".
  - Orden de candidatos: en LOCAL, `nac:` queda último; en NACIONAL, primero.
  - `fetchWeather` (lectura del JSON): con una respuesta guardada de ejemplo.
  - `ambientFor`: límites de 6:00, 7:30, 17:30 y 19:30.

### AUD-017 — Los tests no bloquean la release
- **Categoría:** Testing · **Severidad:** LOW
- **Archivo:** `.github/workflows/build.yml` (`continue-on-error: true` en las capturas)
- **Observación:** es intencional, para no frenar el APK. Cuando haya tests de lógica, esos sí deberían bloquear.

### AUD-018 — Sin Gradle wrapper
- **Categoría:** Build · **Severidad:** LOW
- **Evidencia:** no existe `gradlew` en el repo. La CI fija Gradle 8.7 con `setup-gradle`.
- **Impacto:** compilar en una computadora requiere instalar Gradle 8.7 a mano.

### AUD-019 — Acciones de GitHub sobre Node 20 (obsoleto)
- **Categoría:** Build · **Severidad:** LOW
- **Evidencia:** avisos en las ejecuciones: `actions/checkout@v4`, `setup-java@v4`, `setup-gradle@v3` y `action-gh-release@v2` corren forzadas en Node 24.
- **Recomendación:** actualizar las versiones de las acciones cuando haya versiones nuevas.

### AUD-020 — Versionado inconsistente
- **Categoría:** Versioning · **Severidad:** MEDIUM
- **Evidencia:** los builds 1 a 12 reportaban `versionName "0.1"` (versión fija en el código). Desde el build 13 es `0.<número de build>`. El README decía "versión (0.1)". No había CHANGELOG.
- **Recomendación:** adoptar SemVer explícito (ver `VERSION.md`), con una sola fuente de verdad que lea `build.gradle.kts`.

### AUD-021 — README desactualizado e incorrecto
- **Categoría:** Documentation · **Severidad:** MEDIUM
- **Evidencia:** decía que el clima estaba pendiente (está desde el build 2), que el APK es `app-debug.apk` (ahora es `onix-launcher.apk`, release) y que el modo inicio se activa editando el manifiesto (ahora es un botón). No mencionaba radio, tablero ni paneles.
- **Estado:** corregido en esta auditoría (solo documentación).

### AUD-022 — Falta la atribución de Open-Meteo
- **Categoría:** Legal / Third-party · **Severidad:** MEDIUM
- **Evidencia:** los términos de Open-Meteo exigen aceptar la licencia CC BY 4.0 de los datos. La app no muestra ninguna atribución.
- **Recomendación:** agregar "Clima: Open-Meteo.com" en Diagnóstico o en una pantalla "Acerca de".

### AUD-023 — Imágenes de terceros
- **Categoría:** Legal / Third-party · **Severidad:** MEDIUM
- **Evidencia:** el emblema de Chevrolet (marca de General Motors); la foto del Onix (autor desconocido, aportada por el usuario a partir de una publicación); la foto de montañas con crédito visible "Stefan Sauzuk" en la esquina inferior izquierda de los fondos; la foto del atardecer (origen desconocido).
- **Impacto:** el uso personal y privado es razonable, pero **no se pueden redistribuir**. Ver `THIRD_PARTY_LICENSES.md`.
- **Recomendación:** mantener el repo privado; para publicar, reemplazar las fotos por propias o con licencia libre y quitar el emblema.

### AUD-024 — Sin autoría ni copyright declarados
- **Categoría:** Copyright · **Severidad:** LOW
- **Estado:** documentación agregada en esta auditoría (`COPYRIGHT.md`, README). Falta la pantalla "Acerca de" en la app, que es un cambio funcional y queda para la próxima versión.

### AUD-025 — El permiso de notificaciones se pide cada vez
- **Categoría:** UX · **Severidad:** LOW
- **Archivo:** `RadiosScreen.kt` líneas 102–106
- **Problema:** si se negó, se vuelve a pedir cada vez que se abre la radio. Android deja de mostrar el diálogo después de dos negativas.
- **Recomendación:** recordar que ya se pidió.

### AUD-026 — Ubicación por defecto escrita en el código
- **Categoría:** Configuration · **Severidad:** INFORMATIONAL
- **Archivo:** `LauncherApp.kt` (`fetchWeather(loc?.latitude ?: -26.83, loc?.longitude ?: -65.22)`)
- **Observación:** razonable para un proyecto personal. Conviene llevarla a una constante con nombre.

### AUD-027 — Compatibilidad con el head unit no verificada
- **Categoría:** Compatibility · **Severidad:** INFORMATIONAL (riesgo)
- **Observación:** no se probó en Android 7.1 real. Hay cosas a confirmar: el soporte TLS de los certificados actuales en Android 7.1 (Open-Meteo, radios HTTPS), la fluidez de Compose en ese hardware y la resolución exacta de la pantalla. Es un riesgo, no un error comprobado.

---

## 16. Acciones priorizadas

**Inmediatas**
- AUD-001: decidir si se acepta el riesgo (repo privado) o se mueven los secretos a GitHub Secrets.

**Corto plazo (próxima versión)**
- AUD-007: contador de generación en `RadioService`.
- AUD-013: `Locale.US` en las frecuencias.
- AUD-022 y AUD-024: pantalla "Acerca de" con autoría y atribución de Open-Meteo.
- AUD-002: configuración de red que limite HTTP a las radios.
- AUD-016: primeros tests de lógica de radio y clima.

**Mediano plazo**
- AUD-015: dividir `LauncherApp.kt`.
- AUD-005: escuchar cambios de música en vez de consultar cada segundo.
- AUD-014: eliminar el código muerto.
- AUD-008 y AUD-009: cierre de conexiones y logs.

**Largo plazo**
- AUD-010: OBD2. AUD-011: cámara. AUD-012: luces reales.
- AUD-018 y AUD-019: Gradle wrapper y acciones actualizadas.

---

## 17. Resumen de riesgos

| Riesgo | Probabilidad | Impacto |
|---|---|---|
| Filtración del keystore (AUD-001) | Baja (repo privado) | Alto |
| Fluidez insuficiente en el head unit (AUD-005, AUD-027) | Media | Medio |
| Radio con señal equivocada o sin señal (AUD-007, AUD-003) | Media | Bajo |
| Regresiones sin detectar por falta de tests (AUD-016) | Media | Medio |
| Uso de imágenes de terceros si el repo se publica (AUD-023) | Baja | Medio |

---

## 18. Conclusión

El proyecto avanzó rápido y funciona en celular. La arquitectura es simple y adecuada para su tamaño, y la compilación está automatizada. Los puntos débiles son la gestión de secretos, la falta de tests de lógica, un archivo central demasiado grande y que no se probó en el equipo de destino. No hay hallazgos críticos. El único HIGH está mitigado mientras el repositorio siga privado, pero conviene resolverlo pronto.

---

## 19. Seguimiento — v0.17.0 (2026-10-26)

Las secciones 1 a 18 describen el estado auditado (v0.16.0) y no se modifican. Esta tabla registra qué pasó con cada hallazgo. La copia original de la auditoría está en `docs/audits/AUDIT-2026-10-26.md`.

| ID | Severidad | Estado | Cómo se resolvió |
|---|---|---|---|
| AUD-001 | HIGH | **Abierto — riesgo aceptado** | Requiere cargar secretos en GitHub desde la web. Se mantiene mientras el repo sea privado; pasos en `TODO.md` |
| AUD-002 | MEDIUM | Mitigado | `network_security_config`: HTTPS obligatorio para Open-Meteo, Radio Browser y Cienradios; HTTP solo para el audio (riesgo residual aceptado) |
| AUD-003 | MEDIUM | Resuelto | `RadioDirectory.isTrusted`: solo páginas y enlaces de `cienradios.com` / `cienradios.com.ar` |
| AUD-004 | INFO | Sin cambios | Exportación requerida por Media3; revisar en el futuro |
| AUD-005 | MEDIUM | Resuelto | `MediaWatcher` (eventos) + posición local; reloj por minuto |
| AUD-006 | LOW | Resuelto | `LaunchedEffect(moving)` + `rememberUpdatedState` |
| AUD-007 | MEDIUM | Resuelto | Número de generación en `RadioService` |
| AUD-008 | LOW | Resuelto | `Net.get` con `try/finally`, `responseCode` y límite de tamaño |
| AUD-009 | LOW | Resuelto | `AppLog` (logcat `OnixLauncher`) y "Último error" en Diagnóstico |
| AUD-010 | MEDIUM | Abierto | OBD2: funcionalidad planificada (v0.20.0 desde el roadmap de v0.18.0) |
| AUD-011 | LOW | Parcial | Botón retirado; vuelve cuando se conozca la app de cámara del head unit |
| AUD-012 | INFO | Resuelto | v0.49.0: señales de luces de la radio NWD (`Lights.kt`); probado en el auto con el sensor de iluminación apagado (R-38, 2026-11-03) |
| AUD-013 | LOW | Resuelto | `Locale.US` en todos los `String.format` |
| AUD-014 | LOW | Resuelto | Código muerto eliminado |
| AUD-015 | MEDIUM | Resuelto | `LauncherApp.kt` (930 líneas) → 132 líneas + 7 archivos nuevos |
| AUD-016 | MEDIUM | Resuelto | 25 tests de lógica |
| AUD-017 | LOW | Resuelto | Tests de lógica bloqueantes en la CI; `tests.txt` en cada release |
| AUD-018 | LOW | Resuelto | Gradle wrapper 8.7 + validación |
| AUD-019 | LOW | Resuelto | checkout v6, setup-java v5 |
| AUD-020 | MEDIUM | Resuelto | `version.properties` (ADR-0006) |
| AUD-021 | LOW | Resuelto | README completo |
| AUD-022 | MEDIUM | Resuelto | Atribución en Acerca de |
| AUD-023 | MEDIUM | Sin cambios | Repo privado; reemplazar imágenes antes de publicar |
| AUD-024 | LOW | Resuelto | Acerca de con autoría y copyright |
| AUD-025 | LOW | Resuelto | Se pide una sola vez (`permisos`) |
| AUD-026 | INFO | Resuelto | `DEFAULT_LAT` / `DEFAULT_LON` |
| AUD-027 | MEDIUM | Abierto | Falta probar en el head unit |

**Resumen:** 20 resueltos, 1 mitigado, 1 parcial y 5 abiertos o sin cambios: AUD-001 (HIGH, riesgo aceptado), AUD-010 y AUD-027 (funcionalidad y prueba pendientes), AUD-023 (imágenes, repo privado) y AUD-004 (informativo).

**Verificación:** compilaciones 17 y 18 en GitHub Actions — 25 tests de lógica y 9 capturas, 34 de 34 exitosos en cada una (`tests.txt` de la release `build-18`).


## 20. Revisión técnica — v0.38.1 (2026-10-28)

Pedido del autor: auditoría antes de modificar, correcciones chicas con tests de regresión y sin exponer secretos.

| ID | Tipo | Archivo / función | Hallazgo | Estado |
|---|---|---|---|---|
| AUD-028 | Error | `Radio.kt` → `RadioDirectory.merge()` | `d.freq!!` cerraba la app si una emisora del dial no tenía frecuencia | Resuelto (v0.38.1), `AuditRegressionTest` |
| AUD-029 | Error | `Radio.kt` → `RadioService.onStartCommand` | Servicio exportado (lo exige Media3) aceptaba órdenes de cualquier app, incluso reproducir un enlace cualquiera | Resuelto (v0.38.1): las órdenes propias llevan una clave al azar del proceso; `AuditRegressionTest` |
| AUD-030 | Error | `Media.kt` → `AutoResume` / `LastAudio` (v0.38.0) | Retomaba el audio también en el celular al prender la pantalla y podía fallar al arrancar el servicio en segundo plano | Resuelto (v0.38.1): solo si el launcher es la pantalla de inicio, con manejo de errores |
| AUD-031 | Riesgo | `network_security_config.xml` | HTTPS obligatorio solo para Open-Meteo, Radio Browser, Cienradios, OpenStreetMap, OSRM, FOSSGIS, Photon (v0.42.0) y Spotify; el resto admite HTTP (audio de radios, logos y páginas de emisoras) | Aceptado y documentado: las radios lo necesitan; no se envían datos del usuario por HTTP |
| AUD-001 | Riesgo | `app/release.jks`, `app/keystore.properties` | Firma versionada en el repo privado | Abierto. Decidida la opción A (misma clave, sin impacto; `docs/SIGNING.md`, ADR-0010) con control de firma en la CI; falta que el autor cargue los secretos |
| AUD-032 | Riesgo | Mapas | Nominatim y el servidor de prueba de OSRM no garantizan servicio | Aceptado: si fallan, el mapa lo dice y no se cierra |
| AUD-033 | Diseño | `EnginePanel.kt` | Sin GPS u OBD2 solo se veía "--" | Resuelto (v0.38.1): "sin GPS", "sin OBD2", "OBD2 sin conectar" |
| AUD-034 | Documentación | README, VERSION, ARCHITECTURE, HEAD_UNIT | Describían el Tablero, la ruta animada, el pronóstico de 4 días; roadmap con OBD2 pendiente; resumen de v0.21 | Resuelto (v0.38.1) |
| AUD-035 | Rendimiento | — | Sin medición de fluidez en la radio todavía | Abierto: medir con Diagnóstico → Fluidez antes de tocar animaciones |

Revisado sin hallazgos: `MediaListenerService` exportado con permiso del sistema; tareas en segundo plano atadas a la pantalla (se cancelan al salir); el OBD2 reintenta cada 10 s; los tokens de Spotify no se registran.


## 21. Auditoría — v0.58.0 (2026-11-03)

Pedido del autor: revisar todo el código. Informe completo: [`docs/audits/AUDIT-2026-11-03.md`](docs/audits/AUDIT-2026-11-03.md). Correcciones en v0.59.0.

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-036 | CRITICAL | El motor exige micrófono y ubicación; Onix nunca pedía el micrófono: Android Auto y CarPlay no habrían arrancado | Resuelto (v0.59.0) |
| AUD-037 | HIGH | CarPlay por cable necesita la autorización de VPN; nadie la pedía | Resuelto (v0.59.0) |
| AUD-038 | MEDIUM | El ruido del GPS estacionado armaba viajes y km falsos | Resuelto (v0.59.0), test |
| AUD-039 | MEDIUM | Otra app de proyección instalada puede quedarse con el USB | Mitigado (aviso) |
| AUD-040 | LOW | `REQUEST_INSTALL_PACKAGES` heredado del motor, sin uso | Resuelto (v0.59.0) |
| AUD-041 | MEDIUM | El proceso `:aa` queda en memoria (radio de 2 GB) | Abierto: medir |
| AUD-042 | MEDIUM | Lugar de presintonía −1 del sintonizador sin confirmar | Abierto: R-43 |
| AUD-043 | MEDIUM | Identidad de CarPlay no oficial y certificado público de Android Auto | Aceptado (ADR-0012) |
| AUD-044 | LOW | `tests.txt` no se podía leer desde la sesión | Resuelto (v0.59.0): también en el log |
| AUD-045 | INFO | APK de 40 MB y CI de ~23 min por los motores | Aceptado |
| AUD-046 | LOW | README con el tamaño del código viejo | Resuelto (v0.59.0) |
| AUD-047 | MEDIUM | Lint se cortaba a los 15 min sin reporte (revisaba también el motor) | Mitigado (v0.60.1): lint completo aparte (`lint.yml`); `NewApi` fatal en cada release |

**Resultados reales (build 86):** 149 tests, 149 exitosos, 0 fallidos.


## 22. Auditoría 2 — v0.59.0 (2026-11-04)

Segunda pasada leyendo los archivos completos. Informe: [`docs/audits/AUDIT-2026-11-04.md`](docs/audits/AUDIT-2026-11-04.md). Correcciones en v0.60.0.

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-048 | HIGH | Al pasar de la radio por internet al FM por la antena, el servicio de internet se apagaba después y borraba el estado ("sin emisora, sin sonar"): el display del FM quedaba vacío aunque sonaba | Resuelto (v0.60.0) |
| AUD-049 | HIGH | Al prender la pantalla con Android Auto o CarPlay conectados, Onix retomaba Spotify o la radio y peleaba por el audio | Resuelto (v0.60.0) |
| AUD-050 | HIGH | Con el FM sonando por la antena, al conectar Android Auto o CarPlay la fuente de audio de la radio seguía en FM: **no se escuchaba el celular** | Resuelto (v0.60.0) |
| AUD-051 | MEDIUM | "Borrar códigos" con el lector desconectado quedaba pendiente y se ejecutaba solo al reconectar, quizás andando | Resuelto (v0.60.0) |
| AUD-052 | LOW | Si Android cerraba la app a mitad de un viaje, se perdían esos km del kilometraje | Resuelto (v0.60.0) |
| AUD-053 | MEDIUM | Dos pedidos a la vez (listas y reproducir) renovaban el token dos veces; Spotify invalida el primero y el segundo recibía 400: **Onix desconectaba Spotify** | Resuelto (v0.60.0) |
| AUD-054 | LOW | Si se elegía otro destino mientras se calculaba una ruta, la ruta vieja podía llegar después y dibujarse | Resuelto (v0.60.0) |
| AUD-055 | MEDIUM | Fuera de ruta y sin señal, el recálculo fallaba, la ruta quedaba vacía y la guía (voz y cartel) se apagaba para siempre | Resuelto (v0.60.0) |
| AUD-056 | HIGH | Sin señal de GPS (túnel, cochera) la última velocidad quedaba fija: el velocímetro marcaba la velocidad vieja parado, Mi auto sumaba km y el aviso de luces podía saltar | Resuelto (v0.60.0) |
| AUD-057 | LOW | El pedido de permisos del motor podía superponerse con el de ubicación al abrir | Resuelto (v0.60.0) |


## 23. Auditoría 3 — v0.64.0 (2026-11-04)

Revisión de todo lo cambiado desde la auditoría 2 (v0.61.0 a v0.64.0), seguridad y CI. Informe: [`docs/audits/AUDIT-2026-11-04b.md`](docs/audits/AUDIT-2026-11-04b.md). Correcciones en v0.64.1.

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-058 | HIGH | Con Android Auto, CarPlay u otra app encima, la velocidad que usaban Mi auto y los avisos quedaba congelada: Mi auto sumaba km con el auto parado y los avisos podían repetirse | Resuelto (v0.64.1): `GpsSpeed.live()`; test |
| AUD-059 | MEDIUM | El lint completo se trababa (85 min) analizando el motor de CarLink | Resuelto (v0.64.1): solo el código de Onix; termina en 4 min 23 s (120 avisos, 0 de Android 7) |
| AUD-060 | LOW | El generador de voces podía usar otra voz con otra licencia sin avisar | Resuelto (v0.64.1) |

## 24. Auditoría 4 — v0.67.0, todo el proyecto (2026-11-04)

Barrido de todo el código de Onix por tipo de riesgo (nulos, hilos, recursos, red, almacenamiento, registros, manifiesto, secretos, animaciones) y de la documentación. Informe: [`docs/audits/AUDIT-2026-11-04c.md`](docs/audits/AUDIT-2026-11-04c.md). Correcciones en v0.67.1.

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-061 | MEDIUM | Una voz grabada que no cargaba dejaba abiertos el reproductor y el archivo | Resuelto (v0.67.1) |
| AUD-062 | LOW | El sonido de inicio no liberaba el canal de audio si fallaba | Resuelto (v0.67.1) |
| AUD-063 | LOW | La guía de la Prueba completa estaba vieja (tres pruebas, lista de v0.62.0) | Resuelto (v0.67.1) |
| AUD-065 | LOW | La Prueba completa no decía la salud por sistema | Resuelto (v0.67.1): línea nueva; test |
| AUD-064 | LOW | README con resultados de la compilación 18 y roadmap hasta v0.38; pendientes resueltos sin marcar | Resuelto (v0.67.1) |


## 25. Revisión de los cambios v0.50–v0.68.1 (2026-11-04)

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| AUD-066 | MEDIUM | `vendor/diauto/app/src/main/res/raw/privkey` es una **clave privada** (receptor de referencia de Android Auto, emisor "Google Automotive Link"); `CLAUDE.md`, `THIRD_PARTY_LICENSES.md` y la auditoría 4 la describían como "certificado público" y no la contaban como secreto | Documentación corregida; riesgo aceptado por el autor solo para uso personal (ADR-0012) |
| AUD-067 | MEDIUM | Cada APK lleva esa clave y la identidad de CarPlay del adaptador Carlinkit (la CI la agrega siempre desde v0.58.0), mientras la regla de oro 3 pedía notas de release "para el público" | Aclarado en `CLAUDE.md`, ADR-0012 y `docs/CARPLAY.md`: las releases y los APK no se comparten; "para el público" es solo el estilo de la nota |
| AUD-068 | HIGH | **Corregido en v0.70.1 (build 117 confirmó la identidad en el APK; falta probar en el auto):** la CI toma la identidad de CarLink 1.0.9 (huella publicada) y, si se retira, de la última release con su `.sha256`. Los APK salen **sin CarPlay** desde el build 86 (v0.59.0): la CI no pudo armar la identidad (aviso "no se pudo armar la identidad" en los builds 86 a 114; solo el build 84 la tuvo). `docs/tasks/ESTADO.md` lo daba por listo | Abierto. Causa (build 115): el APK `CarLink-1.0.7.apk` ya no está en la release de CarLink (**HTTP 404**; la release solo muestra el código fuente). Única vía: cargar los secretos de `docs/CARPLAY.md` desde una copia propia del APK |

## 26. Auditoría técnica integral — v0.79.4 (2026-11-05)

Revisión de solo lectura de todo el proyecto (rendimiento, arquitectura, APK y Gradle, seguridad y tests) con evidencia del build 146. Informe completo, con los 49 hallazgos, el plan por fases y las pruebas en el auto: [`docs/audits/AUDIT-2026-11-05.md`](docs/audits/AUDIT-2026-11-05.md). Fase 1 en v0.79.5 ([`docs/tasks/fase-1-auditoria.md`](docs/tasks/fase-1-auditoria.md)); fase 2 en v0.79.6 ([`docs/tasks/fase-2-auditoria.md`](docs/tasks/fase-2-auditoria.md)).

| ID | Prioridad | Hallazgo | Estado |
|---|---|---|---|
| AUD-069 | P1 | La guía de navegación se corta al volver a Inicio (estado en `remember` de la página del mapa) | Abierto: fase 2; reproducir en el auto |
| AUD-070 | P1 | Los relojes del motor redibujan casi sin parar con el OBD2, también en modo liviano | Abierto: decisión del autor (aguja sin animar en liviano) y medición |
| AUD-071 | P2 | 1,23 MB de recursos de BouncyCastle que nadie lee | Resuelto (v0.79.5): −1,26 MB medidos con AUD-113 |
| AUD-072 | P2 | Una cancelación a mitad del reinicio del Bluetooth lo dejaba apagado | Resuelto (v0.79.5); test que falla sin el arreglo |
| AUD-073 | P2 | `carlink.yml`: inyección de comandos con el tag de CarLink | Resuelto (v0.79.5); falta que corra en la CI |
| AUD-074 | P2 | CI con permiso de escritura en todos los trabajos; acciones por tag | Resuelto (v0.79.5); falta que corra en la CI |
| AUD-075 | P2 | Cada lectura del OBD2 recomponía la pantalla aunque nada cambiara | Resuelto (v0.79.5) |
| AUD-080 | P2 | Conexión del OBD2 que llegaba después del tope quedaba abierta | Resuelto (v0.79.5) |
| AUD-081 | P2 | Nombres de las pantallas de los motores escritos a mano, sin test | Resuelto (v0.79.5): test |
| AUD-083 | P2 | JaCoCo no medía las clases de los tests de Robolectric (16,2 % engañoso) | Resuelto (v0.79.5): 20,4 % con los mismos tests |
| AUD-088 | P2 | La CI no guardaba el mapa de R8 | Resuelto (v0.79.5): `onix-launcher-mapping.txt.gz` en la release |
| AUD-089 | P2 | Documentación que no coincidía con el código (sección L) | Resuelto (v0.79.5) |
| AUD-101 | P3 | Un JSON dañado de Mi auto se mostraba vacío y el viaje siguiente lo pisaba | Resuelto (v0.79.5): copia de respaldo; test |
| AUD-103, AUD-104, AUD-106, AUD-113 | P3 | Doble Prueba completa, `info` sin `redact`, código sin uso, sobrantes chicos del APK | Resueltos (v0.79.5) |
| AUD-085 | P2 | Lógica con estado sin tests | En curso: hechos el lector del ELM327, las respuestas del OBD2, el token de Spotify y `Net` (v0.79.6), y el bucle de `PhoneShare` (v0.79.7); faltan la reconexión de la radio y `NwdTuner` |
| AUD-092 | P3 | `AdbClusterActivity` de DiPlay exportada sin permiso | **Revisado, sin cambio (2026-11-06):** se cierra sola salvo que el ajuste esté prendido y el intent traiga un token que el motor genera en memoria; cerrarla rompería la función de tablero (que la abre por el shell). No se toca `vendor/` |
| AUD-096 | P3 | La Prueba completa copiaba la dirección Bluetooth del lector al portapapeles y a Descargas | Resuelto (v0.79.7): solo se ve el último byte; test |
| AUD-105 | P3 | Dos puntos por defecto distintos (clima y mapa; búsqueda desde el celular y "Mis lugares"), a unos 12 km de la casa del autor | Resuelto (v0.79.7): un solo punto, la casa del autor (Tafí Viejo), por decisión suya |
| AUD-086 | P2 | `resources.arsc` con 88 idiomas de las librerías (4,23 MB) | Resuelto (v0.79.7): castellano e inglés; APK −3,30 MB medido (24,43 → 21,13 MB) |
| AUD-087 | P2 | 32 clips de Piper que nunca suenan (0,43 MB) | Descartado por decisión del autor (2026-11-06): ahorra poco y se pierde el respaldo si falta una voz |
| AUD-119 | P2 | El punto azul de los dos mapas se actualizaba como mucho cada 2 s o 5 m (el velocímetro y la guía, cada 1 s): a 40 km/h saltaba ~22 m. Lo contó el autor andando | Resuelto (v0.79.7): 1 s y sin mínimo de metros; falta R-89 en el auto |
| AUD-120 | P2 | Hasta la primera posición del GPS el mapa queda en la última conocida (la casa del autor) sin avisar, y las rutas salen de ahí sin decirlo | Resuelto (v0.79.7): cartel "Buscando GPS… / Sin señal de GPS hace N s" y ruta "desde la última posición conocida"; línea de GPS en la Prueba completa; falta R-89 |
| AUD-121 | P2 | El aviso de luces apagadas no avisaba por debajo de 20 km/h ni de día por debajo de 60; acá es obligatorio andar con luces siempre. Pedido del autor | Resuelto (v0.79.7): desde 2 km/h, de día y de noche, a los 5 s; falta R-90 en el auto |
| AUD-093 | P3 | Redirección de Spotify sin `state`: cualquier app podía mandar un código | Resuelto (v0.79.6); falta probar la conexión real (R-88) |
| AUD-100 | P3 | Reconexión de la radio pendiente que corría sobre el reproductor liberado | Resuelto (v0.79.6); test que la reprodujo |
| AUD-118 | P2 | `Net.getBytes` tiraba una excepción con un enlace que Java no puede leer (logos de directorios de radios), sin que nadie la atrapara: cerraba la app al mostrar esa radio. Lo encontró un test nuevo | Resuelto (v0.79.6) |
| Resto (AUD-076 a 079, 082, 084, 091, 094, 095, 097 a 099, 102, 107 a 117; el número AUD-090 no se usó) | P2–P3 | Ver el informe | Abiertos: fases 2 y 3 o decisión del autor (`TODO.md`) |

---

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González. Licencia AGPL-3.0 (desde v0.56.0).
