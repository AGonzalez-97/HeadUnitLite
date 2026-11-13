# Pendientes

Fuente: pedidos del autor y hallazgos de `AUDIT_REPORT.md` (2026-10-26). Los IDs `AUD-xxx` remiten al reporte.
**Actualizado:** 2026-11-12 · v0.81.21. Hallazgos nuevos de la auditoría técnica: `docs/audits/AUDIT-2026-11-05.md` (AUD-069 a AUD-118); fase 1 hecha en v0.79.5 (`docs/tasks/fase-1-auditoria.md`) y fase 2 en parte en v0.79.6 (`docs/tasks/fase-2-auditoria.md`).

## Alta prioridad

- [ ] Probar en el head unit y enviar la foto de Diagnóstico: resolución, fluidez, memoria (AUD-027).
- [x] OBD2 con el ELM327 por Bluetooth: temperatura, RPM, voltaje, velocidad; combustible si el auto lo informa (AUD-010, v0.31.0).
- [ ] Probar el OBD2 en el auto: emparejar el lector, mirar Diagnóstico y mandar el informe de **Prueba completa** (HU-010).
- [ ] Probar en el auto los códigos de falla, el consumo, las alertas y el aviso de luces apagadas (R-39 a R-42, v0.50.0).
- [ ] Probar la radio FM por la antena (R-43, v0.51.0): sintonizar, buscar, RDS, volver a Spotify; confirmar que sintonizar no pisa las presintonías de la radio de fábrica.
- [ ] Probar Mi auto (R-44, v0.52.0): cargar el km del tablero, agregar el cambio de aceite, manejar y ver el viaje.
- [x] ~~Botones del volante (R-45)~~: no aplica, el Onix Joy 2018 (modelo base) no tiene botones en el volante.
- [ ] Aire acondicionado prendido o apagado: ver si se puede saber por el OBD2 (`docs/tasks/aire-acondicionado.md`).
- [ ] Medir el tiempo de inicio antes y después de v0.53.0 (perfiles de arranque, R-20).
- [x] Android Auto y CarPlay dentro de Onix (ADR-0012, `docs/tasks/auto-carplay.md`): herramientas de compilación (v0.55.0), Android Auto (v0.56.0), CarPlay (v0.57.0 a v0.79.3); CarPlay por cable probado en el auto (R-85 OK, 2026-11-05).
- [ ] Probar en el auto Android Auto por cable y la memoria después de una sesión (ver AUD-041 abajo).
- [ ] Medir en la radio la memoria con el proceso `:aa` del motor de Android Auto (AUD-041, `dumpsys meminfo`).
- [x] Revisar el resultado del primer **Lint completo** (`lint.yml`): resuelto con AUD-059 (v0.64.1), solo el código de Onix, termina en ~4 min sin errores.
- [ ] Ajustar el consumo estimado si difiere del real (eficiencia volumétrica 0,85 en `Consumption`): comparar un tanque lleno con los km recorridos.
- [x] Señal local de La 100 (95.5): investigada con el informe del celular. No existe por internet (reproductor de Tucumán dado de baja, sin señal en StreamTheWorld ni en directorios). Queda la señal de Buenos Aires. Reabrir si Cienradios publica una.
- [ ] Revisar en el equipo cuántas radios quedan visibles después de la primera revisión, y si alguna categoría está mal asignada.
- [ ] Clave de firma (AUD-001): la compilación ya acepta GitHub Secrets (v0.38.1). Decidido: opción A. Falta que el autor cargue `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (pasos en `docs/SIGNING.md`); con "Firma verificada" en la CI, sacar los dos archivos del repo. Rotar la clave solo si el repo se hace público (obliga a desinstalar).
- [ ] Lint: pedir también ubicación aproximada (`CoarseFineLocation`, en Android 12 o más nuevo el pedido solo de precisa se ignora) y corregir el `remember` sin valor de `HomeScreen.kt` (`RememberReturnType`).
- [x] Tag `v0.38.0` sin crear: no hace falta, cada versión se publica como release `build-N` (AUD-064).
- [x] Voz de navegación y avisos sin "Servicios de voz de Google": voces grabadas (HU-011, v0.61.0). Con TextToSpeech instalado además se dice el nombre de la calle.
- [x] Luces de la app con el sensor de iluminación de la radio apagado: funcionan (HU-009, R-38, v0.49.0, confirmado por el autor el 2026-11-03).
- [ ] Mapa etapa 2 (ADR-0011): repo público de paquetes, paquete de Tucumán armado en GitHub Actions, mapa sin internet; después límites de velocidad. La búsqueda y las rutas pasaron a TomTom en línea (ADR-0014): hacerlas sin internet queda solo como idea, a decidir por el autor.
- [x] Spotify y Onix en las listas de procesos de la radio (HU-014, 05/11): siguen abiertos al apagar y prender el contacto.
- [ ] Conectar la cuenta de Spotify en Onix (R-88). El secreto `SPOTIFY_CLIENT_ID` ya está cargado en GitHub (2026-11-06) y entra desde v0.79.6 (ADR-0015): no hay que pegar nada en la radio. Falta tener la cuenta en la lista de usuarios de la app de developer.spotify.com con el Redirect URI `onixlauncher://spotify`.
- [ ] Medir fluidez en la radio antes de tocar animaciones (AUD-035). Auditoría 2026-11-05 (AUD-079): el medidor de Diagnóstico → Fluidez mide la pantalla de Diagnóstico, no Inicio; la medida válida es `adb shell dumpsys gfxinfo com.axel.onixlauncher` con Inicio a la vista (comandos en la sección K del informe).
- [ ] Auditoría 2026-11-05, decisiones del autor que siguen abiertas: **aguja de los relojes** (AUD-070: medir primero con `dumpsys gfxinfo`, sección K de la auditoría). Resueltas en v0.79.7: la coordenada por defecto (AUD-105: su casa, Tafí Viejo, `-26.7368, -65.258`), idiomas es+en (AUD-086, −3,30 MB) y voces de Piper (AUD-087: no se hace).
- [ ] Probar en el auto lo de v0.79.7: GPS del mapa (R-89: "Buscando GPS…", punto azul cada 1 s y línea de GPS en la Prueba completa) y aviso de luces apagadas a cualquier velocidad (R-90). Si el GPS de la radio entrega lento, evaluar el GPS del celular por Bluetooth (solo con el Galaxy; dificultad media).
- [ ] Probar en el auto lo de v0.79.8: clima y nombre del lugar al pasar los 3 km (R-91).
- [ ] Probar en el auto lo de v0.79.9: Spotify al encender con la app recién arrancada y OBD2 sin colgarse (R-92). Informe del 2026-11-06: GPS OK (1,0 s), clima por distancia OK; faltan CarPlay (R-87, sin iPhone) y luces andando (R-90).
- [ ] Probar en el auto lo de v0.79.10 (R-93): evento "Contacto: la radio avisó estado=N" y paso del lector OBD2 trabado; probar también reenchufar el lector. Informe 10:37: el reinicio del Bluetooth no destrabó el lector; Spotify mudo con la fuente de la radio ya en 0 (causa sin identificar).
- [ ] Probar en el auto lo de v0.79.11 (R-94): Spotify al encender con pausa y play (corte breve a los pocos segundos).
- [ ] Probar en el auto lo de v0.79.12 (R-95): lector OBD2 con las formas de conectar verificadas.
- [ ] Probar en el auto lo de v0.79.13 (R-96): Spotify al encender con paso de 1,5 s al primer plano.
- [ ] Auditoría 2026-11-05, fase 2 (`docs/tasks/fase-2-auditoria.md`): paso 1 hecho (tests del lector OBD2, Spotify y `Net`, +30; AUD-118 arreglado; rama `audit/fase-2`, sin subir). **En espera del autor:** estado de la navegación fuera del pager (AUD-069: probar en el auto que la guía se corta al ir a Inicio). Publicados en v0.79.6: AUD-100, AUD-093 (la conexión real con Spotify se prueba en R-88), AUD-118 y los tests del lector, Spotify y `Net`. Publicados en v0.79.7: tests del bucle de los destinos del celular (AUD-085) y MAC enmascarada en la Prueba completa (AUD-096); AUD-092 revisado, sin cambio; GPS del mapa (AUD-119, AUD-120), idiomas del APK (AUD-086) y aviso de luces a cualquier velocidad (AUD-121). Pendientes: AUD-091 (control externo de la radio, después de probar en el auto), tests de la reconexión de la radio y de `NwdTuner`, y Conscrypt fuera del hilo principal (AUD-078, medir antes).

