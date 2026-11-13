# Onix Launcher — reglas del proyecto

Autor: **Axel Hernán González** · © 2026 Axel Hernán González. Licencia **AGPL-3.0** desde v0.56.0 (motor de CarLink, ADR-0012); uso personal, repositorio privado.

## Regla de oro: la documentación va en el mismo cambio que el código

Cada cambio de la app (código, permisos, dependencias, CI) actualiza **en el mismo commit**:

| Qué cambió | Qué se actualiza |
|---|---|
| Cualquier cambio visible o de comportamiento | `version.properties` (SemVer), `CHANGELOG.md`, `VERSION.md`, `docs/releases/vX.Y.Z.md`, insignia y tabla de `README.md` |
| Archivo Kotlin nuevo, borrado o con otra responsabilidad | Tabla de módulos de `ARCHITECTURE.md` y de `README.md`; diagrama y flujos de datos de `ARCHITECTURE.md` |
| Permiso nuevo | Tabla de permisos de `README.md` y `docs/HEAD_UNIT.md` |
| Dependencia o servicio externo nuevo | `README.md` (dependencias / servicios) y `THIRD_PARTY_LICENSES.md`; créditos en `About.kt` si corresponde |
| Tests nuevos o resultados nuevos | `docs/TEST_PLAN.md` (registro de ejecuciones) y tabla de tests del `README.md` |
| Pendientes resueltos o nuevos | `TODO.md` (y la sección 19 de `AUDIT_REPORT.md` si es un hallazgo) |
| Decisión de arquitectura | ADR nuevo en `docs/decisions/` |
| Versión publicada | Paneles del proyecto (Dashboard y Auditoría) y `docs/ARTIFACTS.md` |

La CI lo verifica con `.github/scripts/check_docs.py` (primer paso de los trabajos **tests** y **app**): si la versión, los módulos o los permisos no están documentados, la compilación falla.

## Regla de oro 2: todo dentro del launcher

Cada función que se agregue a la interfaz se usa **dentro del launcher**: no abre otra app.
Solo se permite salir a otra app o a un ajuste del sistema para **configurar algo la primera vez**
(dar un permiso, conectar una cuenta, elegir el launcher) o cuando Android no deja otra opción;
en ese caso se documenta en `README.md` → "Salidas a otras apps".

## Regla de oro 3: cada versión, completa y contada (pedido del autor, 2026-11-04)

- Cada release de GitHub lleva **la descripción de las mejoras**: la CI la toma de `docs/releases/vX.Y.Z.md` y le suma el resultado real de los tests. Esa nota se escribe en lenguaje simple y **para el público que lea la release**, en forma impersonal: sin hablarle al autor (nada de "tu radio", "tocá", "contame"); lo que el autor tiene que hacer va en `docs/INSTRUCTIVO.md` (pedido del autor, 2026-11-04). Si se corrige la nota de una versión ya publicada, se rehace su release con el workflow `notas.yml`.
- **Las releases son privadas (AUD-067):** cada APK lleva la clave de Android Auto de DiAuto y la identidad de CarPlay del adaptador Carlinkit (ADR-0012). "Para el público" se refiere al estilo de la nota (impersonal y simple), no a compartirla: **ni las releases ni los APK se comparten ni se publican**. Si algún día se quisiera compartir la app, primero hay que quitar esas dos identidades y entregar el código completo (AGPL-3.0).
- Con cada versión se actualiza **todo el repositorio** que corresponda: documentos de la tabla de arriba, `docs/tasks/ESTADO.md`, `docs/INSTRUCTIVO.md` si cambia lo que tiene que probar el autor, y el panel Dashboard.

## Regla de oro 4: publicación local directa garantizada y fallback (pedido del autor, 2026-11-08)

- **Prioridad 1 (Local):** Al terminar de programar, verificar los tests y commitear cada versión, cualquier asistente que opere en la máquina local ejecuta directamente `.\tools\publicar.ps1`, que corre los tests (con una falla no publica), compila, crea la release y sube el APK oficial a GitHub Releases usando el token local (`tools/github_token.txt`). `-SkipBuild` sube en 8 segundos un APK ya compilado, solo si es de la versión de `version.properties` (el script lo controla); `-SkipTests` saltea los tests si ya se corrieron para ese mismo código. Esto garantiza que la radio siempre tenga disponible la actualización de inmediato en **Ajustes → Sistema**, sin depender del cupo ni de las demoras de las máquinas de GitHub Actions.
- **Prioridad 2 (Fallback remoto / PC apagada):** Si la IA está corriendo en un entorno remoto (web, nube, app móvil) sin acceso a la PC local del autor, el fallback es pushear a `main` y dejar que el workflow `.github/workflows/build.yml` de GitHub Actions compile y publique la release. (Nota: `build.yml` fue optimizado para tardar solo ~4 minutos separando las capturas de pantalla a ejecución manual).

