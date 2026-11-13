# ADR-0018 - El OBD2 y los viajes viven con el proceso, no con la pantalla

**Estado:** Aceptada · **Fecha:** 2026-11-11 · **Versión:** v0.81.12 · **Autor:** Axel Hernán González

## Contexto

- La auditoría del 2026-11-05 (AUD-082) dejó anotado que el bucle del lector OBD2 (`rememberObd`) y el registro de viajes (`CarCare.tick`) vivían en la composición de la Activity. Si Android destruía la pantalla mientras el proceso seguía (por ejemplo, por `RadioService`), el lector se desconectaba y Mi auto dejaba de contar kilómetros.
- En la radio NWD, volver a conectar con el lector es lo más frágil de toda la app: puede tardar varios intentos y hasta un reinicio del Bluetooth. Cada corte evitable cuesta.
- Onix es el launcher: su proceso es el que Android mantiene vivo con más prioridad.

## Decisión

1. `CarEngine` (en `Obd.kt`) es dueño del bucle del OBD2, del receptor de avisos (contacto, pantalla, Bluetooth) y del reloj de los viajes. Usa un `CoroutineScope` propio que dura lo que dura el proceso.
2. La pantalla solo pide el arranque (`CarEngine.startObd` cuando hay permiso de Bluetooth, `CarEngine.startTrips`) y lee `ObdState`. `StartOnce` garantiza un solo arranque aunque la pantalla se vuelva a crear.
3. El arranque se pide desde la pantalla y no desde `OnixApp`: la app tiene un segundo proceso (`:aa`, el motor de Android Auto) que no debe abrir el lector.
4. **No** se usa un servicio en primer plano de Android. Pediría un permiso nuevo y una notificación fija, y no agrega nada mientras Onix sea el launcher. Queda como paso siguiente si alguna vez se mide que Android mata el proceso con el auto en marcha.

## Consecuencias

- Si la Activity se recrea, el lector sigue conectado y el viaje sigue contando.
- El receptor de avisos ya no se da de baja: vive con el proceso.
- La lógica del bucle no cambió: son las mismas líneas, movidas. Las reglas puras de `ObdRecovery` y sus tests siguen igual.
- Sin prueba en el auto todavía: hay que confirmar que el OBD2 conecta igual que antes al dar contacto.