## Media prioridad

- [x] **Revisión de reintentos (12/11/2026): prioridad media, hecha en v0.81.22** (destino del celular, catálogo y revisión de radios, logos, mapa 3D, ruta inicial y `autoStart`). La cuenta de fallas de `WifiRescue` se deja como está (ver `CHANGELOG.md`).
- [ ] **Revisión de reintentos: pendientes de prioridad baja.** Mensaje de la lista de temas cuando falla por red (hoy dice que Spotify no deja verla); `AutoResume.done` se marca antes de terminar; clima viejo se reintenta recién a los 15 min; `NwdTuner` no reintenta el enlace con el servicio de FM; registros de avisos marcados antes de lograrse (`NetWatch`, `Lights`, `Media`, `CarCare`); el hilo de destinos del celular no sobrevive a un error grave; `DirectHttps` no cierra el socket si falla la conexión; los mosaicos del mapa, ntfy y el reproductor de radio no cuentan para `WifiRescue` (logos, portadas y rutas sí desde v0.81.22).

- [x] Radios de cadena sin señal de Tucumán online: se muestran con la señal nacional (aprobado por el autor, v0.24.0).
- [ ] Revisar Inicio (v0.26.0, igual a la imagen de referencia) en el celular y en el head unit: legibilidad, cajón de apps, aleatorio y me gusta con Spotify.
- [ ] Mi Radio (92.7) y FM Los Ralos (101.7): responden HTTP 401 (informe de v0.24.0); buscar otros enlaces.
- [ ] Antena Ocho (98.1) y Conciencia (104.9): Online Radio Box dice que dejaron de emitir por internet.


