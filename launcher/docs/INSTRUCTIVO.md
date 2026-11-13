# Instructivo completo para el autor (todo lo que tenés que hacer vos)

Actualizado: 2026-11-04 · v0.68.0 (auditoría 4 y APK más liviano). Todo lo que se podía hacer desde el código está hecho y probado en la CI;
lo que queda **solo se puede probar en el auto**. Claude retoma con lo que mandes: los informes de la Prueba completa y lo que notes vos.

**Orden recomendado:** 1 → 2 → 3 (una tarde, con el auto en marcha y parado) → 4 (las Pruebas completas) → 5 (lo que el informe no ve) → 6 (opcionales, cuando tengas un rato con la computadora).

**Qué necesitás a mano:** el auto con la radio prendida, internet en la radio (WiFi del celular compartido), el lector OBD2 ELM327 enchufado, un celular Android con su cable USB y, si tenés, un iPhone con su cable.

---

## 1. Instalar la versión nueva (5 min)

1. En la radio (o en el celular, y después pasalo), entrá a GitHub → repo **onix-launcher** → **Releases**.
2. Bajá `onix-launcher.apk` de la release **más nueva** ("Onix Launcher v0.68.0" o mayor; pesa bastante menos que antes porque solo trae lo que usa la radio). Arriba de cada release dice qué trae de nuevo y el resultado de los tests.
3. Instalala **encima**, sin desinstalar: se conservan tus radios, lugares, viajes, kilometraje y ajustes. Si Android pregunta por "orígenes desconocidos", permitilo para el navegador o el administrador de archivos que uses.
4. Si tenés instalada la app **CarLink** (u otra de Android Auto / CarPlay, como AutoKit o ZLink), **desinstalala**: puede quedarse con el USB y Onix no arranca la proyección.
5. Abrí Onix. En **Diagnóstico** (abajo) se ve la versión: tiene que decir `0.68.0 · build …` o mayor. Nota: desde v0.68.0 el APK es solo para la radio (32 bits); en un celular nuevo solo de 64 bits no se instala, y no hace falta.

## 2. Configuración inicial (una sola vez, 10 min)

Cada paso se hace una vez; después queda guardado.

| # | Qué | Cómo | Para qué |
|---|---|---|---|
| 2.1 | Usar Onix como inicio | Ajustes → Sistema → **Usar como inicio** → elegí **Onix** y "siempre"; la fila tiene que quedar en "Onix es la pantalla de inicio" | Que el botón de inicio de la radio vuelva a Onix |
| 2.1b | Launcher que protege la radio (v0.81.13) | Ajustes → Diagnóstico → fila **Inicio según la radio** | Anotá lo que dice. Si protege a otro launcher, la radio puede volver al suyo aunque elijas Onix: ese valor (`persist.nwd.launcher.default`) solo se cambia por ADB |
| 2.2 | Ubicación | Al abrir, Onix la pide: **Permitir** | Velocidad por GPS, clima de tu lugar, navegación |
| 2.3 | Micrófono (y ubicación del motor) | A los ~6 s de abrir, Onix pide el resto: **Permitir** todo | Android Auto y CarPlay no arrancan sin esto |
| 2.4 | Acceso a la música | Panel de música → **Dar acceso** → activá Onix en "Acceso a notificaciones" → volvé | Ver y controlar lo que suena (Spotify, Bluetooth, otras apps) |
| 2.5 | Notificaciones | La primera vez que abrís **Radios**: **Permitir** | Controles de la radio por internet en segundo plano |
| 2.6 | Lector OBD2 | En los ajustes de Bluetooth de la radio, emparejá el ELM327 (clave habitual `1234` o `0000`). Onix lo encuentra solo | Datos del motor, códigos, consumo, salud del auto |
| 2.7 | Spotify | **Una sola vez, en GitHub** (v0.79.6): secreto `SPOTIFY_CLIENT_ID` con el Client ID de tu app de developer.spotify.com (Redirect URI `onixlauncher://spotify`); después compilar. Con ese APK: Panel de Spotify → **Conectar con Spotify** → iniciá sesión → volvé. Sin el secreto, el panel pide pegar el Client ID | Ver tus listas y buscar dentro de Onix |
| 2.8 | Kilometraje | Dock → **Mi auto** → **Cargar km del tablero** → poné el número del tablero | Que Mi auto lleve el kilometraje real |
| 2.9 | Mantenimiento | Mi auto → **Mantenimiento** → tocá cada ítem (aceite, filtros, alineación, neumáticos…) y escribí a qué km lo hiciste | Que diga cuánto falta para cada uno |
| 2.10 | Aviso de velocidad | Ajustes → Sonido y avisos → **Qué avisa** → botón **Velocidad** → tocá hasta el máximo que quieras (60 a 130) o "no" | Cartel, pitido y voz si te pasás |
| 2.11 | Tu nombre y el inicio | Tocá **"Hola, …"** en Inicio: nombre, **Sonido de inicio** (voz y sonido / solo sonido, por defecto / apagado), **Temas festivos** (sí / no) | Saludo, sonido al prender, adornos en fechas especiales |
| 2.12 | CarPlay por cable (solo si usás iPhone) | La primera vez que enchufás el iPhone aparece **Autorizar**: tocalo y aceptá el aviso de Android (es una "VPN" local, no sale a internet). Antes: iPhone **desbloqueado** al enchufar, CarPlay permitido en Ajustes → Tiempo en pantalla → Restricciones → Apps permitidas, y un cable que **pase datos**. Si no conecta, hacé la Prueba completa con el iPhone enchufado (desde v0.79.1 dice en qué paso se corta) | CarPlay por cable lo necesita |

