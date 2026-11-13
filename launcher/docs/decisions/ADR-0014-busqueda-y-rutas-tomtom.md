# ADR-0014 — Búsqueda y rutas con TomTom (sin OpenStreetMap)

**Estado:** Aceptada · **Fecha:** 2026-11-05 · **Versión:** v0.75.0 · **Autor:** Axel Hernán González

## Contexto

- El buscador (Nominatim y Photon, datos de OpenStreetMap) no encontraba direcciones con número de
  Tucumán: "9 de Julio 550, Tafí Viejo" y "Av. Adolfo de la Vega 473" devolvían solo la calle,
  porque OpenStreetMap no tiene esa numeración cargada.
- Las rutas (OSRM y FOSSGIS) fallaban en la radio por la conexión segura (HU-013).
- El autor descartó Android Auto para navegar ("tiene que ser nativo, sin cable") y pidió quitar
  OpenStreetMap "ni como respaldo".
- Prueba en la CI (05/11/2026, `tools/tomtom/prueba.py`, workflow `tomtom_prueba.yml`): TomTom
  encontró las dos direcciones con la altura exacta (coincidencia 0,91 y 0,98) y calculó las rutas
  con tránsito (14,0 km / 22 min y 5,9 km / 13 min desde Plaza Independencia).
- Alternativas: Google (Places y Routes) pide cuenta de Google Cloud con tarjeta; Horizon Nav y
  Mapbox v11 piden Android 8 y OpenGL ES 3.0 (la radio tiene 7.1.1 y 2.0).

## Decisión

1. Búsqueda (`search/2/search`), rutas (`routing/1/calculateRoute`, con tránsito) y nombres de lugares
   (`reverseGeocode`) con TomTom, en `NavApi` (`MapNav.kt`). Se quitan Nominatim, Photon, OSRM y FOSSGIS.
2. La clave es del autor (plan gratuito, 2.500 pedidos por día, sin tarjeta): GitHub Secret
   `TOMTOM_API_KEY`, que entra a `BuildConfig.TOMTOM_KEY` al compilar. Nunca está en el repositorio.
   Las releases son privadas (AUD-067). El registro de la app tapa `key=` y `token=` (`AppLog.redact`).
3. Las maniobras de TomTom se traducen a los nombres que ya usaban la guía y la voz (`NavApi.maneuver`).
4. Si la conexión normal falla, se reintenta por TLS 1.3 directo (`DirectHttps`, HU-013).
5. El dibujo del mapa no cambia: mosaicos de OpenStreetMap (osmdroid) y OpenFreeMap (MapLibre).

## Consecuencias

- Las direcciones de Tucumán con altura se encuentran; las rutas traen tránsito en vivo.
- Sin el secreto, el buscador dice "Falta la clave de TomTom". Sin internet no hay búsqueda ni rutas
  (igual que antes).
- La ubicación y el destino se envían a TomTom (antes a Nominatim y OSRM).
