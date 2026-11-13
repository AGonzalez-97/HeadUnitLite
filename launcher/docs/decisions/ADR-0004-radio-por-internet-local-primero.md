# ADR-0004 — Radio FM por internet con prioridad a la señal local

**Fecha:** 2026-10-26 · **Estado:** Accepted

> **Actualización (auditoría 2026-11-05):** desde v0.51.0 el sintonizador FM de la radio NWD sí se usa desde la app (`NwdTuner`, Binder `com.nwd.radio.service`; interruptor ANTENA / INTERNET). Esta decisión sigue valiendo para la radio por internet; la frase "no es accesible desde una app" ya no es cierta en este head unit.

## Contexto
El autor quiere una radio con interfaz de FM y emisoras de Tucumán, con prioridad absoluta a las señales locales. El sintonizador FM del head unit no es accesible desde una app.

## Decisión
Reproducir por internet con Media3 en un servicio en segundo plano, con un dial de las FM reales de San Miguel de Tucumán. Cada emisora tiene una cadena de enlaces candidatos (`page:`, listas, `nac:`, `lookup:`), y el interruptor LOCAL / NACIONAL decide el orden.

## Motivo
Los enlaces de transmisión cambian y muchos no son públicos. La cadena permite usar los enlaces confirmados, sacar el local de páginas oficiales y buscar en Radio Browser como respaldo, sin quedarse en silencio.

## Alternativas consideradas
- Solo el directorio Radio Browser (casi no tiene radios de Tucumán).
- Enlaces fijos para cada emisora (no se pudieron confirmar todos).
- Usar la radio FM del head unit (no tiene API para apps de terceros).

## Consecuencias
### Positivas
- Muchas emisoras suenan sin mantenimiento manual.
### Negativas
- Dependencia de páginas de terceros (riesgo AUD-003) y de enlaces que pueden cambiar.
- Necesita datos móviles.
- Riesgo de carrera al re-sintonizar (AUD-007).
