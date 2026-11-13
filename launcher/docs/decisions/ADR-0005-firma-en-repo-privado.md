# ADR-0005 — Clave de firma guardada en el repositorio privado (temporal)

**Fecha:** 2026-10-26 · **Estado:** Accepted (temporal, a revisar)

## Contexto
Hacía falta una firma fija para actualizar la app sin desinstalar. El entorno asistido no pudo cargar secretos en GitHub (la API de secretos estaba bloqueada).

## Decisión
Generar un keystore propio y guardarlo, con sus contraseñas, en `app/` dentro del repositorio privado.

## Motivo
Era la única forma disponible en ese momento de tener una firma estable en la CI.

## Alternativas consideradas
- GitHub Secrets (no se pudo cargar desde el entorno; el autor lo puede hacer desde la web).
- Seguir con la firma de debug (obliga a desinstalar en cada versión).

## Consecuencias
### Positivas
- Actualizaciones sin desinstalar desde v0.13.0.
### Negativas
- Secreto expuesto a cualquiera con acceso al repo (AUD-001, HIGH). No se debe hacer público el repo sin rotar la clave.
