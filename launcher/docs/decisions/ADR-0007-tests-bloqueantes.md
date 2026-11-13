# ADR-0007 — Tests de lógica bloqueantes y capturas informativas

**Estado:** Aceptada · **Fecha:** 2026-10-26 · **Versión:** v0.17.0 · **Autor:** Axel Hernán González

## Contexto

Hasta v0.16.0 solo había capturas sin aserciones, y no bloqueaban la release (AUD-016, AUD-017).

## Decisión

- Los tests de `com.axel.onixlauncher.logic` corren antes de compilar la release. Si uno falla, no se publica nada.
- Las capturas (`ScreenshotTest`) siguen sin bloquear (límite de 15 minutos): sirven para revisar la interfaz desde el celular.
- Cada release incluye `tests.txt` con los resultados reales de esa compilación.

## Consecuencias

- Una regresión en la radio, el clima o el ambiente frena la publicación.
- Los resultados que se informan salen siempre de `tests.txt`, nunca de estimaciones.
