# ADR-0013 — APK solo para la radio (32 bits) y CI en paralelo

**Estado:** Aceptada · **Fecha:** 2026-11-04 · **Versión:** v0.68.0 · **Autor:** Axel Hernán González

## Contexto

Pregunta del autor (2026-11-04): si se puede bajar el peso del APK (40,5 MB en el build 107) y el
tiempo hasta publicar una versión (~11,5 min).

- **Peso:** el APK traía código nativo para dos arquitecturas: `armeabi-v7a` (32 bits) y
  `arm64-v8a` (64 bits). La radio del autor (NWD, Allwinner T3, cuatro Cortex-A7) es **solo de 32
  bits**: nunca carga las librerías de 64. Desde minSdk 23 Android guarda las librerías nativas
  sin comprimir dentro del APK, así que pesan completas. Medido en los AAR: MapLibre 10,2 MB y
  Conscrypt 2,9 MB en 64 bits, más FFmpeg de DiAuto (2,6 MB, que solo existe en 64 bits) y lo que
  compilan los motores: unos 16 MB sin uso.
- **Tiempo:** el paso más largo es compilar la release (8 min): R8 sobre la app y los motores, y el
  código nativo de los motores (CMake / ndk-build) para 3–4 arquitecturas cada uno, aunque la app
  después descartaba casi todas. Los tests de lógica (2 min) corrían antes, en la misma máquina.

## Decisión

1. El APK lleva solo `armeabi-v7a` (propiedad `onix.abis`, por defecto `armeabi-v7a`).
2. Desde `build.gradle.kts` de la raíz, los módulos del motor compilan su código nativo solo para
   esas arquitecturas (`finalizeDsl`), **sin tocar `vendor/`**.
3. La CI corre en paralelo, en máquinas distintas: tests de lógica, APK y capturas. Si falla un test
   de lógica no se publica (igual que antes: ADR-0007).
4. Las notas de cada release dicen el tamaño del APK.

## Consecuencias

- **Medido en el build 109:** APK de 38,7 MB a **23,1 MB** (−40 %); publicación de 11 min 41 s a **8 min 56 s**. Menos espacio en la radio.
- Sin cambios en la radio: ya usaba las librerías de 32 bits. El decodificador HEVC de FFmpeg de
  DiAuto solo existe en 64 bits: en la radio ya no estaba (Android Auto usa H.264 por hardware).
- **No se instala en celulares solo de 64 bits** (algunos de 2023 en adelante). Para eso:
  compilar con `-Ponix.abis=armeabi-v7a,arm64-v8a`.
- Los tests no se quitan ni se saltean: siguen siendo bloqueantes.
