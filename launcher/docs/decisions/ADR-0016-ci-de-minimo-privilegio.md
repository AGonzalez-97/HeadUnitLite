# ADR-0016 - CI de mínimo privilegio y acciones fijadas por commit

**Estado:** Aceptada · **Fecha:** 2026-11-06 · **Versión:** v0.79.5 · **Autor:** Axel Hernán González

## Contexto

La auditoría técnica del 2026-11-05 (`docs/audits/AUDIT-2026-11-05.md`) encontró en las GitHub Actions:

- **AUD-073:** `carlink.yml` metía el `tag_name` de la última release de CarLink (un repositorio de terceros) directo en un script con `${{ }}`, en un trabajo con permiso de escribir en el repositorio. Un tag con comandos adentro se ejecutaba.
- **AUD-074:** `build.yml` daba `contents: write` a los cuatro trabajos, incluidos los que corren los plugins de Gradle de terceros, y las acciones con permiso de escritura estaban fijadas solo por tag.
- **AUD-088:** la CI no guardaba el mapa de R8 (`mapping.txt`), y sin él no se pueden leer los errores de un APK ofuscado.

## Decisión

1. Todo lo que viene de afuera (versiones, nombres de tag) entra a un script por una variable de entorno (`env:`), nunca con `${{ }}` dentro del script, y se valida con un patrón antes de usarlo (`^[0-9]+(\.[0-9]+){1,3}$` para la versión de CarLink).
2. `build.yml` corre con `permissions: contents: read`. Solo el trabajo `publicar` (que crea la release y el tag) pide `contents: write`.
3. Las acciones de terceros con permiso de escritura (`softprops/action-gh-release`) y las de Gradle (`gradle/actions/*`) se fijan por el hash del commit de su versión, con la versión en un comentario. Las oficiales de GitHub (`actions/*`) siguen por tag mayor.
4. Cada release lleva el mapa de R8 comprimido (`onix-launcher-mapping.txt.gz`, unos 9 MB; sin comprimir pesa unos 115 MB).

## Consecuencias

- Una versión nueva de una acción fijada por hash no se usa sola: hay que cambiar el hash a mano (se puede automatizar con Dependabot más adelante).
- `carlink.yml` conserva sus permisos de escritura porque su trabajo es fijar la versión de CarLink en el repositorio; por eso la validación del punto 1 es obligatoria.
- No cierra AUD-001 (la clave de firma en el repositorio): eso va aparte (`docs/SIGNING.md`, ADR-0010).
