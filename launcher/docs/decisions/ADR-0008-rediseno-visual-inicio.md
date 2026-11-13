# ADR-0008 — Rediseño visual de Inicio sobre la misma estructura

**Estado:** Aceptada · **Fecha:** 2026-10-27 · **Versión:** v0.25.0 · **Autor:** Axel Hernán González

## Contexto

El autor pidió un aspecto más premium, tipo equipo de fábrica, con una imagen de referencia. La distribución de esa imagen coincide con la de Inicio (reloj y clima arriba, música y navegación a la izquierda, auto al centro, motor a la derecha, dock abajo). Un pedido anterior de rediseño se había descartado porque el autor prefería lo ya hecho.

## Decisión

- Mantener la estructura, los paneles movibles y todo el código de datos; cambiar solo la presentación.
- Centralizar el estilo en `Ui.kt` (`card()` de vidrio grafito y paleta) y usarlo en todos los paneles.
- No agregar controles que el equipo no puede cumplir: la imagen de referencia tenía "aleatorio" y "me gusta", que `MediaController` no ofrece en API 24, y "Apps", que no tiene destino en un launcher sin cajón de apps.
- No mostrar datos inventados: sin OBD2, temperatura, combustible, batería y RPM quedan en "--"; las luces se rotulan "automático por hora".

## Consecuencias

- Cambio acotado (sin dependencias ni arquitectura nueva); las capturas de la CI son la referencia visual.
- Un panel cambió de forma y de identificador (`pronostico`), por eso vuelve a su lugar por defecto.

## Actualización v0.26.0

El autor pidió replicar la imagen "tal cual". Se cambia la decisión en tres puntos:

- Aleatorio y "me gusta" se muestran: usan las acciones propias que publica la app que suena (`PlaybackState.CustomAction`, disponible en API 21) y se ven apagados si no las ofrece.
- Apps se implementa como un cajón de aplicaciones real (`AppsOverlay`).
- El reloj pasa a 12 h con AM/PM, como la imagen.

Se mantiene: el fondo, el auto y la ruta propios (dependen de la hora, el clima y las luces); ningún dato inventado.

## Actualización v0.27.0

El autor pidió también el fondo de la imagen y un auto más chico. El fondo se reconstruye con
`tools/make_bg_reference.py` (cielo, cerros y ciudad de las zonas libres de la imagen; piso
generado) y se derivan las versiones de día, noche y lluvia para conservar el ambiente dinámico.
Se quita la ruta animada, que no está en la imagen.

## Actualización v0.28.0

Espejar recortes de la imagen dejaba cerros repetidos y un día poco creíble. Los fondos se
generan ahora con `tools/make_bg_scene.py` (misma composición y colores; solo las nubes del
atardecer salen de la imagen), con un cielo propio para día, noche y lluvia.

## Actualización v0.29.0

El autor mandó tres imágenes nuevas (día, atardecer y noche) hechas con otra IA sobre la app y
pidió replicarlas sin inventar. Los fondos se sacan de esas imágenes (`tools/make_bg_mockups.py`):
como el auto de las imágenes es `onix_car.png`, se lo ubica por coincidencia de plantilla y el auto
de la app se dibuja en el mismo lugar, tapando lo que se rellenó. El panel de motor copia los
relojes redondos y las barras de las imágenes; los haces de los faros se dibujan en código.

## Actualización v0.29.2

La imagen de noche tenía otra ruta y otro encuadre que las de día y atardecer, y el autor pidió la
misma ruta en todos los climas. La noche se deriva del atardecer (misma escena) en lugar de usar
su propia imagen.
