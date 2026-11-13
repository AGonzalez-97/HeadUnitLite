# Fase 1 de la auditoría 2026-11-05 (bajo riesgo)

**Origen:** `docs/audits/AUDIT-2026-11-05.md`, sección J (Fase 1). Aprobada por el autor el 2026-11-05 ("dale").
**Rama:** `audit/fase-1` (commits locales; el push y el merge a `main` los decide el autor; la regla 8 de `CLAUDE.md` queda suspendida para esta fase).
**Versión:** todo junto sale como **v0.79.5** en el último commit de la fase; mientras tanto, cada cambio se anota en `CHANGELOG.md` → `[Unreleased]`.
**Pruebas en la PC:** JDK 21 + SDK de Android instalados el 2026-11-05. Línea base local: `:app:testDebugUnitTest` 205/205 (igual que el build 146).

## Pasos

- [x] 1. CI más segura (AUD-073, AUD-074, AUD-088): `carlink.yml` pasa la versión por `env:` y la valida; `build.yml` con `contents: read` (solo `publicar` escribe); acciones de terceros fijadas por SHA; `mapping.txt` de R8 en el artefacto y en la release.
- [x] 2. Quitar del APK los recursos que no usa nadie (AUD-071, AUD-113 seguros): BouncyCastle `pqc/**` y `CertPathReviewerMessages*`, `google/protobuf/*.proto`, `DebugProbesKt.bin`.
- [x] 3. OBD2: que una cancelación no deje el Bluetooth apagado ni se cuente como falla; cerrar el socket que queda huérfano después del tope (AUD-072, AUD-080), con tests.
- [x] 4. Que cada lectura del OBD2 o de la música no recomponga toda la pantalla si nada cambió (AUD-075).
- [x] 5. Robustez chica: test de las clases de los motores (AUD-081), Prueba completa sin doble arranque (AUD-103), `redact` en `AppLog.info` (AUD-104), código muerto (AUD-106). **AUD-105 (constantes de ubicación) queda para el autor:** las dos coordenadas difieren ~1,6 km (`-65.2038` vs `DEFAULT_LON = -65.22`) y unificarlas cambia el punto de búsqueda de TomTom o el del clima; hay que elegir cuál es la buena.
- [x] 6. Mi auto: no pisar el historial si el JSON guardado no se pudo leer; el kilometraje del OBD2 se aplica en el hilo principal (AUD-101).
- [x] 7. Cobertura real de JaCoCo con Robolectric (AUD-083).
- [x] 8. Documentación al día (AUD-089, sección L del informe) y versión v0.79.5.

## Evidencia

| Paso | Qué se verificó | Resultado |
|---|---|---|
| 1 | YAML válido (pyyaml); permisos por trabajo; regex probada con `1.0.10`, `2.1` (acepta) y `1.0.10$(id)`, `1.0;rm -rf /`, `v1.0`, vacío (rechaza) | OK en la PC. **Falta:** que la CI corra con estos workflows (al subir la rama a `main`) |
| 1 | `mapping.txt` medido en la compilación local: 115.825.162 B; con gzip -9: 8.833.395 B | Se sube comprimido (`.txt.gz`) |
| 2 | `:app:assembleRelease` local (7 min 24 s) y comparación entrada por entrada con el APK del build 146 | Se fueron exactamente las 17 entradas excluidas: **−1.264.599 B**; ninguna otra entrada cambió más de 20 KB. (Las 2 de `assets/offline-mfi/` faltan porque la identidad de CarPlay solo la arma la CI.) **Falta en el auto:** CarPlay por cable ("Confiar" y proyección) con el APK nuevo |

| 3 | RED: los 3 tests nuevos no compilaban (funciones inexistentes). GREEN: `ObdRecoveryTest` 9/9. Mutación: sin `NonCancellable`, `reinicio_del_bluetooth_cancelado_a_la_mitad…` falla. Suite completa local: **208/208** | OK en la PC. **Falta en el auto:** R-84 sigue abierto; con el lector desconectado y 2+ fallas, abrir Android Auto o cortar el contacto y ver que el Bluetooth queda prendido |

