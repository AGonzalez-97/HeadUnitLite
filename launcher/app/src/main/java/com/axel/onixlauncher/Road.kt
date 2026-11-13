package com.axel.onixlauncher

/*
 * Desde v0.27.0 el piso y la ciudad son parte del fondo (sacado de las imágenes de referencia
 * del autor con tools/make_bg_mockups.py), así que ya no se dibujan la ruta ni su línea central. Queda el interruptor de
 * animaciones que usan la lluvia y las capturas.
 */

/** Interruptor global de animaciones (las capturas automáticas lo apagan). */
object RenderFlags {
    @JvmField
    var animate = true
}
