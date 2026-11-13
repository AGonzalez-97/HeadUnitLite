# ADR-0011 — Mapa 3D con MapLibre 10.x y paquetes propios

**Estado:** Aceptada · **Fecha:** 2026-10-28 · **Versión:** v0.47.0 · **Autor:** Axel Hernán González

## Contexto

El autor quiere un mapa propio en 3D, sin Google Maps ni Waze, parecido a la idea de Horizon-Maps
(navegador para radios con paquetes por país armados en GitHub Actions). Ese proyecto no tiene
licencia (no se puede copiar su código) y usa Mapbox, que pide clave, cobra por uso, OpenGL ES 3.0 y
Android 8. La radio tiene Android 7.1, OpenGL ES 2.0 y procesador de 32 bits (armeabi-v7a).

## Decisión

- **Motor:** MapLibre Native Android **10.3.7** (último de la serie 10, OpenGL ES 2.0; la 11 pide 3.0).
- **Datos, etapa 1:** mosaicos vectoriales de OpenFreeMap (gratis, sin clave, esquema OpenMapTiles),
  con un estilo oscuro propio: calles por categoría, edificios en 3D (`fill-extrusion` con la altura
  de OpenStreetMap), nombres en castellano. Cámara inclinada 45° (60° navegando) y botón 2D/3D.
- **Respaldo:** si MapLibre no arranca o el estilo no carga, se usa el mapa clásico (osmdroid). Ambos
  implementan la interfaz `NavMap`.
- **Etapa 2 (sin internet):** paquete de Tucumán con el mismo esquema, armado en GitHub Actions desde
  los datos de OpenStreetMap (Geofabrik) y publicado en un **repositorio público aparte** (decisión
  del autor, 28/10), solo con datos y la atribución de OpenStreetMap. Después: búsqueda y rutas sin
  internet, límites de velocidad.
- APK solo para ARM (`armeabi-v7a`, `arm64-v8a`).

## Consecuencias

- El APK crece (código nativo de MapLibre).
- Rendimiento por medir en la radio (Diagnóstico → Fluidez con el mapa abierto).
- OpenFreeMap es un servicio gratuito sin garantías; por eso la etapa 2 es importante.
