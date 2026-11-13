# ADR-0001 — Interfaz en Jetpack Compose sobre un lienzo fijo de 1024x600

**Fecha:** 2026-10-25 · **Estado:** Accepted

## Contexto
El launcher apunta a un head unit con pantalla horizontal de 1024x600 (resolución probable), pero se prueba en celulares con otras proporciones.

## Decisión
Escribir la interfaz en Kotlin con Jetpack Compose y diseñar todo sobre un lienzo fijo de 1024x600 dp que se escala, con `graphicsLayer`, para entrar en la pantalla real.

## Motivo
Lo que se ve en las capturas es lo que se ve en el auto, sin rediseñar para cada pantalla, y las posiciones se pueden fijar con precisión, como en un tablero de auto.

## Alternativas consideradas
- Diseño adaptable (responsive) con layouts flexibles.
- Vistas clásicas de Android (XML).
- Flutter.

## Consecuencias
### Positivas
- Diseño exacto y predecible; las capturas automáticas son fieles.
### Negativas
- En pantallas con otra proporción quedan bandas a los costados.
- Las coordenadas están escritas en el código (números mágicos de diseño).