## 3. Probar cada función (30–40 min, con el auto en marcha)

Anotá en cada fila **anda / no anda / raro** y lo que viste. Si algo falla, no importa: es justo lo que Claude necesita saber.
Entre paréntesis, el código de la prueba en `docs/TEST_PLAN.md`.

### 3.1 Parado, motor en marcha

| # | Qué | Cómo | Qué tiene que pasar |
|---|---|---|---|
| 1 | **Android Auto** (R-48) | Enchufá el Android por USB. Si Android pregunta con qué app abrir, elegí **Onix** y "siempre". Usalo 2–3 min (mapa, música, una llamada si podés). Desenchufá | Arranca solo. **Contá los segundos** desde que enchufás. Se escucha la música y la voz del mapa. Al desenchufar vuelve a Inicio |
| 2 | **CarPlay** (R-50, R-62) | **Instalá primero la versión 0.70.1 o posterior**: las anteriores (desde la 0.59.0) salieron sin CarPlay (AUD-068). Enchufá el iPhone por USB; si aparece **Autorizar**, tocalo y aceptá | Arranca solo. **Contá los segundos**. Música, mapas, Siri. Al desenchufar vuelve a Inicio |
| 3 | **Radio FM por la antena** (R-43) | Radio → interruptor **ANTENA** → tocá La 100 (95.5); probá ⏮ ⏭; mové el dial a una frecuencia vacía; poné Spotify y volvé a la radio | Suena el FM sin internet; ⏮ ⏭ paran en emisoras; se ve el nombre (RDS); las presintonías de la radio de fábrica no cambian |
| 4 | **Radio por internet** | Radio → **LOCAL** o **NACIONAL** → tocá una emisora | Suena; logo y nombre correctos. Anotá las que no anden |
| 5 | **Spotify** | Panel de Spotify: tocá una lista, buscá un tema | Suena; carátula y progreso se mueven. **Contá cuánto tarda** en arrancar |
| 6 | **Música por Bluetooth** (R-56) | Poné música por Bluetooth desde la app de la radio; usá ⏮ ⏯ ⏭ de Onix | Cambia de tema y pausa. Si no, anotá qué app de Bluetooth es |
| 7 | **OBD2 y salud del auto** (R-39, R-55, R-69) | Mirá el panel del motor en Inicio; tocá la línea de códigos; **Leer de nuevo** | Datos del motor (vueltas, temperatura, voltaje). Ocho sistemas con su color (verde si no hay códigos). Coincide con la luz del motor del tablero |
| 8 | **Luces** (R-42) | Prendé y apagá las luces del auto; mirá la franja de abajo de Inicio | Dice "Luces prendidas / apagadas" según el auto; la pantalla se oscurece de noche |
| 9 | **Sonido de inicio** (R-58, R-60) | En **Ajustes → Sonido y avisos → Sonido de inicio** tocá cada voz (Locutor, Épico, Locutor 2, Británico, Grave, Daniela, Original) y quedate con la que más te guste; después apagá y prendé la pantalla | Cada voz suena al tocarla; la presentación dice "Find new roads" con la elegida. Con "Solo sonido" no habla; con "Apagado", nada; con Android Auto conectado no suena |
| 10 | **Temas festivos** (R-59) | Tocá "Hola, …" → **Ver tema** varias veces, mirando el auto de Inicio | Cada fiesta decora el auto: lucecitas que titilan (techo, capó, ventanilla), nieve y moño en Navidad; bandera que flamea en fechas patrias; calabazas con vela en Halloween; corazones, banderines, flores. **Contame si algún adorno queda mal ubicado** (mejor con una foto de la pantalla) |
| 11 | **Modo liviano** (R-51) | Ajustes → Apariencia → **Modo liviano** | Dice "automático (sí)" o "(no)". Anotá cuál |
| 12 | **Navegación** (R-63, R-71) | Navegación → buscá "9 de Julio 550, Tafí Viejo" o un lugar cercano → **Ir** | Aparece la ruta; arranca la guía con voz. Desde v0.71.0 la ruta se pide por TLS 1.3 directo si falla lo normal |
| 12e | **Lugares y paradas** (R-73 a R-75) | Mapa → **⛽ Combustible**; navegando → **Parada**; **✎** → escribí Casa y Trabajo | Lista con distancias; la ruta pasa por la parada; "Guardado: …" |
| 12b | **Teléfono** (R-64) | Dock → **Teléfono** | Se abre la app de teléfono Bluetooth de la radio (antes: "No se pudo abrir") |
| 12c | **Volver a Android Auto** (R-67) | Con Android Auto andando, tocá Inicio de Onix y después **Auto / CarPlay** en el dock | Aparece **Volver a Android Auto** (vuelve) y **Desconectar** (corta) |
| 12d | **Última tecla** (R-68) | Diagnóstico abierto: apretá uno por uno los botones de la radio (y del volante, si hay) | En "Última tecla" aparece qué manda cada uno: anotalo o sacale foto |

