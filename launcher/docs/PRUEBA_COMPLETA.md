# Cómo hacer la Prueba completa (para que sirva)

La Prueba completa (Ajustes → Diagnóstico → **Prueba completa** → **Iniciar**) revisa todo el launcher, arma un informe `.txt`, lo guarda en `Descargas/OnixLauncher` y lo copia. Ese informe es lo que Claude usa para seguir mejorando la app: **pegalo entero en el chat** (o subí el `.txt`).

Tarda hasta 2 min. No toques nada mientras corre.

## Qué revisa (v0.67.1)

| Área | Qué dice |
|---|---|
| Equipo y sensores | Fluidez (cuadros/s), memoria de la app, RAM, pantalla, Android, procesador, versión |
| Red e Internet | Conexión y cada servicio (clima, radios, mapa, búsqueda, rutas, Spotify) con su tiempo |
| Registro del encendido (v0.79.16) | Contacto, fuente de la radio, audio y red a los 0, 10, 30 y 60 s de poner contacto; se guarda aparte de "Últimos eventos" |
| GPS y Clima | Permiso, GPS prendido, última ubicación, **frecuencia de las posiciones** (cada cuánto llegan y con qué retraso, v0.79.7), clima y nombre del lugar |
| Luces | Señal de luces de la radio, modo, brillo y avisos de la radio NWD |
| OBD2 | Bluetooth emparejado con la radio (v0.81.25), lector conectado, datos del motor, códigos de falla, **salud por sistema** (v0.67.1), consumo, alertas y respuestas crudas |
| Radio | Sintonizador FM por la antena, prueba de la emisora, catálogo |
| Música y Spotify | Acceso a las sesiones, app instalada, cuenta (con el Client ID: "viene en la app" o "pegado a mano") y listas |
| Navegación | Voz del equipo, **voces grabadas** (y cuáles faltan), lugares, mapa guardado |
| Auto/CarPlay | Estado del motor con el **motivo exacto** si falló (v0.79.1), permisos que faltan, autorización de VPN, otra app de proyección instalada, **equipos USB** enchufados (Apple = `05ac`) y, abajo, **cada paso del motor** con la hora, el registro de CarLink y el del motor de CarPlay (`diplay.log`) |
| Equipo | **Memoria de cada proceso** (la app y el motor de Android Auto `:aa`) y modo liviano |
| Mi auto | Kilometraje, viajes, recordatorios, **aviso de velocidad** elegido y el viaje en curso |
| Volante | Cada tecla distinta que llegó a la app desde que se abrió. El Onix Joy 2018 no tiene botones en el volante: solo aparecen las teclas físicas de la radio, si manda alguna |
| Permisos, espacio, interfaz | Permisos concedidos, espacio libre, dock, saludo, **sonido de inicio y tema festivo de hoy** |
| Al final | Ajustes y propiedades de la radio, avisos recientes y los últimos 80 eventos (errores incluidos) |

## Cómo hacerla para que sirva

Son **cuatro pruebas** (A a D, las mismas de `docs/INSTRUCTIVO.md`), una en cada situación. Cada una cuenta cosas distintas. Al pegar cada informe, decí cuál es (A, B, C o D).

### Prueba A: auto en marcha, parado, aire apagado
1. Motor en marcha, lector OBD2 enchufado, WiFi o datos del celular compartidos.
2. Abrí Onix y esperá 1 minuto (que conecte el OBD2 y cargue el clima).
3. Poné el FM por la antena (Radio → ANTENA) y dejalo sonar.
4. Aire acondicionado **apagado** desde hace 1 minuto.
5. Ajustes → Diagnóstico → **Prueba completa** → **Iniciar**.

### Prueba B: igual, con el aire prendido
1. Sin cambiar nada más, **prendé el aire** y esperá 1 minuto.
2. Ajustes → Diagnóstico → **Prueba completa** → **Iniciar**. Comparando A y B se ve si el OBD2 nota el compresor (vueltas en ralentí, carga del motor; `docs/tasks/aire-acondicionado.md`).

### Prueba C: justo después de Android Auto o CarPlay
1. Enchufá el celular por USB y esperá a que arranque Android Auto o CarPlay. Usalo 2 o 3 minutos (mapa y música).
2. Desenchufá, volvé a Onix y hacé la **Prueba completa** enseguida.
3. Así se ve cuánta memoria quedó usando el motor (`:aa`, AUD-041) y si la radio se pone lenta.
4. Anotá cuánto tardó en arrancar desde que enchufaste.

### Prueba D: después de manejar
1. Hacé un viaje corto (más de 1 km), si se puede de noche y con una navegación a algún lugar cercano.
2. Al llegar, **Prueba completa**.
3. Contá: ¿se escucharon las voces ("En doscientos metros…")? ¿saltó el aviso de luces si las tenías apagadas? ¿se armó el viaje en Mi auto?

## Lo que el informe no puede saber (contalo vos)

- Si **se escucha** bien (voces, FM, Android Auto) y si la música baja cuando habla.
- Si algo **se ve lento** o se traba, y en qué pantalla.
- Cuánto tarda en abrir Spotify o arrancar Android Auto / CarPlay (contá los segundos).
- En cuál de las pruebas el aire estaba prendido.

## Qué no tiene el informe

No tiene contraseñas ni tokens: Spotify solo dice si hay sesión. Sí tiene la ubicación aproximada (precisión del GPS, no las coordenadas) y el nombre del lugar.
