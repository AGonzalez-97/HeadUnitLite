# Instrucciones para Asistentes y Modelos de IA (AGENTS.md)

Este repositorio sigue estrictamente las reglas definidas en [CLAUDE.md](./CLAUDE.md).

Cualquier asistente de IA (Claude, Gemini, GPT, Copilot, Cursor, etc.) debe leer y aplicar las directivas de `CLAUDE.md`, haciendo especial énfasis en:
- **Licencia AGPL-3.0 y privacidad:** Repositorio y releases estrictamente privadas (AUD-067). Ni los APKs ni las releases se comparten públicamente.
- **Regla de oro 4 (Publicación de releases):** 
  - **Entorno local:** Ejecutar `.\tools\publicar.ps1` (corre los tests, compila, crea la release y sube el APK con `tools/github_token.txt`); `-SkipBuild` solo para un APK ya compilado de esa misma versión.
  - **Entorno remoto / sin acceso a la PC:** Pushear a `main` y dejar que compile y publique `.github/workflows/build.yml` en GitHub Actions.
- **No inventar resultados:** Los tests se reportan desde salidas reales de Gradle o CI.
- **Sincronización:** Cada versión actualiza la documentación (`CLAUDE.md`, `docs/tasks/ESTADO.md`, `docs/releases/vX.Y.Z.md`).
