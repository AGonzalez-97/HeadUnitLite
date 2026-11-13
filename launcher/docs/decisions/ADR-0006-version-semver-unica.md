# ADR-0006 — Versión SemVer en `version.properties`

**Estado:** Aceptada · **Fecha:** 2026-10-26 · **Versión:** v0.17.0 · **Autor:** Axel Hernán González

## Contexto

Hasta v0.16.0 la app armaba su versión con el número de compilación (`0.<build>`), así que `0.16` en la app no coincidía con la nomenclatura SemVer de la documentación (AUD-020).

## Decisión

- La versión SemVer vive solo en `version.properties` (`version=0.17.0`), que lee `app/build.gradle.kts`.
- `versionCode = 100 + GITHUB_RUN_NUMBER`, para que cada compilación se pueda instalar encima.
- `BuildConfig.BUILD_NUMBER` lleva el número de compilación; la app muestra `0.17.0 · build N`.
- Cada compilación tiene su release `build-N`; cada versión se marca con un tag de git `vX.Y.Z`, que crea la CI sobre la primera compilación de esa versión (desde v0.18.0).

## Consecuencias

- Una sola fuente: cambiar de versión es editar una línea.
- Varias compilaciones pueden compartir la misma versión SemVer; las distingue el número de build.
- La CI lee la versión para nombrar la release.
