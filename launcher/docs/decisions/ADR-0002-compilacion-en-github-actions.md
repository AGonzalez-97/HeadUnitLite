# ADR-0002 — Compilación y capturas en GitHub Actions

**Fecha:** 2026-10-25 · **Estado:** Accepted

## Contexto
El autor solo cuenta con un celular; no tiene una computadora con Android Studio. El entorno de desarrollo asistido no puede descargar el Android SDK.

## Decisión
Compilar en GitHub Actions en cada push a `main`, generar capturas de las pantallas reales con Robolectric (gráficos nativos) y Roborazzi, y publicar APK y capturas en GitHub Releases.

## Motivo
Permite instalar desde el celular y ver la interfaz real sin dispositivo ni emulador.

## Alternativas consideradas
- Android Studio local (no disponible).
- Emulador en la CI (más lento y frágil).
- Maquetas de diseño (descartadas por no reflejar el código real).

## Consecuencias
### Positivas
- Cada versión queda publicada con su APK y sus capturas: trazabilidad.
### Negativas
- Cada build tarda unos 12 minutos.
- Las capturas no verifican nada (no hay comparación ni aserciones).
- Las animaciones infinitas trabaron la CI una vez (build 12); se apagan con `RenderFlags`.
