# Componentes de terceros

Componentes, servicios y recursos que **no** son propiedad de Axel Hernán González.
"No verificado" significa que no se pudo confirmar la licencia o el origen desde una fuente.

## Librerías

| Componente | Versión | Autor | Licencia | Uso |
|---|---|---|---|---|
| Kotlin | 2.2.10 | JetBrains | Apache 2.0 | Lenguaje |
| Jetpack Compose (BOM) | 2026.02.01 | Google / AOSP | Apache 2.0 | Interfaz |
| Material 3 (Compose) | según BOM | Google / AOSP | Apache 2.0 | Componentes de texto |
| AndroidX Activity Compose | 1.9.0 | Google / AOSP | Apache 2.0 | Activity |
| AndroidX Core KTX | 1.13.1 | Google / AOSP | Apache 2.0 | Utilidades |
| AndroidX Media3 (ExoPlayer, Session, HLS, DataSource) | 1.3.1 | Google / AOSP | Apache 2.0 | Reproducción de radio |
| AndroidX Annotation Experimental | 1.4.0 | Google / AOSP | Apache 2.0 | `@OptIn` |
| AndroidX ProfileInstaller | 1.3.1 | Google / AOSP | Apache 2.0 | Perfiles de arranque (v0.53.0) |
| osmdroid | 6.1.20 | osmdroid | Apache 2.0 | Mapa clásico de la pantalla de navegación (respaldo) |
| MapLibre Native Android | 10.3.7 | MapLibre | BSD 2-Clause (incluye componentes con sus propias licencias permisivas) | Mapa 3D |
| Conscrypt (conscrypt-android) | 2.8.0 | Google / The Android Open Source Project | Apache 2.0 (incluye BoringSSL: licencias OpenSSL/ISC) | TLS 1.3 en Android 7 |
| Android Gradle Plugin | 9.3.0 | Google | Apache 2.0 | Build |
| JUnit | 4.13.2 | JUnit team | EPL 1.0 | Tests |
| Robolectric | 4.12.2 | Robolectric | MIT | Tests (capturas) |
| Roborazzi | 1.20.0 | takahirom | Apache 2.0 | Tests (capturas) |
| Gradle Wrapper | 9.5.0 | Gradle Inc. | Apache 2.0 | Build (`gradlew`, `gradle/wrapper/`) |

## Servicios y datos

