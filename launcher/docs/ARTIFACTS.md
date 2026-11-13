# Paneles en Claude (Artifacts)

Los paneles son una **vista** del proyecto. La fuente de verdad sigue siendo el código y estos documentos. Si un panel contradice a un archivo, vale el archivo y hay que actualizar el panel.

| Panel | Qué muestra | Fuente | Versión del panel | Versión del proyecto | Actualizado |
|---|---|---|---|---|---|
| Onix Launcher — Dashboard | Estado, funcionalidades, problemas conocidos, roadmap, archivos del código, historial de versiones, autoría | `VERSION.md`, `CHANGELOG.md`, `README.md`, `AUDIT_REPORT.md`, `TODO.md`, `docs/tasks/ESTADO.md` | 57 | v0.79.15 (estado, historial con v0.79.10 a v0.79.15 y roadmap; funcionalidades, archivos y hallazgos AUD-001 a AUD-121 sin cambios) | 2026-11-07 |
| Auditorías 2026-11-03, 2026-11-04 y 2026-11-05 | En "Problemas conocidos" del Dashboard; detalle en `docs/audits/AUDIT-2026-11-03.md`, `AUDIT-2026-11-04.md`, `AUDIT-2026-11-05.md` y secciones 21–26 de `AUDIT_REPORT.md`. La del 2026-11-05 no tiene panel propio (se siguió lo de las dos anteriores): si el autor lo pide, se arma "Onix Launcher — Audit 2026-11-05" | `AUDIT_REPORT.md` | — | v0.79.9 | 2026-11-06 |
| Revisión técnica 2026-10-28 | Sin panel propio todavía: los hallazgos están en `AUDIT_REPORT.md` sección 20 | `AUDIT_REPORT.md` | — | v0.38.1 | 2026-10-28 |
| Onix Launcher — Audit 2026-10-26 | Hallazgos por severidad y área, estado de cada uno (seguimiento v0.17.0), tabla completa con filtro, alcance | `AUDIT_REPORT.md` | 2 | v0.17.0 | 2026-10-26 |

Los paneles son privados del autor hasta que se compartan desde su menú. No son un backup, ni un control de versiones, ni un registro legal.

## Cómo actualizar el Dashboard

Panel: https://claude.ai/artifact/KDNSTasTyNk6JsqGwJajUQ (privado del autor). Es del tipo Dashboard: guarda sus datos en seis documentos `datasets/<id>` (`status`, `versions`, `features`, `roadmap`, `modules`, `findings`), cada uno con un archivo JSON fijo como fuente (`source.url`, del tipo `/_blob/<id>`). Los viejos quedan en el panel como historial. Para cambiarlos (lo que se hizo en el panel 57, 2026-11-07):

1. **Bajar el archivo actual:** herramienta `Artifact`, acción `read`, `path` = el id del archivo (los de `source.url`; uno por llamada). Sirve para ver el formato.
2. **Armar el JSON nuevo desde los documentos del repo**, con un script (no a mano ni de memoria): `status` desde `VERSION.md` y la CI (`tests.txt`); `versions` desde `CHANGELOG.md`; `findings` desde `AUDIT_REPORT.md` y los informes de `docs/audits/`; `modules` desde la tabla de módulos del README y las líneas reales de cada archivo; `features` y `roadmap` desde `README.md`, `docs/tasks/ESTADO.md` y `TODO.md`. Los resultados de los tests y la cobertura salen del `tests.txt` de la CI, nunca de la PC.
3. **Subirlo:** `Artifact` con `url`, `asset: true` y `file_path` (un JSON por llamada); la respuesta da la `url` nueva.
4. **Apuntar el dato al archivo nuevo:** `ArtifactData`, acción `batch`, un `update` por documento de `datasets/` con `source.url`, `updated: {at, by: "Claude"}`, `edit: {via: "claude", at}` y `description`, y cada uno con su `if_version`.

`status.artifact_version` sube en 1 y coincide con la columna "Versión del panel" de arriba. Después de cambiar los datos hay que abrir el panel y comprobar que muestre la versión nueva.

## Cuándo actualizarlos

- **Nueva versión:** Dashboard (estado, historial, roadmap) y una nota en `docs/releases/`.
- **Nueva auditoría:** sus hallazgos entran a `findings` del Dashboard ("Problemas conocidos"), como los de las auditorías del 2026-11-03, 11-04 y 11-05, y se anotan en la tabla de arriba. Un panel propio "Onix Launcher — Audit AAAA-MM-DD" solo si el autor lo pide; el anterior queda como historial.
- **Antes de dar por terminada una versión:** el panel y esta tabla al día es parte de "terminado" (`CLAUDE.md`, regla 9 del flujo de trabajo).
- **Cambio de arquitectura:** sección Arquitectura del Dashboard.
