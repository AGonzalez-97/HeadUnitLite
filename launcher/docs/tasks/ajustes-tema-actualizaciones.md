# Ajustes, tema y actualizaciones (ideas tomadas de StradaOS)

Objetivo: un menú de Ajustes propio por categorías, tema oscuro/claro/automático con color de acento, aviso de actualización con descarga dentro de la radio y registro de fallos. Rama: `main` (regla 8 de `CLAUDE.md`). Entrega: una versión por tarea. Tests: `./gradlew :app:testDebugUnitTest --tests "com.axel.onixlauncher.logic.*"` y `--tests "*ScreenshotTest*"`.

## Specs

- **S1 — Menú de Ajustes.** "tener ese menu de configuraciones". Una pantalla propia del launcher, por categorías, que junta los ajustes que hoy están sueltos (luces, brillo, modo liviano, sonido de inicio, avisos) y da acceso a Diagnóstico y Acerca de. El acceso "Ajustes" del dock deja de abrir los Ajustes de Android (regla de oro 2). Criterio: se abre desde el dock y desde Apps, se cierra con Atrás o tocando afuera, y cada ajuste guarda igual que hoy.
- **S2 — Tema.** "Me gustaria poner ese nivel de personalizacion tipo modo oscuro claro o automático". Automático = claro de día, oscuro con faros prendidos o de noche (propuesta aceptada: usar `Lights.kt` y `Ambient.kt`, no solo la hora). Incluye color de acento.
- **S3 — Actualizaciones.** "no puede hacer que se descargue automatico y se instale dentro del radiounit?" Acordado: descarga automática dentro de la radio, verificación de que el APK esté firmado con la misma llave, y un toque en "Instalar" (Android no permite instalar en silencio a una app común).
- **S4 — Registro de fallos.** Propuesta aceptada ("mandale nomas"): si la app se cierra sola, el error queda guardado y se ve desde Ajustes.

## Tasks

| ID | Specs | Ruta | Estado | Evidencia |
|---|---|---|---|---|
| T1 | S1 | inline | completada (v0.80.0) | `4745aad` · `SettingsAndThemeTest` RED (task-874) → GREEN (task-893) |
| T2 | S2 | inline | completada (v0.80.0) | `4745aad` · `SettingsAndThemeTest` RED (task-874) → GREEN (task-893) |
| T3 | S3 | inline | completada (v0.81.0) | `088641c` · `SettingsAndThemeTest` RED (task-953) → GREEN (task-1030), verifier PASS |
| T4 | S4 | inline | completada (v0.80.0) | `4745aad` · `SettingsAndThemeTest` RED (task-874) → GREEN (task-893) |

## Log

- **L1 (2026-11-08, autor):** "Che mientras esperamos para hacer los test de nuevo mira esto que te parece? Me gustaria poner ese nivel de personalizacion tipo modo oscuro claro o automático o tener ese menu de configuraciones vos que opinas no se que mas podemos rescatar de este proyecto y ponerlo en el nuestro que te parece?" (con `StradaOS-main.zip`)
- **L2 (2026-11-08, autor):** "Che los de la actualización esta bueno, no puede hacer que se descargue automatico y se instale dentro del radiounit?"
- **L3 (2026-11-08, autor):** "Sii mandale nomas" / "Segui donde quedaste".
- **L4 (análisis):** StradaOS es GPL-3.0 (compatible con AGPL-3.0) pero es Java con Views para API 16; se toman ideas, no código. Su tema automático es por hora (6 a 18). En Onix convertimos los colores de `Ui.kt` en propiedades `get()` respaldadas por estado reactivo de Compose (`ThemePrefs`), de modo que los ~300 lugares donde se usan se actualizan en vivo sin tocar cada archivo.
- **L5 (bloqueo T3):** las releases del repo `AGonzalez-97/onix-launcher` son privadas (`CLAUDE.md`, AUD-067; ADR-0005) y no se permiten tokens en el código, así que una consulta anónima a GitHub Releases da 404. Además, volver a habilitar `REQUEST_INSTALL_PACKAGES` (quitado en v0.59.0, AUD-040) es un cambio de permisos (riesgo alto) que debe ir en su propio commit una vez elegido cómo descargar el APK.
- **L6 (T1+T2+T4, v0.80.0):** implementados `SettingsScreen.kt` (`SettingsCategory`, `SettingsNav`, `CrashLog`, `SettingsOverlay`), `ThemeMode` / `AccentPalette` / `ThemePrefs` en `Ui.kt`, conexión en `Dock.kt`, `AppsScreen.kt`, `Diagnostics.kt`, `OnixApp.kt` y `LauncherApp.kt`, y test unitario `SettingsAndThemeTest.kt`.
- **L7 (2026-11-08, autor):** "La primera opcion y mira , te paso los logs de la version donde arrgelamoa spotify y obd te comento obd sigue sin conectar y spotify se inica sola y despues vuelve al launcher  y otro error o bug que encontre en donde esta el mapa si pongo para reproducir una lista suena ok pero si pongo en busqueda o recientes y elijo una musica no hace nada y otra cosa cuando minizimo la música para que se vea mas grande el mapa no podes poner aparte de un pause y play , siguiente o retroceder?" / "segui nomas".
- **L8 (T3 + correcciones de auto, v0.81.0):** implementado `Updates` en `SettingsScreen.kt` con token de lectura guardado en `SharedPreferences("actualizaciones")`, verificación de firma del APK y `FileProvider` (`REQUEST_INSTALL_PACKAGES`); en `Spotify.kt`, `contextUri` del álbum + `offset` para canciones de Recientes/Buscar; en `NavMusicScreen.kt`, botones `⏮` y `⏭` en el reproductor minimizado; en `Media.kt`, retomar Spotify cuando vuelve la red y enviar siempre `KEYCODE_MEDIA_PLAY`; en `Obd.kt`, espera de `15 s` (`WAKE_SETTLE_MS`) tras contacto antes de abrir RFCOMM, `DEFAULT_PREFERRED_WAY = 2` y cierre de `mPfd`.
