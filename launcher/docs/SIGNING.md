# Firma de la app

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González

Android solo instala una versión nueva **encima** de la anterior si las dos están firmadas con la
**misma clave**. Por eso la clave no se cambia a la ligera.

## Estado actual

> **Versión pública:** `app/release.jks` y `app/keystore.properties` no están en este repositorio. Para compilar una versión firmada hay que generar una clave propia y pasarla por variables de entorno o GitHub Secrets. La descripción de abajo corresponde al repositorio privado original.

- La clave (`app/release.jks`) y sus datos (`app/keystore.properties`) están en el repo **privado** (AUD-001, riesgo aceptado).
- Desde v0.38.1 la compilación acepta la clave desde **GitHub Secrets**. Si no están cargados, usa el archivo como siempre.
- **Decisión del autor (2026-10-28): opción A**, misma clave en GitHub Secrets.
- **Control automático:** cada compilación compara el certificado del APK con la huella de `.github/signing-cert.sha256`. Si no coincide, la compilación falla y no se publica nada, así que un secreto mal cargado nunca llega a la radio. La huella es pública (viene dentro de cada APK); no es un secreto.
- La CI deja un aviso con el origen de la firma ("Firma: GitHub Secrets" o "Firma: app/keystore.properties") y otro con "Firma verificada".

## Opción A — misma clave en GitHub Secrets (recomendada, sin impacto)

Las instalaciones existentes se siguen actualizando encima. Pasos (los hace el autor, en la web de GitHub):

1. En una computadora, descargar `app/release.jks` del repo y pasarlo a texto base64 (no pegarlo en chats ni documentos):
   - Windows (PowerShell, en la carpeta del archivo): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\release.jks")) | Set-Clipboard`
   - Linux: `base64 -w0 release.jks` · Mac: `base64 -i release.jks`
2. GitHub → repo → Settings → Secrets and variables → Actions → New repository secret:
   - `KEYSTORE_BASE64`: el texto del paso 1.
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: los valores de `app/keystore.properties`.
3. Hacer una compilación (Actions → Build → Run workflow). En el resumen tienen que aparecer los avisos **"Firma: GitHub Secrets"** y **"Firma verificada"**.
4. Instalar esa versión **encima** de la anterior: si se instala sin desinstalar, la clave es la misma.
5. Recién ahí borrar `app/release.jks` y `app/keystore.properties` del repo y agregarlos a `.gitignore`.

Nota: siguen en el historial de git. Mientras el repo sea privado alcanza; si se hace público, hace falta la opción B.

## Opción B — clave nueva (solo si el repo va a ser público)

- Hay que **desinstalar la app una vez** en cada equipo: Android no deja instalar encima con otra firma.
- Se pierden los ajustes guardados en el equipo: paneles, dock, favoritas, lugares del mapa, conexión de Spotify, modo de luces.
- Requiere decisión del autor. No se aplica sin su OK.
