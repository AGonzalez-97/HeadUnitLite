# Tarea: actualizar los motores de Android Auto y CarPlay

Pedido del autor (2026-11-04): "hacelo". Motivo: Android Auto por cable con la radio en el hotspot del celular; tras un corte de internet, TuneIn dentro de Android Auto no volvió hasta desenchufar y volver a enchufar el USB.

## Alcance
- Reemplazar el código de CarLink 1.0.7 por el de **CarLink 1.0.9** (zip que aportó el autor): `carlink-core/src/`, `vendor/diauto/{app,contract}`, `vendor/diplay/{shared,common}`, sin cambios (regla: no se toca código de terceros).
- Que la identidad de CarPlay y el aviso de motores nuevos se mantengan solos (`carlink.yml`, `tools/carlink.properties`).

## Pasos
- [x] Comparar: `carlink-core` 36 archivos distintos, DiAuto 18, DiPlay `shared` 64 y `common` 46; la API que usa Onix (`CarLinkCore`, `CarLinkHost`, `Connection`, `LinkState`) no cambió; `LinkProblem` suma `HEAD_UNIT_SERVER_OFF` (Onix lo cubre con `else`).
- [x] Copiar el código de 1.0.9 y anotar en `vendor/ONIX_CHANGES.md`.
- [x] `conscrypt-android` de la app a 2.8.0 (la de DiAuto).
- [x] Workflow diario `carlink.yml`.
- [x] CI: compila con el Kotlin y AGP de Onix (build 130: 182/182).
- [ ] En el auto: Android Auto por cable, cortar y volver el hotspot, ver si TuneIn vuelve solo (R-70); CarPlay (R-62).

## Evidencia
Build 130 OK (182/182, APK 24,4 MB). Falta la prueba en el auto.

## Siguiente paso
Prueba del autor en el auto (R-62, R-70).
