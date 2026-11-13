# Tarea: saber si el aire acondicionado está prendido

Pedido del autor (2026-11-04). Auto: Chevrolet Onix Joy 2018, modelo base, aire manual.

## Qué se sabe
- La radio NWD es de reemplazo: no está conectada a la red del auto (CAN) para el aire. No hay señal del aire en la radio.
- El OBD2 estándar (modo 01) **no tiene** un dato de "aire prendido".
- Caminos posibles:
  1. **Indirecto por el OBD2 (estándar):** con el compresor enganchado, en ralentí suben las vueltas que pide la computadora y la carga del motor. Se puede estimar, pero no es seguro (también suben con las luces, la dirección o el ventilador).
  2. **Datos propios de GM (modo 22):** algunas computadoras de GM informan el embrague del compresor o el pedido de aire. No se sabe si la del Onix Joy lo hace ni con qué código: hay que probarlo con el lector.
  3. **Un cable del compresor a la radio:** fuera del alcance (instalación eléctrica).

## Pasos
- [ ] Prueba completa con el aire apagado y otra con el aire prendido, en ralentí (guía en `docs/PRUEBA_COMPLETA.md`).
- [ ] Comparar vueltas, carga y respuestas crudas del OBD2.
- [ ] Si hace falta, sumar al informe una exploración de solo lectura de datos de GM (modo 22) y repetir.
- [ ] Si hay una señal confiable: mostrar el aire en Inicio y en el consumo.

## Evidencia
Ninguna todavía.

## Siguiente paso
Las dos Pruebas completas del autor (aire apagado y prendido).
