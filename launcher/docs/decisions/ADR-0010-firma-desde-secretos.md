# ADR-0010 — Firma desde GitHub Secrets con respaldo en el archivo

**Estado:** Aceptada (opción A, 2026-10-28; falta cargar los secretos) · **Fecha:** 2026-10-28 · **Versión:** v0.38.1 · **Autor:** Axel Hernán González

## Contexto

La clave de firma está en el repo privado (ADR-0005, AUD-001). Cambiarla obliga a desinstalar la app.

## Decisión

- `app/build.gradle.kts` toma la firma de variables de entorno (`ONIX_STORE_FILE`, `ONIX_STORE_PASSWORD`, `ONIX_KEY_ALIAS`, `ONIX_KEY_PASSWORD`); si no están, de `app/keystore.properties`.
- La CI arma esas variables desde los secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD` si existen.
- Se usa la **misma clave** (opción A en `docs/SIGNING.md`). Rotarla (opción B) queda a decisión del autor.

## Consecuencias

- La CI verifica el certificado del APK contra `.github/signing-cert.sha256` y frena si no coincide.
- Hasta que el autor cargue los secretos, se sigue firmando con el archivo.
- Cuando el autor los cargue, los archivos pueden salir del repo sin afectar las instalaciones.
