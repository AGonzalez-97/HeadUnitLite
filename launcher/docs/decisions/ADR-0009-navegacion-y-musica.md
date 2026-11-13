# ADR-0009 — Pantalla de navegación y música dentro del launcher

**Estado:** Aceptada; parcialmente reemplazada por ADR-0011 (mapa 3D con MapLibre), ADR-0014 (búsqueda y rutas con TomTom en vez de Nominatim y OSRM) y ADR-0015 (el Client ID de Spotify puede venir de un secreto de GitHub en vez de pegarse en el equipo) · **Fecha:** 2026-10-27 · **Versión:** v0.34.0 · **Autor:** Axel Hernán González

## Contexto

El autor pidió reemplazar el Tablero por una pantalla de navegación y música donde se pueda hacer
todo sin abrir otras apps. Android no permite meter la pantalla de Google Maps o Waze dentro de
otra app, y la guía por voz giro a giro solo la dan esas apps.

## Decisión

- Mapa propio con osmdroid (OpenStreetMap, sin claves), buscador con Nominatim y ruta con OSRM.
  "Maps" y "Waze" abren la guía por voz hacia el destino elegido (elegido por el autor).
- Música con los controles de la sesión de la app que suena (`MediaController`) y la radio de la app.
- Las listas, recientes y el buscador de Spotify necesitan su API web con una app registrada por el
  autor (elegido por el autor); se hacen en una versión aparte y el identificador no va al repo.
- En esta pantalla el pager no se desliza, para que el dedo mueva el mapa.

## Consecuencias

- Dependencia nueva (osmdroid, Apache 2.0) y tres servicios sin clave con política de uso liviano.
- La ubicación y el destino buscado se envían a Nominatim y OSRM.
- El Tablero se elimina; los datos del motor quedan en el panel de Inicio.

## Actualización v0.35.0

El autor tiene Spotify Premium. La biblioteca usa la API web de Spotify con PKCE (sin secreto de
cliente) y reproduce con Spotify Connect en el dispositivo de la radio. El Client ID lo pega el
autor en el equipo; ni el Client ID ni los tokens se guardan en el repositorio.

## Actualización v0.37.0

El autor no quiere depender de Google Maps ni Waze. La guía giro a giro es propia: maniobras de
OSRM, seguimiento con el GPS, avisos con el TextToSpeech del equipo y recálculo al salirse del
camino. El mapa sigue siendo osmdroid (2D, gira con el rumbo). La vista 3D necesita MapLibre
(OpenGL); se decide según la versión de OpenGL ES que informe la radio en Diagnóstico.