### 3.2 Andando (en un lugar seguro; si podés, que maneje otro y vos mirás)

| # | Qué | Cómo | Qué tiene que pasar |
|---|---|---|---|
| 13 | **Voces de navegación** (R-52) | Navegá al lugar de la fila 12 | "En doscientos metros, girá a…", "Llegaste a destino". La música baja mientras habla |
| 14 | **Aviso de velocidad** (R-54) | Aviso en 60; pasá los 63 km/h unos segundos | Cartel naranja "Velocidad alta", pitido y voz; no repite antes de 2 min |
| 15 | **Avisos con el FM y con Android Auto** (R-57) | Repetí la fila 14 con el FM por la antena sonando, y después con Android Auto en pantalla | Se escucha el pitido y la voz en los dos casos |
| 15a | **Aviso de luces apagadas** (R-90, v0.79.7) | Con las luces apagadas, de día, empezá a andar a paso de hombre (2 a 5 km/h) | A los 5 segundos: cartel, pitido y la voz "Atención: las luces están apagadas…". No repite antes de 10 minutos. Parado, o con las luces prendidas, no avisa. Si no salta, mirá en Diagnóstico → "Aviso de luces" si la radio informa las luces |
| 15b | **GPS del mapa** (R-89, v0.79.7) | Al prender el auto, abrí el mapa antes de salir; manejá unos minutos con el mapa abierto; pasá por un túnel o cochera si hay; al terminar mandá la **Prueba completa** | Mientras no hay GPS el mapa dice "Buscando GPS…" (no te ubica en la casa sin avisar); el punto azul se mueve cada segundo; sin señal dice "Sin señal de GPS hace N s". En la Prueba completa, GPS → "Posiciones en vivo (frecuencia)": intervalo medio cerca de 1 s. **Si el intervalo o el retraso salen altos, la lentitud es de la radio** |
| 15c | **Clima y lugar al cambiar de zona** (R-91, v0.79.8) | Con el Inicio abierto, salí de Tafí Viejo hacia el centro de San Miguel de Tucumán | A los pocos minutos de pasar los 3 km, el nombre del lugar y el clima cambian solos, sin esperar los 15 minutos |
| 15d | **Spotify al encender con la app recién arrancada y OBD2** (R-92, v0.79.9) | Con Spotify como última fuente, apagá el auto del todo y volvé a poner contacto; con el lector OBD2 enchufado | Spotify suena solo a los pocos segundos; en Últimos eventos "Al encender: fuente de la radio N; devuelvo el audio a Android". Si el lector queda "conectando…", aparecen fallas y reintentos; a la tercera, "reinicio el Bluetooth" |
| 15e | **Contacto y lector OBD2** (R-93, v0.79.10) | Apagá el auto y volvé a poner contacto; con el lector enchufado, mirá Últimos eventos | "Contacto: la radio avisó estado=1" y después "Al encender: fuente de la radio N…". Si el lector no conecta, el evento de "trabado" dice el paso ("escribiendo ATZ" o "esperando la respuesta a ATZ"). Probá también desenchufar y volver a enchufar el lector del auto |
| 15f | **Spotify al encender** (R-94, v0.79.11) | Con Spotify como última fuente, apagá el auto y volvé a poner contacto | Suena solo, con un corte breve a los pocos segundos. En Últimos eventos: "Al encender: Spotify figura sonando; le doy pausa y play…". Si sigue mudo, abrí la app de Spotify y contame si ahí suena |
| 15g | **Lector OBD2** (R-95, v0.79.12) | Con el lector enchufado, auto en marcha, mirá Últimos eventos | "OBD2: conectado" y datos del motor. Si la manera 1 de conectar no deja escribir, aparece "la manera 1… falló… pruebo la siguiente" y luego "la manera N de conectar deja escribir". Si falla de las tres, mandá la Prueba completa |
| 15r | **Bluetooth emparejado en el informe** (R-105, v0.81.25) | Ajustes → Diagnóstico → Prueba completa → Iniciar | En la sección OBD2 hay una fila "Bluetooth emparejado con la radio" con nombre, tipo y si está conectado cada equipo. Si el lector OBD2 no conecta y ahí figura otro equipo conectado, el lector está tomado por ese equipo |
| 15q | **Actualizar con el token bien** (R-104, v0.81.24) | Ajustes → Sistema → Actualizaciones | Si dice "Sin conexión" con internet andando, había un texto raro guardado como token: borrar `actualizaciones.xml` (ver nota de la versión). Después de actualizar, no copiar nada antes de "Cambiar token" salvo el token |
| 15p | **Informe que llega solo a la PC** (R-103, v0.81.23) | Ajustes → Diagnóstico → Prueba completa → Iniciar | Al terminar dice "Enviado a la PC". Sin internet dice que se envía cuando vuelva la red. No hace falta copiarlo ni mandarlo: avisá que la corriste |
| 15o | **Red que se recupera sola** (R-101, v0.81.19) | Con la radio en la red del celular, apagá los datos del celular dos minutos y prendelos de nuevo | En uno o dos minutos la radio vuelve a tener internet sin tocar nada (el Wi-Fi se apaga y prende solo). Mandá el informe |
| 15n | **Retomar la música** (R-100, v0.81.18) | Ajustes → Sonido y avisos → "Retomar la música al dar contacto": poné **No**. Con Spotify sonando, apagá y prendé el contacto | Spotify queda en pausa. Tocá play: tiene que escucharse. Con **Sí** sigue solo, como antes. "Desconectar Spotify" ahora está en esa misma pantalla |
| 15m | **Temas de una lista y letra al pie** (R-99, v0.81.17) | Navegación → Música → Listas: tocá una lista | Aparecen sus temas. Tocá uno del medio: suena ese y después sigue con los siguientes. "▶ Todo" reproduce la lista entera y "‹ Listas" vuelve. Fijate si la letra al pie se lee bien |
| 15l | **Spotify al dar contacto** (R-98, v0.81.16) | Con Spotify sonando, apagá el contacto, esperá 20 segundos y prendé | La música sigue y la pantalla de Spotify no se abre. Probá también con Google Maps guiando: decí si la voz se escucha bien encima de la música |
| 15k | **Sonido de inicio nuevo** (R-97, v0.81.15) | Apagá y prendé la radio | Suena un acorde suave con dos notas claras, sin la voz del slogan. Decí si gusta y si el volumen está bien. La voz se vuelve a activar en Ajustes → Sonido y avisos |
| 15j | **Lector OBD2 y el celular** (R-96, v0.81.14) | El lector atiende a un solo equipo. Con el Bluetooth del celular apagado (o sin Torque abierto en el celular), poné contacto y esperá un minuto | "OBD2: conectado". Si el celular tiene el lector, Onix dice "lector apagado o en uso por otro equipo" y reintenta cada vez más espaciado; al soltarlo desde el celular, conecta al dar contacto |
| 15h | **Lector OBD2 simplificado** (R-95, v0.81.13) | Con Torque cerrado (compite por el único canal del lector), poné contacto y esperá un minuto; después andá unos minutos | "OBD2: conectado" y que no se corte andando. Con contacto y motor apagado puede decir "el auto no contesta… reinicio el lector sin cortar el Bluetooth": es lo esperado. Mandá la Prueba completa, conecte o no |
| 15h | **Spotify al encender** (R-96, v0.79.13) | Con Spotify como última fuente, apagá el auto y volvé a poner contacto | Spotify se ve 1,5 s y vuelve Onix; el sonido sale solo. En Últimos eventos: "audio válido=…, sonando=…" y, después de pasarlo al frente, los dos números iguales. Mandá la Prueba completa si sigue mudo |
| 15i | **Registro del encendido y red** (R-97, v0.79.16) | Con Spotify como última fuente, apagá el auto y volvé a poner contacto. Si no suena, hacé la Prueba completa sin tocar nada | En la Prueba completa, sección "Registro del encendido": "Contacto…", "Al encender…" y "Red al encender (+0, +10, +30, +60 s)". Sirve para saber si faltó internet o si fue el audio |
| 16 | **Aviso de luces apagadas** (R-41) | De noche, con las luces apagadas, andá a más de 20 km/h unos 15 s; prendelas | Cartel, pitido y voz; al prenderlas se va; no repite antes de 10 min |
| 17 | **Consumo** (R-40) | Mirá el panel del motor andando en ciudad y en ruta | Aparece un valor de km/l andando ("--" parado), razonable para el Onix |
| 18 | **Mi auto** (R-44, R-80) | Hacé un viaje de más de 1 km y apagá el auto | Aparece el viaje con km, tiempo, máxima y km/l; los km se parecen a los del tablero; el kilometraje sube |
| 19 | **Velocidad con Android Auto encima** (R-57) | Abrí Android Auto andando, estacioná, esperá 5 min, desenchufá y mirá Mi auto | El viaje terminó y no siguió sumando km parado |