| 4 | RED: `lectura_igual_no_cambia_el_estado` falla con `class`. GREEN con `data class`; ningún código compara `ObdData` por identidad (solo `== null`). `MediaInfo`/`MediaResult` **no se cambian**: `readAt` difiere en cada lectura, no habría beneficio. Suite local **209/209** | OK en la PC. **Falta en el auto:** `top` con el OBD2 en ralentí antes/después (sección K) |

| 5 | RED: `tryBegin` no existía. GREEN: `la_prueba_completa_no_arranca_dos_veces` y `las_pantallas_de_los_motores_existen` (las dos clases del vendor se encuentran sin inicializarlas). Código muerto: sin usos en `app/src` antes de borrarlo. Suite local **211/211** | OK en la PC |

| 6 | RED: `CarCareStorageTest` no compilaba. GREEN 4/4 (Robolectric: en JVM pura `org.json` es un stub que nunca falla). Comportamiento igual al anterior salvo la copia: dañado → vacío, como antes. `check_docs.py` OK. Suite local **215/215** | OK en la PC |

| 7 | Reproducido en la PC: con los tests de lógica (igual que la CI) Spotify/PhoneShare/NearbyPlaces 0 %, total 16,5 %. Causa: la extensión de JaCoCo no existía al configurar los tests (`findByType` → null). Con `jacoco` en `plugins`: Spotify 36,2 %, PhoneShare 30,8 %, NearbyPlaces 24,1 %, MapNav 18,6 %, **total 20,4 %**. Suite completa OK con el plugin | OK en la PC. La CI va a informar ~20 % desde v0.79.5 (no es que haya más tests) |

| Final | `check_docs.py`: "Documentación al día para v0.79.5". `:app:testDebugUnitTest` **215/215**. `:app:assembleRelease` OK (4 min 4 s): APK de 24.433.873 B con la versión 0.79.5 en el manifiesto, **−1.267.798 B** frente al del build 146 (solo faltan las 17 entradas excluidas y las 2 de `offline-mfi`, que agrega la CI) | OK en la PC |
| 8 | Documentación: ARCHITECTURE (módulos, flujos, CI, ADR, límites), README (versión, navegación, módulos, servicios, estructura, tests medidos, compilación local), TODO, ESTADO (orden de la tabla), TEST_PLAN, AUDIT_REPORT §26, ADR-0004 y ADR-0009, `vendor/ONIX_CHANGES.md`, comentarios viejos en `Obd.kt` y `LauncherApp.kt`, `.gitignore` (`.cxx/`). Versión **v0.79.5** | Ver la verificación final abajo |

**Resuelto en el paso 8 (antes pendiente):** borrar `openApp` de la fila de `Ui.kt` en `ARCHITECTURE.md`; sumar los tests nuevos a `docs/TEST_PLAN.md` y a la tabla de tests de `README.md`; agregar `.cxx/` a `.gitignore` (la compilación nativa local deja `vendor/diauto/app/.cxx/`).

## Siguiente paso

Fase 1 terminada y subida a `main` (build 147). **CI confirmó** (run 42146271419): los 4 trabajos OK; 215/215; cobertura 20,4 % (igual que en la PC); firma verificada; identidad de CarPlay desde CarLink 1.0.10 con huella verificada; APK 24.434.530 B con `offline-mfi/` y sin los 17 archivos excluidos; `onix-launcher-mapping.txt.gz` (8,8 MB) en la release. Los workflows nuevos (AUD-073/074/088) corrieron bien.

**Decide el autor:**
1. Instalar `build-147` encima y probar CarPlay por cable primero, después el OBD2 (R-87, R-84). Si CarPlay falla: volver a v0.79.4 recompilando su código como build nueva (`build-146` no se instala encima).
2. Las decisiones de `TODO.md` (AUD-086, 087, 105, 070) y si se arranca la fase 2.
