# Voces con emoción (ElevenLabs)

Desarrollado por Axel Hernán González · © 2026 Axel Hernán González · Licencia AGPL-3.0

Desde v0.70.0 el slogan de inicio puede tener voces con emoción y acento argentino generadas con
[ElevenLabs](https://elevenlabs.io). Se generan **una sola vez en la CI** (workflow
`voces_ia.yml`) y quedan como audios dentro del APK: la radio no necesita internet ni ninguna clave.

## Qué hace falta (una vez, desde la PC o el celular)

1. Crear una cuenta gratis en https://elevenlabs.io (plan gratuito: sin tarjeta; uso personal, no
   comercial y con crédito a ElevenLabs, que queda en Acerca de).
2. En ElevenLabs: perfil → **API Keys** → **Create API Key**. Permisos mínimos: *Text to Speech* y
   *Voices* (lectura y escritura, para sumar las voces argentinas de la biblioteca). Copiar la clave.
3. En GitHub, en el repositorio: **Settings → Secrets and variables → Actions → New repository
   secret**. Nombre: `ELEVENLABS_API_KEY`. Valor: la clave. **Nunca pegarla en un chat.**
4. En GitHub: **Actions → Voces con IA (ElevenLabs) → Run workflow**. Tarda un par de minutos y
   guarda los audios en el repositorio (commit "voces: generadas con ElevenLabs").
5. Avisarle a Claude para que compile una versión con las voces nuevas.

**Si el log dice `missing the permission voices_read` (pasó la primera vez, 2026-11-04):** la clave
se creó con permisos restringidos. En ElevenLabs → **API Keys** → editar la clave → en *Voices* elegir
**Read** y **Write** (y *Text to Speech*: Access), guardar y volver a correr el workflow. Sin esos
permisos (o si el plan gratuito no deja usar la biblioteca desde la API) el script usa voces
prediseñadas de ElevenLabs (Brian, Sarah, George): con emoción, pero sin acento argentino.

## Qué voces se generan

Se configuran en `tools/voces/elevenlabs.txt`:

| id | Voz | Frase |
|---|---|---|
| `comercial` (v0.72.0) | Voz diseñada: locutor estadounidense de comercial de autos, grave y cálido | "Find new roads." |
| `ar_locutor` | Voz diseñada: locutor de FM porteño, acento rioplatense, con energía | "Find new roads!" |
| `ar_locutora` | Voz diseñada: locutora porteña, acento rioplatense, cálida | "Find new roads!" |
| `asistente` | George (voz británica de ElevenLabs), estilo asistente de IA sereno | "Find new roads." |

**Plan gratuito (04/11/2026):** ElevenLabs no deja usar voces de la biblioteca por la API sin plan pago (HTTP 402 "Free users cannot use library voices via the API"). Por eso las voces argentinas y la Comercial se crean con el **diseño de voces** (Voice Design) a partir de una descripción; si eso tampoco anda, se usa la voz prediseñada más parecida. Las voces diseñadas son nuevas: no imitan a ninguna persona.

Además, por la API el plan gratuito tampoco deja **crear** voces (HTTP 403 "Creating a voice through the API is only available on a paid plan"). En la **web** sí se puede, gratis. Para tener acento argentino y el locutor de comercial:

1. Entrar a https://elevenlabs.io → **Voices** → **Create a voice** (o "Add a new voice") → **Voice Design**.
2. Pegar la descripción, tocar **Generate**, escuchar las opciones y guardar la que guste con **este nombre exacto**:

| Nombre al guardar | Descripción para pegar |
|---|---|
| `Onix Comercial` | Deep, warm and confident American male announcer for a car commercial, cinematic, slow and powerful delivery |
| `Onix Locutor AR` | Argentine man from Buenos Aires with a strong rioplatense Spanish accent, energetic and enthusiastic FM radio announcer |
| `Onix Locutora AR` | Argentine woman from Buenos Aires with a rioplatense Spanish accent, warm and energetic radio host |

3. Avisarle a Claude (o en GitHub: Actions → Voces con IA (ElevenLabs) → Run workflow). El script usa esas voces por su nombre; si alguna no está, sigue con una prediseñada distinta para cada fila.

**Frases (v0.72.2; voz Comercial desde v0.72.3, la elegida por el autor para todo):** las indicaciones y avisos de `tools/voces/frases.txt` también se graban con la voz Comercial y tono serio (`ia_<clave>.ogg`); la app las prefiere a las de Piper.

**Hecho (04/11/2026):** el autor creó las tres voces en la web y la CI las usó. Desde v0.72.1 el **Locutor AR** es la voz por defecto del slogan y del aviso de luces; el aviso se graba con tono serio (estabilidad 0,8, estilo 0).

Desde v0.72.0 todas dicen solo el slogan (pedido del autor). Además, cada una graba el aviso de
luces ("Atención: las luces están apagadas. Encendelas para un viaje más seguro.") en
`luces_<id>.ogg`: el aviso usa la voz elegida en Diagnóstico.

El estilo de asistente es una voz original de ElevenLabs: no imita la voz de ningún actor ni
personaje. Qué voz de la biblioteca se usó queda en `app/src/main/assets/voz/LEEME_ia.txt`.

## Para quitarla

Borrar el secreto `ELEVENLABS_API_KEY`. Los audios ya generados quedan hasta que se borren de
`app/src/main/assets/voz/`.