## Otras reglas

- **Resultados reales:** los tests y mediciones se informan solo desde `tests.txt`, `lint.txt` o el log de la CI. Nunca se inventan. Lo que no se pudo probar se dice.
- **Formato del informe al terminar:** Tests ejecutados / totales / exitosos / fallidos / cobertura / estado.
- **Seguridad:** nada de contraseñas, claves ni tokens en código o documentación. La clave de firma en el repo privado es un riesgo aceptado y documentado (AUD-001). Excepción (AUD-066): el **certificado y la clave privada** de receptor de Android Auto que trae el motor DiAuto en su código abierto (`vendor/diauto/app/src/main/res/raw/cert` y `privkey`, emitidos por "Google Automotive Link" para el estéreo de referencia de Google; ADR-0012). No son públicos ni autorizados para este uso: los usan los proyectos abiertos de Android Auto desde 2014. Riesgo aceptado por el autor solo para uso personal. La identidad de CarPlay **nunca** va al repo: GitHub Secrets o la CI.
- **Código de terceros en `carlink-core/` y `vendor/`** (CarLink, AGPL-3.0): no se modifica salvo que haga falta, y cada cambio se anota en `vendor/ONIX_CHANGES.md`.
- **Terceros:** no atribuir al autor código, imágenes o logos ajenos; registrarlos en `THIRD_PARTY_LICENSES.md`.
- **Compatibilidad:** mínimo Android 7.1 (minSdk 25, desde v0.55.0 por CarLink, ADR-0012); el equipo objetivo tiene Android 7.1.1. Lint revisa `NewApi` en cada compilación.
- **Cambios chicos y precisos:** preferir cambios acotados a reescrituras.
- **Commits solo de documentación** no compilan (`paths-ignore`).
- Toda la interfaz se dibuja en un lienzo de 1024 × 600 dp; las capturas de la CI son la referencia visual.

## Flujo de trabajo (adaptado de gentle-ai, MIT)

Para no perder el hilo entre sesiones (claude.ai, Claude Code web o en la PC):

1. **Al empezar una sesión:** leer `docs/tasks/ESTADO.md` (dónde quedó todo y qué sigue) y la tarea abierta de `docs/tasks/`.
2. **Pedido chico y claro:** se hace directo, sin archivo de tarea.
3. **Trabajo grande** (una versión nueva, una función con varios archivos): antes de tocar código se crea `docs/tasks/<nombre>.md` con **alcance**, **pasos** (casillas), **evidencia** (tests, CI, prueba en el auto) y **siguiente paso**. Se actualiza al avanzar.
4. **Tests primero** cuando hay un test posible y un resultado esperado claro: escribir el test (falla), implementar (pasa), ordenar. Un test por regla pedida.
5. **Commits chicos:** unas 400 líneas por commit como máximo; un cambio riesgoso (firma, permisos, borrado de datos) va solo en su commit.
6. **Al terminar o al cortar:** actualizar `docs/tasks/ESTADO.md` (versión, qué está en la CI, qué falta probar en el auto, siguiente paso) en el mismo commit. Es la "memoria" del proyecto.
7. **Revisión con evidencia:** nada se da por terminado sin el resultado de la CI (`tests.txt`) o de la prueba en el auto; lo que no se pudo probar se dice.
8. **Todo a `main` (pedido del autor, 2026-11-04):** cada cambio terminado se sube también a `main` sin preguntar (avance directo desde la rama de trabajo, sin reescribir historia).
9. **Panel Dashboard al día (pedido del autor, 2026-11-04):** con cada versión publicada se actualizan los datos del panel "Onix Launcher — Dashboard" en Claude (estado, historial, funcionalidades, roadmap, archivos y hallazgos; cómo, en `docs/ARTIFACTS.md`) y su fila en `docs/ARTIFACTS.md`. Así claude.ai, Claude Code web y la PC ven lo mismo.
10. **Informes del auto:** la Prueba completa se pide y se lee según `docs/PRUEBA_COMPLETA.md`; si se agrega una función que se pueda revisar sola, se suma al informe y a esa guía.
