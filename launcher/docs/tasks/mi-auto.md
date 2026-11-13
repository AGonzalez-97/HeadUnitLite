# Tarea: Mi auto (v0.52.0)

## Alcance
Pantalla "Mi auto" desde el dock: viajes anotados solos (OBD2 o GPS), kilometraje ajustable al del tablero y recordatorios de mantenimiento (aceite y service por fecha y km, VTV, seguro, patente, otro) con aviso en Inicio una vez por día. Todo dentro del launcher.

## Pasos
- [x] `CarCare.kt`: `TripTracker`, `Reminders` (puros), guardado y pantalla
- [x] Litros por hora en `ObdData` (para los litros del viaje)
- [x] Acceso "Mi auto" en el dock (reemplaza a Diagnóstico, que queda en la franja de Inicio)
- [x] Lectura cada 1 s desde `LauncherApp`; aviso de recordatorios en `SafetyOverlay`
- [x] Tests: `CarCareTest` (viajes, recordatorios) y captura de la pantalla
- [x] Documentación de la versión

## Evidencia
- CI: build 76 y 77 OK.
- Auto: pendiente (manejar, ver el viaje; cargar km; agregar el cambio de aceite).

## Siguiente paso
Compilar en la CI y probar en el auto (R-44).