## 4. Las cuatro Pruebas completas (lo que más le sirve a Claude)

Ajustes → Diagnóstico → **Prueba completa** → **Iniciar**. Tarda hasta 2 min; **no toques nada mientras corre**. Guía completa: `docs/PRUEBA_COMPLETA.md`.

| Prueba | Situación | Antes de tocar el botón |
|---|---|---|
| **A** | Motor en marcha, parado, OBD2 enchufado, FM por la antena sonando, **aire apagado** | Esperá 1 min con Onix abierto (que conecte el OBD2 y cargue el clima) |
| **B** | Igual que A pero con el **aire prendido** | Prendé el aire y esperá 1 min |
| **C** | Justo después de usar **Android Auto o CarPlay** 2–3 min y desenchufar | Hacela enseguida al volver a Onix |
| **D** | Después de **manejar** más de 1 km (si se puede de noche y navegando) | Hacela al llegar |

Cada una guarda un `.txt` en **Descargas/OnixLauncher** y lo copia al portapapeles. Pegalo entero en el chat (o subí el archivo) y **decí cuál es (A, B, C o D)**.
La primera vez Onix puede pedir permiso de almacenamiento para guardarlo en Descargas: aceptalo (si no, queda en la carpeta de la app).

## 5. Lo que el informe no ve (contalo vos)

