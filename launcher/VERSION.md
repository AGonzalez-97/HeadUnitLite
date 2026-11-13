# Versión actual

**Versión:** v0.81.25
**Build:** se completa al publicar la compilación
**Fecha:** 2026-11-11
**Estado:** Beta

## Resumen

Launcher para el head unit del Onix: Inicio con clima, fondos y panel de motor (OBD2); navegación propia con voz; Spotify y radio de Tucumán dentro del launcher; dock personalizable. Probado en la radio del autor (pantalla, OBD2); luces sincronizadas con el auto (probado); falta medir fluidez y probar los códigos de falla en el auto.

## Cambios principales

- La Prueba completa lista los equipos Bluetooth emparejados con la radio (v0.81.25).
- El actualizador ignora un token pegado que no tenga forma de token y no deja valores de encabezados en el registro (v0.81.24).
- El informe de la Prueba completa se envía solo a la PC (`tools\bajar_informe.ps1` lo baja) y el buscador del mapa sugiere mientras se escribe (v0.81.23).
- Revisión de reintentos, segunda parte: destino del celular en espera sin internet, catálogo y logos de radios, mapa 3D y rutas que se piden de nuevo solas (v0.81.22).
- Revisión de reintentos: la app nota sola que internet volvió, prueba la red cada 30 s durante una caída y retoma la radio, las listas y la letra (v0.81.21).
- Las listas de Spotify muestran sus temas: se usa la dirección nueva de Spotify (`/playlists/{id}/items`) (v0.81.20).
- Si el Wi-Fi queda conectado pero sin internet, la app lo reinicia sola; las listas de Spotify avisan cuando no hay conexión (v0.81.19).
- Ajuste "Retomar la música al dar contacto" (apagado, la app no da play sola) y letra al pie más grande; "Desconectar Spotify" pasó a Ajustes (v0.81.18).
- Las listas de Spotify se abren para elegir el tema y el recuadro de la letra al pie es más grande (v0.81.17).
- Spotify ya no se abre y se cierra al dar contacto: la radio lo cerraba al despertar; agregado a su lista de navegación sigue sonando, y la app no lo pasa al frente si figura ahí (v0.81.16).
- Sonido de inicio nuevo (acorde que entra de a poco y dos notas claras) y slogan apagado por defecto (v0.81.15).
- Lector OBD2: causa de las fallas encontrada en el auto (el lector atiende a un solo equipo y cada reintento ocupaba un lugar de conexión que el Bluetooth de la radio no libera); un solo socket por intento y reintentos de 15 s a 10 minutos (v0.81.14).
- Conexión con el lector OBD2 simplificada tras la auditoría contra una app estándar: el motor de CarLink ignora al lector, el auto que no contesta no corta el Bluetooth, sin reinicio automático del Bluetooth de la radio, los avisos no cortan una conexión sana y los reintentos se espacian; Diagnóstico muestra el launcher que protege la radio (`persist.nwd.launcher.default`) (v0.81.13).
- El lector OBD2 y los viajes de Mi auto viven con el proceso de la app y no con la pantalla (`CarEngine` en `Obd.kt`, AUD-082, ADR-0018); el actualizador ofrece también una versión vuelta a publicar, comparando el número de compilación (`Updates.hasUpdate`) (v0.81.12).
- Ajustes ordenado en cinco categorías y Diagnóstico adentro (`SettingsScreen.kt`, `Diagnostics.kt`): Apariencia, Sonido y avisos, Auto y conexiones (lector OBD2 con **Reintentar**), Sistema y Diagnóstico (lecturas agrupadas y Prueba completa arriba, sin controles mezclados). **Pantalla de inicio** consulta a Android quién atiende el botón Inicio (`HomeDefault`) y pide la ventana para elegir aunque la radio no tenga ajustes de inicio (v0.81.11).
- OBD2: el corte del lector ya no pone en cero la cuenta de fallas (`ObdRecovery.restartsRetries` en `Obd.kt`); vuelve a correr el reinicio automático del Bluetooth tras 3 fallas seguidas y el reintento respeta su espera. Compilación en la PC con las mismas claves que la CI desde `secrets.properties` (fuera del repositorio) (v0.81.10).
- Remoción de efectos artificiales de noche y restauración de escarapela limpia: eliminación total de superposiciones de haces y destellos en `HomeScreen.kt` para conservar la iluminación fotográfica natural nocturna; restauración de la escarapela sutil en fechas patrias (`Festive.kt`) dejando la carrocería limpia (v0.81.9).
- Iluminación de faros suavizada y funda patria artesanal en capot y techo: atenuación del 55% en la luz de los faros sobre el asfalto para un acabado sutil; funda festiva completa en capot y techo con franjas longitudinales celeste-blanco-celeste, Sol de Mayo únicamente en capot, cubre-espejos retrovisores y carrocería limpia (v0.81.8).
- Iluminación orgánica de faros, ploteo patrio completo y adornos navideños vivos con corona en parrilla: eliminación de conos poligonales duros reemplazándolos por dispersión difusa en el asfalto; ploteo integral de la carrocería en la bandera argentina con Sol de Mayo en capó; corona navideña con moño rojo en la parrilla y guirnaldas C9 dinámicas con onda corrediza y destellos (v0.81.7).
- Faros frontales realistas, ploteo patrio, festivos extendidos y ajustes glass: proyección de faros hacia el frente siguiendo la perspectiva del auto; ploteo deportivo de franja celeste y blanca en capó y techo con escarapela ampliada; calendario festivo con rangos amplios (Navidad desde el 8/12, etc.); menú de Ajustes con estilo vidriado (Glass) y opacidad configurable (v0.81.6).
- Corrección óptica izquierda y letra en vivo compacta en Spotify: posición milimétrica del faro izquierdo en `HeadlightFlares` (`0.052w, 0.453h`) y `HeadlightBeams` (`400 dp`); barra flotante de letra en vivo (`MiniLyricsBar`) en `NavMusicScreen` para leer la letra actual mientras se busca o navega sin cambiar de pestaña (v0.81.5).
- Temas festivos adaptativos: los efectos atmosféricos y adornos del auto (fuegos artificiales, luces navideñas, cielo patrio, niebla de Halloween, nieve) se modulan según la hora (día, atardecer, noche) y la lluvia, manteniendo coherencia física y visual realista en cualquier momento (v0.81.4).
- Temas festivos realistas: el auto deja los adornos de caricatura y solo conserva nieve/luces en Navidad y escarapela chica en fechas patrias; cada fiesta se nota en el ambiente (fuegos con estela, niebla de Halloween, cielo patrio, copos con profundidad). Botón Radio/Spotify más grande en la tarjeta de música (v0.81.3).
- Faros del auto con destello óptico frontal, halo difuso y haces proyectados sobre el asfalto en perspectiva activos de día y noche (`HomeScreen.kt`), más indicadores sutiles de conexión (Wi-Fi y estado de lectura OBD2) en la barra inferior `VehicleStrip` (v0.81.2).
- GPS sincronizado sin retraso visual (`minTime = 0L`, animación de seguimiento de cámara a 350 ms, rotación a baja velocidad a 0.8 m/s) y botones de destino inteligentes que ocultan Casa/Trabajo al llegar dentro de 100 m o cuando ya están configurados, mostrando solo Favorito (v0.81.1).
- Actualizador integrado en **Ajustes → Sistema** (`Updates` con verificación de firma del APK y `FileProvider`), reproducción de canciones desde **Recientes** y **Buscar** de Spotify (`context_uri` del álbum + `offset`), botones **Anterior (`⏮`)** y **Siguiente (`⏭`)** en el mini-reproductor con mapa expandido, reanudación de Spotify cuando vuelve el Wi-Fi tras dar contacto y espera de 15 s (`WAKE_SETTLE_MS`) tras encendido del MCU antes de abrir sockets OBD2 para no chocar con el reinicio Bluetooth de la radio NWD (v0.81.0).
- Menú de Ajustes por categorías (`SettingsScreen.kt`), tema Oscuro / Claro / Automático según faros del auto y hora (`ThemePrefs`), 6 colores de acento elegibles en vivo y registro de cierres inesperados (`CrashLog`) dentro del launcher (v0.80.0).
- Audio al encender y OBD2 en la radio NWD (informes del 08/11 `build 163`): `NwdTuner.bounceToAndroid` despierta el chip de radio (`select` + `wake` + `TX_SET_FREQUENCY`) antes de volver a Android a los 1,8 s, `LastAudio.resume` corre a los 2,5 s y `kickSpotify` rescata a Spotify aunque su sesión figure en pausa (`sonando=false`); en el OBD2, `ExternalBluetoothProbe` deja de abrir y cerrar `/dev/socket/zj_bt_socket` al arrancar y `closeBluetoothSocket` ejecuta `shutdownInput`/`shutdownOutput` sobre el `LocalSocket` interno antes del `close` (v0.79.20).
- Spotify con portadas, búsqueda en vivo sin Enter, sugerencias de recientes y pestaña de letras sincronizadas (`LRCLIB`); reproductor minimizable para ver el mapa a pantalla completa y pulido visual en velocímetro, radio FM y chips del mapa (v0.79.19).
- OBD2 al dar contacto: descarta canales Bluetooth mudos (`answered`), prueba primero la última manera de conectar que funcionó, evita el doble `ATZ` en `initElm` y cierra la conexión vieja apenas se pone contacto o se corta el lector (v0.79.18).
- Al poner contacto, la fuente del estéreo conmuta medio segundo por radio FM antes de volver a Android; Android Auto precalienta su proceso también para teléfonos usados por USB y al volver la red con Android Auto / CarPlay activo se envía `PLAY` para reanudar TuneIn (v0.79.17).
- El registro del encendido se guarda aparte (no lo borran los errores de red) y se anota la red a los 0, 10, 30 y 60 s de poner contacto (v0.79.16).
- CarPlay por cable usa la capa USB del DiPlay original en lugar de la de CarLink, para evitar los cortes a los pocos segundos (v0.79.15).
- Spotify mudo al encender: pasa 4 s al frente (antes 1,5 s) y el registro y la Prueba completa muestran `arm_sound` y el modo de audio de la radio (v0.79.14).
- Spotify mudo al encender: si la radio no lo tiene como audio válido, pasa 1,5 s al frente y se vuelve a Onix (v0.79.13).
- OBD2: cada forma de conectar se verifica con una escritura de prueba y se pasa a la siguiente si no deja escribir (v0.79.12).
- Al encender, si Spotify figura sonando se le da pausa y play para destrabar el audio (v0.79.11).
- El estado del contacto de la radio se lee por su contenido (byte, texto o número), el registro dice dónde se traba el lector OBD2 (v0.79.10).
- Spotify vuelve a sonar si el contacto se puso antes de abrir la app, y el OBD2 ya no queda "conectando…" sin reintentar cuando el Bluetooth se cuelga al cerrar (v0.79.9).
- El clima y el nombre del lugar se actualizan también cuando el auto se aleja más de 3 km del último pedido, no solo cada 15 minutos (v0.79.8).
- Aviso de luces apagadas a cualquier velocidad y hora, mapa con el punto azul cada segundo y aviso de "Buscando GPS…", frecuencia del GPS en la Prueba completa, la casa como ubicación por defecto y APK 3,30 MB más chico (v0.79.7).
- Fase 2 de la auditoría: 40 tests nuevos (lector del OBD2, token de Spotify, red, servicio de la radio), un logo roto ya no cierra la app, la radio no reconecta después de destruirse, Spotify exige el `state` del permiso y el Client ID de Spotify puede venir incluido en la app (v0.79.6).
- Fase 1 de la auditoría 2026-11-05: el Bluetooth ya no puede quedar apagado tras el reinicio del OBD2, el historial de Mi auto no se pierde si lo guardado se daña, APK 1,26 MB más chico, CI más segura y cobertura medida de verdad (v0.79.5).
- Al poner contacto, el audio vuelve solo a Android si sonaba Spotify o internet (v0.79.4).
- CarPlay por cable: el pedido de "Confiar" ya llega al iPhone (paquete USB vacío al final de un envío justo en el borde) (v0.79.3).
- OBD2: una conexión trabada se corta sola y cuenta como falla, así llega el reinicio del Bluetooth (v0.79.2).
- La Prueba completa dice dónde se corta CarPlay (motivo exacto, equipos USB y cada paso del motor) y qué hace el Bluetooth cuando se corta el OBD2 (v0.79.1).
- Mi auto rehecho: Mantenimiento por km, Viajes con litros y resumen por mes, Motor con códigos y borrar andando (v0.79.0).
- Kilometraje del auto por OBD2 si el auto lo informa (v0.78.1).
- Sonido como Google Maps y Waze (todo / solo avisos / silencio) y cada aviso por separado (v0.78.0).
- Lugares cerca, paradas en el camino, Mis lugares escribiendo la dirección y aviso de nafta baja (v0.77.0).
- Destinos desde el celular sin cable: Compartir → ntfy y Onix pregunta "¿Ir a …?" (v0.76.0).
- Búsqueda y rutas con TomTom: encuentra direcciones con altura de Tucumán y rutas con tránsito (v0.75.0).
- Motores de Android Auto y CarPlay de CarLink 1.0.9 y revisión diaria de CarLink para no quedar sin CarPlay (v0.74.0).
- OBD2 que se recupera solo: tiempo máximo para conectar, reintento al dar contacto y reinicio automático del Bluetooth (v0.73.0).
- Voz Comercial por defecto para todo: slogan, aviso de luces, indicaciones y avisos (v0.72.3).
- Indicaciones del GPS y avisos con el Locutor AR y tono serio (v0.72.2).
- Locutor AR (voz argentina creada por el autor en ElevenLabs) por defecto para el slogan y el aviso de luces, con tono serio en el aviso (v0.72.1).
- Voz Comercial y slogan solo "Find new roads"; aviso de luces con la voz elegida; Volver a Android Auto / CarPlay; última tecla en vivo; sin puntitos de página (v0.72.0).
- Rutas en la radio por TLS 1.3 directo cuando OSRM y FOSSGIS no contestan (HU-013); Dock → Teléfono abre la app de teléfono de la radio (v0.71.0).
- CarPlay vuelve a los APK: identidad tomada de CarLink 1.0.9 (AUD-068); voces con IA más tolerantes a los permisos de la clave (v0.70.1).
- Voces con emoción y acento argentino para el slogan de inicio, con ElevenLabs (v0.70.0).
- Voces de locutor para elegir del slogan de inicio, desde Diagnóstico (v0.69.0).
- Descripciones de las releases escritas para el público, sin hablarle al autor; releases anteriores corregidas (v0.68.1).
- APK más liviano (solo 32 bits, lo que usa la radio) y versiones publicadas antes (CI en paralelo) (v0.68.0).
- Auditoría 4 de todo el proyecto: dos fugas chicas de audio corregidas, documentación al día e instructivo completo (v0.67.1).
- Adornos festivos realistas y animados: lucecitas que titilan por todo el auto, bandera que flamea, calabazas con vela (v0.67.0).
- Adornos festivos del techo mejor ubicados (v0.66.1).
- Los temas festivos decoran el auto (nieve, bandera, calabazas…) en vez de saludar (v0.66.0).
- Sonido de inicio con el slogan "Find new roads" y temas festivos de Argentina en Inicio (v0.65.0).
- Auditoría 3: la velocidad ya no se congela con Android Auto o CarPlay encima (Mi auto y avisos); lint completo solo del código de Onix (v0.64.1).
- Salud del auto por sistema en la ventana de códigos y controles de música por Bluetooth sin sesión (ideas de LiteDash) (v0.64.0).
- Aviso de velocidad con voz (máximo elegido en Mi auto) y cobertura de los tests medida en la CI (v0.63.0).
- Prueba completa con Android Auto / CarPlay, memoria por proceso, Mi auto y teclas del volante; guía `docs/PRUEBA_COMPLETA.md` (v0.62.0).
- Voces grabadas para los avisos y la navegación (la radio no tiene TextToSpeech) (v0.61.0).
- Compilación más rápida: el lint completo pasa a un workflow aparte; NewApi sigue revisado en cada build (v0.60.1).
- Segunda auditoría: 10 errores corregidos (audio de Android Auto/CarPlay con el FM, velocidad fija sin GPS, Spotify que se desconectaba, navegación sin señal) (v0.60.0).
- Auditoría: Android Auto y CarPlay piden los permisos y la autorización que les faltaban; viajes sin ruido del GPS (v0.59.0).
- Modo liviano automático en radios lentas y CarPlay listo sin cargar nada (identidad desde el APK público de CarLink, verificado) (v0.58.0).
- CarPlay con su identidad cargada desde GitHub Secrets (v0.57.0).
- Android Auto y CarPlay dentro de Onix con el motor de CarLink: se enchufa el celular por USB y arranca solo; licencia AGPL-3.0 (v0.56.0).
- Herramientas de compilación al día (Gradle 9.5, AGP 9.3, Kotlin 2.2, Compose 2026) y Android mínimo 7.1, para integrar Android Auto y CarPlay (v0.55.0).
- Botón Auto/CarPlay en el dock (abre CarLink); plan para tenerlos dentro de Onix (ADR-0012) (v0.54.0).
- Arranque más rápido (perfiles de arranque) y registro de los botones del volante en el informe (v0.53.0).
- Mi auto: viajes, kilometraje y recordatorios de mantenimiento con aviso en Inicio (v0.52.0).
- Radio FM por la antena del auto con el sintonizador de la radio NWD: todas las emisoras sin internet, búsqueda y RDS (v0.51.0).
- Códigos de falla del motor explicados en castellano, alertas del motor, consumo estimado y aviso de luces apagadas con voz; selector de luces fuera de Inicio (v0.50.0).
- Luces del auto por el aviso de la radio y el estado de su microcontrolador, sin depender del sensor que baja el brillo (v0.49.0).
- Pantallas sin deslizar, brillo al máximo y menos trabajo de fondo (v0.48.0).
- Mapa 3D con MapLibre: inclinado, edificios en 3D, modo oscuro, botón 2D/3D (v0.47.0).
- Búsqueda de direcciones más tolerante y diagnóstico de la conexión con los servidores de rutas (v0.46.0).
- Luces de la app sincronizadas con las luces del auto (señal de la radio NWD) (v0.45.0).
- El informe registra cambios de ajustes y propiedades de la radio para encontrar la señal de luces (v0.44.0).
- Rutas en la radio: TLS 1.3 con Conscrypt en Android 7 (v0.43.0).
- Rutas y búsqueda con respaldo y certificados para Android 7.1, nombre del lugar desde OpenStreetMap, búsqueda de la señal de luces (v0.42.0).
- Diagnóstico con un solo botón, Prueba completa, que guarda el informe de todas las funciones en un .txt (v0.41.0).
- Nombre del saludo editable, reconexión al volver el Wi-Fi y OBD2 que se reinicia solo si el auto no contesta (v0.40.0).
- Lluvia según la hora del día, saludo y presentación animada al abrir y al volver de la suspensión (v0.39.0).
- Revisión técnica: arreglos de seguridad y estabilidad con tests de regresión; firma preparada para GitHub Secrets (v0.38.1).
- Todo dentro del launcher (regla de oro 2) y retomar Spotify o la radio al encender (v0.38.0).
- Navegación giro a giro propia con voz y recálculo, sin Google Maps ni Waze; elegir un punto con el dedo (v0.37.0).
- Mapa con Casa, Trabajo, favoritos y recientes; caché permanente para usarlo sin conexión (v0.36.0).
- Spotify dentro del launcher: listas, recientes y buscador, y reproducir sin abrir Spotify (v0.35.0).
- Segunda pantalla de navegación y música: mapa con buscador y ruta, Spotify y radio sin salir del launcher (v0.34.0).
- Dock personalizable: quitar íconos y agregar cualquier app (v0.33.0).
- Botones Radio ↔ Spotify en la tarjeta de música (v0.32.0).
- OBD2 con el ELM327 por Bluetooth: temperatura, RPM, velocidad, nafta y voltaje en el panel de motor y el Tablero (v0.31.0).
- Emblema de Chevrolet animado (v0.30.1).
- Las luces del auto siguen la perilla de luces (modo noche de la radio por el cable ILL), con modo manual (v0.30.0).
- Fondos de las imágenes nuevas del autor, velocímetro y cuentavueltas redondos, barras de temperatura y nafta, haces de los faros (v0.29.0).
- Fondos generados: cerros, ciudad con luces, cielo propio de día, noche y lluvia (v0.28.0).
- Fondo de la imagen de referencia (día, atardecer, noche y lluvia) y auto más chico (v0.27.0).
- Inicio igual a la imagen de referencia: reloj 12 h, lugar del clima, música con aleatorio y me gusta, telemetría con el auto visto desde arriba, dock de 8 accesos y cajón de apps (v0.26.0).
- Rediseño visual de Inicio: paleta grafito y azul, encabezado con reloj y clima, pronóstico de 5 días, dock con Inicio y franja del vehículo (v0.25.0).
- Cadenas (Mitre, Radio 10, Vale, Cadena 3, María, etc.) con señal nacional cuando no hay de Tucumán (v0.24.0).
- 19 radios de Tucumán con enlace de audio nuevo; La 100 va directo a la única señal que existe online (v0.23.0).
- La 100 siempre visible; la tarjeta de música pasa a Spotify cuando suena (v0.22.2).
- El informe trae el detalle de la revisión de radios, con el motivo de cada falla (v0.22.1).
- Diagnóstico: probar una radio y copiar un informe para pegarlo en el chat (v0.22.0).
- Logos para 71 de las 77 radios del dial; La 100 LOCAL prueba más señales; regla de oro de documentación verificada por la CI (v0.21.0).
- La radio se ve y se controla desde el inicio y el tablero; notificación en segundo plano (v0.20.0).
- Solo radios que funcionan (revisión en el equipo) y categorías (v0.20.0).
- Más radios con logo (página web de la emisora, búsqueda por nombre, íconos .ico) y diagnóstico de la señal local de La 100 (v0.19.2).
- La radio ya no se pausa sola: reconecta si se corta y sigue con la pantalla apagada (v0.19.1).
- Logos de las radios en el display y en las presintonías.
- Favoritas: estrella, mantener apretado, filtro ★ FAVORITAS.
- La 100 y Mitre vuelven a sonar (StreamTheWorld, redirecciones https/http, HLS).
- Velocímetro de arco con degradé y brillo al lado del auto (v0.18.0).
- La CI crea el tag `vX.Y.Z` de cada versión.

## Compatibilidad

- Se instala encima de v0.13.0 en adelante (misma firma).
- Desde v0.12.0 o anteriores hay que desinstalar una vez.
- Mínimo Android 7.0 (API 24), objetivo 34. Pensado para el Android 7.1 del head unit; probado en el celular y en la radio del autor.

## Fuente de la versión

- `version.properties` → `versionName` (SemVer). Única fuente de verdad.
- `versionCode = 100 + GITHUB_RUN_NUMBER` (sube en cada compilación, necesario para instalar encima).
- La app muestra `0.18.0 · build N` en Diagnóstico y Acerca de.
- Cada compilación tiene su release `build-N`; la CI crea el tag `vX.Y.Z` en la primera compilación de cada versión.

### Cómo sacar una versión nueva

1. Cambiar `version` en `version.properties`.
2. Mover `[Unreleased]` a la nueva versión en `CHANGELOG.md`; actualizar este archivo y `docs/releases/vX.Y.Z.md`.
3. Push a `main` → la CI corre los tests, publica `build-N` y crea el tag `vX.Y.Z`.

## Próxima versión

- Prueba en el head unit (foto de Diagnóstico) y ajustes de resolución y rendimiento.
- OBD2 con el ELM327 por Bluetooth.
