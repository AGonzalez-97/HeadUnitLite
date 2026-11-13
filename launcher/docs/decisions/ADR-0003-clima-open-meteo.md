# ADR-0003 — Clima con Open-Meteo

**Fecha:** 2026-10-25 · **Estado:** Accepted

## Contexto
El fondo y los faros cambian con el clima real, y el inicio muestra clima y pronóstico.

## Decisión
Usar la API gratuita de Open-Meteo, consultada cada 15 minutos con la última ubicación conocida (San Miguel de Tucumán por defecto).

## Motivo
No pide registro ni clave, permite uso personal no comercial y da datos actuales y pronóstico en una sola llamada.

## Alternativas consideradas
- Meteored (requiere clave de afiliado; planes no verificados). El autor decidió mantener Open-Meteo.
- OpenWeatherMap (requiere clave).

## Consecuencias
### Positivas
- Sin claves que proteger.
### Negativas
- Los datos están bajo CC BY 4.0 y requieren atribución (pendiente: AUD-022).
- Es un pronóstico por modelo, no una medición local.