- ¿Se escuchan bien las voces, el pitido y el sonido de inicio? ¿Baja la música cuando hablan?
- ¿Algo anda lento o se traba? ¿En qué pantalla? (Inicio con adornos festivos, mapa, radio…)
- ¿Cuántos segundos tarda en arrancar Android Auto, CarPlay y Spotify?
- ¿Mi auto anotó el viaje? ¿Los km se parecen a los del tablero?
- ¿Algún adorno festivo queda mal ubicado sobre el auto? (una foto ayuda mucho)
- ¿Qué emisoras de radio por internet no andan?
- Cualquier cosa rara: un cartel que no se va, una pantalla en blanco, algo que se cerró solo (Diagnóstico → **Último error** lo dice).

## 6. Opcionales (cuando tengas un rato)

| Qué | Para qué | Cómo |
|---|---|---|
| **Spotify más rápido al prender** (HU-014) | Que la radio no cierre Spotify ni Onix al apagarse | Con la app **Bugjaeger** en el celular, conectado a la radio: comandos de `docs/RADIO_NWD.md` → "Procedimiento" (primero la copia de seguridad, después agregar Spotify y Onix a las listas de la radio, reiniciar) |
| **Firma en GitHub Secrets** (AUD-001) | Sacar la clave de firma del repo | En la web de GitHub, con una computadora: `docs/SIGNING.md` → "Opción A" (cargar `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). **Nunca pegues la clave ni las contraseñas en un chat** |
| **Destinos desde el celular** (v0.76.0) | Mandar una dirección o ubicación de WhatsApp a Onix sin cable | En el celular: instalar **ntfy** (Play Store) → **+** → nombre del canal (uno largo y difícil de adivinar). En GitHub: secreto `NTFY_TOPIC` con ese mismo nombre. Después: Compartir → ntfy → el canal |
| **Voces con emoción** (v0.70.0) | Locutor argentino, "¡Arrancamos!" y asistente de IA en el sonido de inicio | Cuenta gratis en ElevenLabs y el secreto `ELEVENLABS_API_KEY` en GitHub: pasos en `docs/VOCES_IA.md`. **Nunca pegues la clave en un chat** |
| **Botones del volante** | — | No aplica: el Onix Joy 2018 no tiene |

## 7. Para retomar con Claude (web, app o Code)

1. Decile: *"Leé `docs/tasks/ESTADO.md` y `CLAUDE.md` del repo onix-launcher en main"*.
2. Pegale los informes A, B, C y D (decí cuál es cuál) y lo que anotaste en las secciones 3 y 5.
3. Con eso Claude arregla lo que falle, ajusta lo que haga falta (memoria del motor de Android Auto, aire acondicionado por OBD2, ubicación de los adornos) y publica la versión siguiente con su descripción en GitHub.