| Servicio | Uso | Condiciones |
|---|---|---|
| [Open-Meteo](https://open-meteo.com) | Clima y pronóstico | API gratuita para uso no comercial; los datos están bajo **CC BY 4.0 y requieren atribución** (en la app: Diagnóstico → Acerca de, desde v0.17.0) |
| [Radio Browser](https://www.radio-browser.info) | Directorio de radios | Licencia de los datos **no verificada** |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | Mosaicos y datos del mapa | Datos **ODbL**, atribución "© colaboradores de OpenStreetMap" (en el mapa y en Acerca de); uso liviano según la política de mosaicos |
| [ntfy](https://ntfy.sh) (v0.76.0) | Recibir destinos compartidos desde el celular | Servidor público de ntfy (Apache 2.0 / GPL-2.0), uso liviano sin cuenta; canal con nombre secreto (GitHub Secret `NTFY_TOPIC`). La app ntfy del celular es del autor y no va en Onix |
| [TomTom](https://developer.tomtom.com) (v0.75.0) | Búsqueda, rutas con tránsito y nombre del lugar | Términos de TomTom para desarrolladores, plan gratuito (2.500 pedidos por día); clave del autor como GitHub Secret; crédito "© TomTom" en Acerca de |
| [OpenFreeMap](https://openfreemap.org) | Mosaicos vectoriales y letras del mapa 3D | Servicio gratuito sin clave; atribución "OpenFreeMap © OpenMapTiles, datos © colaboradores de OpenStreetMap" (en el mapa y en Acerca de). Las letras son Noto Sans (SIL OFL). El estilo oscuro es propio |
| ISRG Root X1 e ISRG Root X2 | `res/raw/isrg_root_x1.pem`, `isrg_root_x2.pem` (raíces de confianza en `network_security_config.xml`) | Certificados raíz públicos de Internet Security Research Group (Let's Encrypt); no se atribuyen al autor |
| [Spotify Web API](https://developer.spotify.com/terms) | Biblioteca y reproducción | Términos para desarrolladores de Spotify; app personal en modo desarrollo; reproducir requiere Premium. Spotify es marca de Spotify AB |
| [World Radio Map](https://worldradiomap.com/ar/san-miguel-de-tucuman) | Fuente de la lista de frecuencias de `TucumanFm.kt` | Datos usados como referencia |
| [radioarg.net](https://radioarg.net/tucuman) | Fuente de los enlaces de audio de radios de Tucumán en `TucumanFm.kt` | Datos usados como referencia; las transmisiones son de cada emisora |
| Transmisiones de cada emisora | Audio en vivo | Pertenecen a cada emisora (Los 40, Mitre/Cienradios, Continental, Radio Nacional, LV7, La 100, etc.) |
| Cienradios (página del reproductor de La 100 Tucumán) | Obtener el enlace local | Pertenece a Cienradios / Grupo Clarín |
| StreamTheWorld (Triton Digital) | Servidores de audio de La 100 y Mitre | Las transmisiones pertenecen a cada emisora |

## Imágenes y marcas

| Recurso | Archivo | Origen | Derechos |
|---|---|---|---|
| Emblema de Chevrolet | `chevrolet_emblem.png` | Imagen aportada por el usuario | Marca registrada de General Motors. Uso personal (franja de Inicio y presentación) |
| Lema "Find New Roads" | Texto de la presentación (`Intro.kt`) | Lema publicitario de Chevrolet, pedido por el usuario | Marca de General Motors; no se atribuye al autor. Uso personal |
| Foto del Onix (base) | `onix_car.png`, `onix_glow.png` | Foto aportada por el usuario a partir de una publicación; patente reemplazada, fondo y reflejos editados | Autor original desconocido (no verificado). Uso personal |
| Valle con ruta (día y atardecer; noche y lluvia derivadas) | `bg_dia.jpg`, `bg_atardecer.jpg`, `bg_noche.jpg`, `bg_lluvia.jpg`, `bg_lluvia_atardecer.jpg`, `bg_lluvia_noche.jpg` | Sacados con `tools/make_bg_mockups.py` de las imágenes de referencia aportadas por el usuario (hechas con otra IA, v0.29.0); noche derivada del atardecer y lluvia del día (v0.29.2); lluvia de atardecer y de noche derivadas con `tools/make_bg_rain.py` (v0.39.0) | Imágenes del usuario; no se atribuyen al autor del código. Uso personal |
| Íconos de la interfaz | `Drawing.kt` | Dibujados en código | Propios |
| Logos de las radios | No están en el repositorio | Imágenes publicadas por World Radio Map para cada emisora del dial, y los íconos de Radio Browser o de la página web de cada emisora; la app los descarga y guarda en la caché del equipo | Pertenecen a cada emisora. Uso personal |

**Versión pública:** las imágenes originales (emblema, foto del auto y fondos) no se incluyen. En su lugar hay placeholders generados con código (degradados, una silueta genérica y una insignia neutra) creados por el autor. Quien quiera la apariencia original debe aportar sus propios recursos con los mismos nombres de archivo en `app/src/main/res/drawable-nodpi/`.

## Referencias sin código copiado

| Proyecto | Qué se tomó | Nota |
|---|---|---|
| [CleanLauncher](https://github.com/jhontandazo008/CleanLauncher) (launcher para la misma radio NWD) | Solo datos de las interfaces de la radio: nombres de avisos (`com.nwd.ACTION_ILL_STATE_CHANGE`, `com.nwd.action.ACTION_MCU_STATE_CHANGE`, `com.android.action.ACTION_BACKCAR_STATE_CHANGE`), el bit de luces de `mcu_no_source_device_state` la existencia de las listas de procesos de `/nwdappconfig/app/` y (v0.51.0) la interfaz del sintonizador FM: servicio `com.nwd.radio.service`, interfaz `RadioFeature`, códigos y formato de sus mensajes, y los ids de fuente de audio (FM = 4, Android = −128) | El repositorio no tiene licencia: no se copió código ni recursos; la implementación es propia |
| [Horizon-Maps](https://github.com/benbishop028-arch/Horizon-Maps) | Solo la idea de paquetes de mapas por región armados en GitHub Actions | Sin licencia: no se copió código |
| [gentle-ai](https://github.com/Gentleman-Programming/gentle-ai) (Gentleman Programming) | Ideas del flujo de trabajo (v0.52.0): archivo de tarea con alcance, pasos, evidencia y siguiente paso; memoria del proyecto entre sesiones; tests primero; commits chicos | Licencia MIT; no se copió texto ni código: las reglas de `CLAUDE.md` están escritas para este proyecto |
| [LiteDash](https://github.com/IrvingGuerra/litedash) (Irving Guerra, launcher para pantallas de auto) | Ideas (v0.50.0 y v0.64.0): salud del auto por sistema y teclas de música para apps de Bluetooth sin sesión (LiteDash 1.4.53–1.4.55); leer y explicar códigos de falla, alertas del motor (recalentado, alternador, batería), consumo estimado, simulador de ELM327 para probar sin el auto. Los formatos OBD2 (SAE J1979 / J2012) y las fórmulas son públicos | Licencia GPL-3.0: no se copió código para no obligar a cambiar la licencia de Onix; la implementación, las descripciones de los códigos y el lector simulado de los tests son propios |


## Voces grabadas (v0.61.0)

| Componente | Uso | Licencia |
|---|---|---|
| [Piper](https://github.com/rhasspy/piper) 1.2.0 (`piper-tts`) | Herramienta que genera las frases en la CI (`voces.yml`); no va en el APK | MIT |
| Voces del slogan de inicio con IA (v0.70.0): [ElevenLabs](https://elevenlabs.io) (voces de su biblioteca pública con acento argentino y la voz George), generadas en la CI con la cuenta del autor (`tools/voces/elevenlabs.py`) | `app/src/main/assets/voz/inicio_ar_*.ogg`, `inicio_asistente.ogg` (si existen); voces usadas en `assets/voz/LEEME_ia.txt` | Términos de ElevenLabs, plan gratuito: uso personal no comercial con atribución (en Acerca de) |
| Voces del slogan de inicio (v0.69.0), de [piper-voices](https://huggingface.co/rhasspy/piper-voices) con efecto de locutor (`tools/voces/inicio.txt`): **en_US-ryan-high** (dataset RyanSpeech, **CC BY-NC-SA 4.0**: no comercial), **en_US-joe-medium** (NabuCasa voice-datasets, **CC0**), **en_GB-alan-medium** (Mycroft mimic3-voices en_UK/apope, licencia según su URL), **en_US-norman-medium** (LibriVox, **dominio público**) y es_AR-daniela-high (ver abajo) | `app/src/main/assets/voz/inicio_*.ogg` (obras derivadas) | Las de cada modelo; fichas completas en `assets/voz/LEEME_inicio.txt` (las guarda la CI). Uso personal, sin fines comerciales |
| Voz **es_AR-daniela-high** de [piper-voices](https://huggingface.co/rhasspy/piper-voices) (entrenada por [larcanio](https://huggingface.co/larcanio/piper-voices)) con el corpus de castellano argentino [OpenSLR 61](https://www.openslr.org/61/) | Las 31 frases de `app/src/main/assets/voz/` (obra derivada) | **CC BY-SA 4.0** (atribución y compartir igual); ficha en `assets/voz/LEEME.txt` |

## Código de terceros incluido (v0.56.0, ADR-0012)

Copiado sin cambios de [CarLink](https://github.com/F-e-n-y-x/CarLink) 1.0.9 (código del 2026-11-04, desde v0.74.0; antes 1.0.7, commit `cfba714`). `vendor/CARLINK_INTEGRATION.md` y `vendor/VENDOR_CHANGES.md` son `docs/INTEGRATION.md` y `docs/VENDOR_CHANGES.md` de CarLink. Por estar incluido, Onix se distribuye bajo AGPL-3.0.

| Componente | Carpeta | Autores | Licencia | Uso |
|---|---|---|---|---|
| CarLink `carlink-core` | `carlink-core/` | F-e-n-y-x | AGPL-3.0 (`vendor/CARLINK_LICENSE`) | Detección del celular, sesiones, registro |
| DiAuto (open-headunit, basado en headunit de Michael Reid) | `vendor/diauto/` | André Knieriem, Michael Reid, shihabal3amri y otros | AGPL-3.0 (`vendor/diauto/LICENSE`, `COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`) | Android Auto. Incluye el **certificado y la clave privada** de receptor de Android Auto (`res/raw/cert`, `res/raw/privkey`; emisor "Google Automotive Link", sujeto "Google-Android-Reference") que usan los proyectos abiertos desde 2014. No es una credencial pública ni autorizada por Google: riesgo aceptado solo para uso personal (AUD-066) |
| DiPlay (basado en xcertplay) | `vendor/diplay/` | shihabal3amri, shilapi | GPL-3.0 (`vendor/diplay/LICENSE`) | CarPlay. La identidad de accesorio (clave privada `identity.pk8` y certificado de un adaptador Carlinkit) **no** está en el repo: la CI la saca del APK de CarLink 1.0.9 (huella verificada; 1.0.7 hasta v0.70.0) o de GitHub Secrets (v0.57.0 / v0.58.0). No es de Apple ni de Onix: cada APK la lleva, por eso los APK no se comparten (AUD-067) |
| Dependencias de los motores | Gradle | Google, Bouncy Castle, JmDNS, ZXing, protobuf, libsu, Conscrypt, libusb, FFmpeg (swscale) y otras | Apache 2.0, MIT, LGPL y otras: ver los avisos de cada motor (`vendor/diplay/README.md`, `vendor/diauto/README.md`) | Motores |

CarPlay es marca de Apple Inc. y Android Auto es marca de Google LLC. Sin relación ni aval.
