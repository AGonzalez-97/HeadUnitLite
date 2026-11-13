# Auditoría de estado inicial — Onix Launcher

**Fecha:** 2026-10-26 · **Versión de partida:** v0.16.0 (build 16, commit `fff2181`)
**Autor del proyecto:** Axel Hernán González

> **Actualización v0.17.0 (2026-10-26):** las correcciones de la auditoría ya se aplicaron. El estado actual está en [`VERSION.md`](VERSION.md), la arquitectura vigente en [`ARCHITECTURE.md`](ARCHITECTURE.md) y el seguimiento de cada hallazgo en la sección 19 de [`AUDIT_REPORT.md`](AUDIT_REPORT.md). Este documento se conserva como registro histórico.

Este documento registra el punto de partida antes de adoptar el sistema de versionado y documentación. Los hallazgos detallados, con evidencia por archivo y línea, están en [`AUDIT_REPORT.md`](AUDIT_REPORT.md).

---

## 1. Estructura actual

```text
onix-launcher/
├── .github/workflows/build.yml      Compilación release + capturas + publicación en Releases
├── app/
│   ├── build.gradle.kts             Dependencias, firma, R8, número de versión
│   ├── proguard-rules.pro
│   ├── keystore.properties          ⚠ secretos versionados (AUD-001)
│   ├── release.jks                  ⚠ keystore versionado (AUD-001)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/axel/onixlauncher/   15 archivos Kotlin
│       │   └── res/drawable-nodpi/          fotos y capas (auto, faros, fondos, ruta, emblema)
│       └── test/.../ScreenshotTest.kt       7 capturas renderizadas con Robolectric + Roborazzi
├── tools/                           Scripts Python que generan las imágenes
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── README.md
```

## 2. Módulos

| Archivo | Responsabilidad | Líneas |
|---|---|---|
| `MainActivity.kt` | Activity única, pantalla completa | 20 |
| `LauncherApp.kt` | Estado global, páginas, inicio, dock, diagnóstico, GPS, utilidades | 930 |
| `DashboardScreen.kt` | Tablero con velocímetro y tacómetro | 415 |
| `EnginePanel.kt` | Panel de motor del inicio | 219 |
| `RadiosScreen.kt` | Interfaz de radio FM | 434 |
| `Radio.kt` | Modelo de emisora, directorio, servicio de reproducción | 345 |
| `TucumanFm.kt` | Dial de FM de San Miguel de Tucumán | 90 |
| `Weather.kt` | Clima de Open-Meteo | 112 |
| `Media.kt` | Lectura de la sesión de música activa | 59 |
| `Ambient.kt` | Ambiente según hora y clima | 64 |
| `Road.kt` | Línea central animada de la ruta | 82 |
| `PanelLayout.kt` | Paneles movibles y escalables | 167 |
| `Drawing.kt` | Íconos y dibujos vectoriales | 383 |
| `MediaListenerService.kt` | Servicio vacío para el permiso de notificaciones | 9 |

## 3. Dependencias

Compose BOM 2024.06.00, Material 3, activity-compose 1.9.0, core-ktx 1.13.1, Media3 1.3.1 (ExoPlayer + Session). Tests: JUnit 4.13.2, Robolectric 4.12.2, Roborazzi 1.20.0, ui-test-junit4. Build: AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7.

Servicios externos: Open-Meteo (clima), Radio Browser (directorio de radios), transmisiones de cada emisora, página del reproductor de Cienradios.

## 4. Configuración

- `minSdk 24`, `targetSdk 34`, release con R8 y reducción de recursos.
- Versión: `versionCode = 100 + GITHUB_RUN_NUMBER`, `versionName = "0.<build>"`.
- Firma fija desde `app/keystore.properties`.
- Permisos: ubicación, internet, servicio en primer plano (media), notificaciones.
- `usesCleartextTraffic="true"`.

## 5. Tests

7 tests de captura de pantalla (inicio en 4 ambientes, tablero, radio, modo edición). Pasan en CI (build 16), pero no tienen aserciones. No hay tests de lógica. Cobertura no medida.

## 6. Documentación existente

Solo `README.md`, desactualizado (AUD-021). No había CHANGELOG, VERSION, ARCHITECTURE, TODO, autoría ni licencias.

## 7. Problemas detectados (resumen)

0 CRITICAL · 1 HIGH · 10 MEDIUM · 12 LOW · 4 INFORMATIONAL. El principal es el keystore y las contraseñas en el repo (AUD-001). Detalle en `AUDIT_REPORT.md`.

## 8. Deuda técnica

Secretos en el repo, falta de tests de lógica, `LauncherApp.kt` monolítico, lectura de música por sondeo, código muerto.

## 9. Riesgos

Filtración del keystore si el repo deja de ser privado; fluidez y compatibilidad no verificadas en el head unit; uso de imágenes de terceros fuera del ámbito personal.

## 10. Oportunidades de mejora

Pantalla "Acerca de" con autoría y atribuciones; tests de lógica en la CI; dividir `LauncherApp.kt` antes de sumar OBD2; logs para diagnosticar en el auto.

## 11. Versión inicial recomendada

**v0.16.0, en estado Beta.** El número 16 continúa la numeración que ya muestra la app (`0.16`), así no hay un salto confuso. Sigue en 0.x porque falta la funcionalidad central (OBD2) y no se probó en el equipo de destino. La v1.0.0 queda para cuando funcione OBD2 y esté validada en el head unit.

## 12. Roadmap propuesto

| Versión | Contenido |
|---|---|
| v0.16.1 (PATCH) | AUD-007, AUD-013, AUD-008, AUD-025 · código muerto (AUD-014) |
| v0.17.0 (MINOR) | Pantalla "Acerca de" (autoría y atribución Open-Meteo) · configuración de red (AUD-002) · tests de lógica (AUD-016) · SemVer explícito en Gradle (AUD-020) |
| v0.18.0 (MINOR) | Dividir `LauncherApp.kt` (AUD-015) · música por eventos (AUD-005) |
| v0.19.0 (MINOR) | OBD2 con ELM327: temperatura, RPM, voltaje, velocidad; combustible si el auto lo informa |
| v1.0.0 (MAJOR) | Validada en el head unit, con OBD2 funcionando |

El roadmap es una propuesta. Las tareas oficiales están en [`TODO.md`](TODO.md).

---

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González. Todos los derechos reservados.