- [ ] Botón Cámara: volver a agregarlo cuando se conozca el paquete de la app de cámara del head unit (AUD-011).
- [x] Luces reales del auto en lugar de deducirlas por la hora: señales de la radio NWD (`ACTION_ILL_STATE_CHANGE`, `mcu_no_source_device_state`, `high_beam_state`) (AUD-012, v0.49.0).
- [x] Medir cobertura de tests (JaCoCo): desde v0.63.0, en cada release (16,0 % en el build 107).

## Baja prioridad

- [ ] Tests de interfaz con aserciones (además de las capturas).
- [x] Evaluar si `RadioService` necesita seguir exportado (AUD-004): lo exige Media3; protegido desde AUD-029.

## Ideas futuras

- [ ] Pantalla dividida con Maps o Waze (si el head unit la soporta).
- [ ] Enlaces confirmados para más radios de Tucumán.

## Completado

### v0.19.0 a v0.21.0 (2026-10-26)
- [x] Logos de las radios (World Radio Map, directorio, página web) y favoritas.
- [x] La radio no se pausa sola (reconexión, wake lock) y se ve en el inicio y el tablero.
- [x] Notificación y segundo plano de la radio (sesión registrada).
- [x] Solo radios que funcionan y categorías.
- [x] Regla de oro de documentación verificada por la CI.

### v0.18.0 (2026-10-26)
- [x] Velocímetro de arco moderno en el inicio.
- [x] Tag de versión automático en la CI (desde `v0.18.0`).

### v0.17.0 (2026-10-26)
- [x] Pantalla "Acerca de" con versión, autoría y atribución de Open-Meteo (AUD-022, AUD-024).
- [x] Contador de generación en `RadioService` (AUD-007).
- [x] `Locale.US` en frecuencias, voltaje y RPM (AUD-013).
- [x] Configuración de red: HTTPS para APIs, HTTP solo para el audio (AUD-002).
- [x] Solo dominios de confianza para los reproductores (AUD-003).
- [x] Tests de lógica bloqueantes (AUD-016, AUD-017).
- [x] SemVer explícito en `version.properties` (AUD-020).
- [x] Música por eventos (AUD-005).
- [x] Conexiones HTTP con `try/finally` y código de respuesta (AUD-008).
- [x] Logs con `Log.w` y último error en Diagnóstico (AUD-009).
- [x] Permiso de notificaciones una sola vez (AUD-025).
- [x] Botón Cámara sin función retirado (AUD-011, parcial).
- [x] Gradle wrapper (AUD-018) y acciones de GitHub actualizadas (AUD-019).
- [x] Animación de la ruta sin reinicios (AUD-006).
- [x] `LauncherApp.kt` dividido (AUD-015).
- [x] Código muerto eliminado (AUD-014).
- [x] Constante para la ubicación por defecto (AUD-026).
- [x] README completo.

### Antes
- [x] Sistema de documentación, autoría y primera auditoría (2026-10-26).
- [ ] Si alguna vez se comparte Onix: quitar la clave de Android Auto de DiAuto y la identidad de CarPlay, y entregar el código completo (AGPL-3.0) (AUD-066, AUD-067).
- [x] CarPlay sin identidad en los APK desde el build 86 (AUD-068): causa, APK de CarLink 1.0.7 retirado; v0.70.1 usa CarLink 1.0.9. Falta probar CarPlay en el auto (R-62).
- [x] CarPlay no conecta con el iPhone 17 Pro Max (2026-11-05): causa encontrada con el logcat (pedido `Pair` de 4608 bytes sin paquete USB vacío); arreglo en v0.79.3. **R-85 OK en el auto (05/11 20:29).**
- [x] Reportar el paquete USB vacío a DiPlay y CarLink: **no** (decisión del autor, 2026-11-05: el proyecto es privado).
- [ ] A veces se va el sonido al apagar y prender el auto con Spotify sonando (2026-11-05): v0.79.4 devuelve la fuente a Android al poner contacto; falta R-86. Si vuelve a pasar: Prueba completa **sin sonido**, antes de cambiar de fuente.
- [ ] OBD2 se corta cada ~23 s (Prueba completa 05/11 13:12): v0.79.1 anota los eventos del Bluetooth; falta el informe para ver la causa.
- [x] OBD2 trabado en "conectando…" sin falla ni reinicio del Bluetooth (05/11 17:12): v0.79.2, tope y vigía. Falta R-84 en el auto.
- [ ] Confirmar en la radio las rutas por TLS 1.3 directo (HU-013, R-63) y Dock → Teléfono (R-64), v0.71.0.
- [x] Voces con acento argentino: creadas por el autor en la web de ElevenLabs; Locutor AR por defecto (v0.72.1).
- [x] Motores de `vendor/` a CarLink 1.0.9 (v0.74.0); falta la prueba en el auto (R-70).
