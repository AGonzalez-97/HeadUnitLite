# Tarea: sonido de inicio y temas festivos

Pedido del autor (2026-11-04): que al iniciar el launcher suene algo y se diga el slogan "Find new roads"; y temas festivos para el auto (Navidad, Halloween, fechas patrias de Argentina…).

## Alcance
- **v0.65.0 (las dos cosas juntas) — Sonido de inicio:** con la presentación, un sonido propio (tres notas tipo campana, compuesto en código: no se copia el sonido de ningún auto, que es de su marca) y la voz grabada diciendo "Find new roads" (con la voz argentina, escrito para que se pronuncie en inglés). Opciones: voz y sonido / solo sonido / apagado. No suena con Android Auto o CarPlay conectados.
- **Temas festivos:** en Inicio, decoración y saludo según la fecha (Argentina): Navidad, Año Nuevo, Reyes, San Valentín, Carnaval, Pascua, Malvinas, 25 de Mayo, Día del Padre, Día de la Bandera, Independencia, Día del Amigo, San Martín, Primavera, Día de la Madre, Halloween, Soberanía. Con el modo liviano, la decoración queda quieta. Se puede apagar y se puede ver cada tema para probarlo.
- Los dos ajustes van en la ventana del saludo (tocando "Hola, …" en Inicio).

## Pasos
- [x] Plan.
- [x] `IntroSound.kt`: `IntroChime` (pura, test) y reproducción; frase `inicio`.
- [x] `Festive.kt`: `Festive.on(fecha)` (pura, tests con Pascua y domingos variables), `FestiveLayer`, saludo festivo, frases `fiesta_*`.
- [x] Ajustes en la ventana del saludo.
- [x] Docs, CI (build 103: 167/167, capturas 17 y 18 OK), panel.
- [ ] Prueba en el auto (R-58, R-59).

- [x] v0.66.0 (pedido del autor): las fiestas decoran el auto (`CarDecor`) y no saludan; se sacaron el saludo y las frases `fiesta_*`.

- [x] v0.67.0 (pedido del autor): adornos realistas y animados. Contornos medidos sobre la foto (fracciones de la caja del auto): techo (0,26; 0,195)→(0,785; 0,015); capó (0,04; 0,375)→(0,40; 0,375)→(0,43; 0,42)→(0,745; 0,375); ventanilla (0,80; 0,255)→(0,975; 0,30).

## Evidencia
Build 103: 167/167 tests; capturas de Navidad y fecha patria sin errores. Build 107 (v0.67.0): 168/168, capturas con los adornos animados sin errores. Falta el auto.

## Siguiente paso
Prueba en el auto (R-58, R-59), fila 9 y 10 de `docs/INSTRUCTIVO.md`.
