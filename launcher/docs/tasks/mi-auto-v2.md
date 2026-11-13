# Tarea: Mi auto nuevo (v0.79.0)

Pedido del autor (2026-11-05), debatido antes de programar.

## Alcance
- Pestañas **Mantenimiento · Viajes · Motor**.
- Mantenimiento: cada ítem con "lo hice a los ___ km", "cada ___ km" y/o "cada ___ meses"; "Faltan X km" con colores; Hecho pide el km. Ítems: aceite y filtro, filtro de aire, filtro de habitáculo, filtro de combustible, alineación y balanceo (con rotación) cada 10.000 km; neumáticos 60.000 km; batería y VTV por fecha; pastillas, líquido de frenos, bujías y correa sin intervalo (lo completa el autor); otros a gusto. Sin seguro, patente ni plata.
- Viajes: terminan al apagar el auto (aviso de contacto de la radio NWD) o a los 5 min parado; litros por el consumo del OBD2 o por el nivel del tanque (54 L); resumen por mes; cartel al terminar.
- Motor: datos en vivo, luz del motor, códigos con su significado, salud por sistema y borrar códigos con el auto encendido, también andando (como Torque).
- El aviso de velocidad pasa a Diagnóstico → Avisos.

## Pasos
- [x] Tests de lo puro (mantenimiento, viajes con nivel de tanque, resumen por mes).
- [x] `CarCare.kt` nuevo; `EnginePanel.kt` (borrar andando); `Diagnostics.kt` (velocidad en Avisos); `SafetyAlerts.kt` (avisos de mantenimiento y de fin de viaje); `LauncherApp.kt`.
- [x] CI: builds 139 y 140 OK (196/196); capturas de Mi auto y Diagnóstico revisadas.
- [ ] En el auto (R-79 a R-82).

## Evidencia
Build 140: 196/196. Captura de Mi auto → Mantenimiento con colores y textos ("Faltan 600 km", "Faltan 15 días").

## Siguiente paso
CI y prueba en el auto.
