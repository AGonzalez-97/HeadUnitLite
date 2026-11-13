# Tarea: Android Auto y CarPlay dentro de Onix

Decisión y riesgos: [ADR-0012](../decisions/ADR-0012-android-auto-carplay.md).

## Alcance
Android Auto y CarPlay por USB dentro del launcher, con el motor de CarLink (`carlink-core`, AGPL-3.0).

## Pasos
- [x] v0.54.0: botón "Auto/CarPlay" en el dock que abre CarLink (puente)
- [x] v0.55.0: herramientas de compilación al día (Gradle 9.5, AGP 9.3, Kotlin 2.2, compileSdk 37, Compose 2026) y minSdk 25; capturas iguales
- [x] v0.56.0: copiar `carlink-core` y `vendor/diauto` + `vendor/diplay`; licencia AGPL; pantalla de proyección propia; Android Auto por USB
- [x] v0.57.0 (código): CarPlay con `DIPLAY_AUTH_ASSETS_DIR` armado en la CI desde GitHub Secrets (`CARPLAY_IDENTITY_PK8`, `CARPLAY_CERTIFICATE_P7B`, en base64)
- [ ] Probar en el auto: Android Auto y CarPlay por USB

## Evidencia
- CI: pendiente.
- Auto: CarLink (app aparte) funciona por USB (autor, 2026-11-03).

## Siguiente paso
Desde v0.58.0 la identidad se arma sola en la CI. El autor prueba R-48 (Android Auto) y R-50 (CarPlay) en el auto.
