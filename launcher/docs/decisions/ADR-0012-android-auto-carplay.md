# ADR-0012 — Android Auto y CarPlay dentro de Onix con `carlink-core` (AGPL-3.0)

**Estado:** Aceptada · **Fecha:** 2026-11-03 · **Versión:** desde v0.54.0 (por etapas) · **Autor:** Axel Hernán González

## Contexto

El autor quiere Android Auto y CarPlay en la radio sin apps aparte. Probó CarLink (F-e-n-y-x/CarLink)
por USB en el auto y funciona. No hay app de proyección de fábrica en la radio. Android Auto y
CarPlay son protocolos cerrados; los proyectos abiertos que los resuelven son AGPL/GPL:
CarLink (AGPL-3.0, junta DiPlay para CarPlay y DiAuto para Android Auto), DiPlay (GPL-3.0, solo
CarPlay) y open-headunit (AGPL-3.0, solo Android Auto). CarLink separa su motor en la librería
`carlink-core`, pensada para que otro launcher de la misma radio la use.

## Decisión (del autor, 2026-11-03)

- **Integrar `carlink-core` y sus motores (`vendor/diplay`, `vendor/diauto`) dentro de Onix.**
- **Onix pasa a licencia AGPL-3.0** (el autor conserva el derecho de autor de su código). Uso
  personal; el repositorio sigue privado. Si alguna vez se entrega la app a otra persona, hay que
  ofrecerle el código fuente completo.
- **CarPlay:** necesita dos archivos de identidad (`offline-mfi/identity.pk8` y
  `offline-mfi/certificate.p7b`) que no son de Apple: salen del firmware de un adaptador Carlinkit
  y los distribuye DiPlay en su APK. **No se guardan en el repositorio** (regla de seguridad): van
  como GitHub Secrets, igual que la clave de firma. Riesgo aceptado: una actualización de iOS puede
  dejar de aceptarlos; no se publica una APK con ellos.
- **Android Auto** no necesita archivos extra.
- **Actualización v0.58.0 (pedido del autor: "hacelo vos"):** si no hay secretos, la CI baja el APK
  público de CarLink 1.0.7, verifica la huella SHA-256 publicada por CarLink y saca de ahí la
  identidad. Sigue sin estar en el repositorio; sigue siendo para uso personal.
- **Actualización v0.70.1 (AUD-068):** CarLink retiró el APK 1.0.7; la CI usa el de CarLink 1.0.9
  (huella publicada) y, si también se retira, la última release con la huella de su `.sha256`.
- **Actualización v0.74.0:** el código de los motores pasa a CarLink 1.0.9 (DiPlay 0.2.22), copiado sin
  cambios (`vendor/ONIX_CHANGES.md`). La versión del APK de la identidad vive en
  `tools/carlink.properties` y la actualiza sola, todos los días, el workflow `carlink.yml`; si sale
  código nuevo de los motores, ese workflow abre un aviso para actualizarlos.

## Etapas

1. **v0.54.0:** botón "Auto/CarPlay" en el dock que abre CarLink instalada (puente mientras tanto).
2. **v0.55.0:** actualizar las herramientas de compilación de Onix a las que pide CarLink (Gradle 9,
   AGP 9, Kotlin 2.2, compileSdk 37, Compose actual) sin cambios visibles; minSdk 24 → 25
   (Android 7.1: el equipo objetivo). Verificar con las capturas de la CI que nada cambió.
3. **v0.56.0:** `carlink-core` + Android Auto dentro de Onix (pantalla propia, USB), licencia AGPL.
4. **v0.57.0:** CarPlay con la identidad desde GitHub Secrets.

## Consecuencias

- Cambia la licencia del proyecto (`LICENSE`, `README.md`, `CLAUDE.md`, `About.kt`).
- El APK crece mucho (código nativo de los dos motores, ~40 MB de fuentes) y la compilación tarda más
  (CMake/NDK).
- Las actualizaciones de CarLink se traen a mano (copia de `carlink-core` y `vendor/` con su historia
  de cambios anotada).
- Alternativa descartada por el autor: dejar CarLink como app aparte (Onix seguía con todos los
  derechos reservados).

## Aclaración (2026-11-04, AUD-066 y AUD-067)

- **Android Auto:** lo que trae DiAuto en `vendor/diauto/app/src/main/res/raw/` no es solo un
  certificado público: son el certificado **y la clave privada** (`privkey`) de un receptor de
  referencia emitidos por "Google Automotive Link". Los usan los proyectos abiertos desde 2014, pero
  Google no los autorizó para este uso.
- **CarPlay:** `identity.pk8` es la clave privada del accesorio de un adaptador Carlinkit, sacada de
  su firmware por terceros. La CI la mete en **cada** APK.
- **Consecuencia:** ningún APK ni release de Onix se comparte ni se publica (las releases son del
  repositorio privado). Las notas "para el público" (regla de oro 3) son un estilo de redacción, no
  permiso para difundir. Antes de compartir la app con cualquier persona habría que quitar ambas
  credenciales y entregar el código completo (AGPL-3.0).

