# Tarea: lugares cerca, paradas en el camino y lugares escritos (v0.77.0)

Pedido del autor (2026-11-05): buscar combustible y otros lugares cerca; "no tocar para guardar, sino escribir la dirección y que la guarde" (Casa, Trabajo, favoritos); aviso de nafta baja con el OBD2. Idea parecida a Horizon Nav (sin licencia: no se copia código).

## Alcance
- Botones de categoría (TomTom nearbySearch): Combustible, Comida, Café, Estacionar, Farmacia, Cajero, Taller.
- Navegando: "en el camino" (TomTom searchAlongRoute) y agregar como parada (ruta con punto intermedio).
- Mis lugares: escribir Casa, Trabajo y favoritos; se buscan con TomTom y se guardan.
- Nafta baja (OBD2 ≤ 12 %, andando): "¿Buscar estación en el camino?", con voz.

## Pasos
- [x] Tests de lo puro (URL, lectura de respuestas, maniobra de parada, cuándo avisar la nafta).
- [x] `NearbyPlaces.kt` y cambios en `MapNav.kt`, `SafetyAlerts.kt`, `LauncherApp.kt`, `HomeScreen.kt`.
- [x] Frase grabada `nafta_baja`.
- [x] CI: builds 135 a 138 OK (194/194 en el 138).
- [ ] En el auto (R-73 a R-76).

## Evidencia
Build 138: 194/194; capturas del mapa con las categorías y de Diagnóstico con Avisos.

## Siguiente paso
CI y prueba en el auto.
