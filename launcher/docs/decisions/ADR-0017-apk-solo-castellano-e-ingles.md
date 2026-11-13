# ADR-0017 - APK solo con castellano e inglés

**Estado:** Aceptada · **Fecha:** 2026-11-06 · **Versión:** v0.79.7 · **Autor:** Axel Hernán González

## Contexto

- La auditoría técnica del 2026-11-05 midió el APK de la compilación 146: `resources.arsc` pesaba 4,23 MB (el 16,5 % del APK) y traía 88 idiomas que vienen de las librerías (AppCompat, Material, Media3, los motores de Android Auto y CarPlay…).
- Onix no tiene textos traducidos: todos los de la interfaz están en el código, en castellano. La radio está en castellano de Argentina.
- ADR-0013 ya había achicado el APK (solo 32 bits) y R8 recorta clases y recursos sin uso, pero no los idiomas.

## Decisión

1. `androidResources { localeFilters += listOf("es", "en") }` en `app/build.gradle.kts`. Lo que no tiene idioma (`values/`) siempre queda.
2. Castellano porque es el idioma de la radio y del autor. Inglés como respaldo: las pantallas de los motores (ajustes de DiAuto y DiPlay) salen en inglés si el sistema se pusiera en un idioma que no está.
3. No se tocan los motores ni `vendor/` (ADR-0012): el filtro actúa al armar el APK de Onix.

## Consecuencias

- **APK 3,30 MB más chico, medido** compilando la versión release: de 24.434.526 B (CI, build 148) a 21.134.693 B; `resources.arsc` de 4.232.440 a 933.256 B. La única otra diferencia con el APK de la CI son los dos archivos de la identidad de CarPlay, que solo agrega la CI.
- El selector de idioma de DiAuto (`BuildConfig.AVAILABLE_LOCALES`, 22 idiomas) los sigue mostrando, pero los que no están caen al inglés.
- Si algún día se agrega un idioma a la interfaz, hay que sumarlo a esta lista.
- Se descartó, por poco ahorro (0,43 MB) y por perder el respaldo si falta una voz, dejar de empaquetar las voces de Piper (AUD-087).
