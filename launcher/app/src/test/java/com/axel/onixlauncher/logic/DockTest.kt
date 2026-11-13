package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Builtin
import com.axel.onixlauncher.DockSlot
import com.axel.onixlauncher.DockStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dock personalizable: guardar y leer los lugares. */
class DockTest {

    @Test
    fun sin_nada_guardado_quedan_los_accesos_por_defecto() {
        assertEquals(DockStore.defaults, DockStore.parse(null))
        assertEquals(7, DockStore.parse("").size)
    }

    @Test
    fun ida_y_vuelta_de_cada_tipo_de_lugar() {
        val slots = listOf(
            DockSlot.Own(Builtin.RADIO),
            DockSlot.App("com.waze", "com.waze.FreeMapAppActivity", "Waze"),
            DockSlot.Empty
        )
        val raw = slots.joinToString(";") { it.encode() }
        val parsed = DockStore.parse(raw)
        assertEquals(slots, parsed.take(3))
        assertEquals(DockStore.defaults.drop(3), parsed.drop(3))
    }

    @Test
    fun valores_rotos_quedan_vacios() {
        assertEquals(DockSlot.Empty, DockSlot.decode("own:NADA"))
        assertEquals(DockSlot.Empty, DockSlot.decode("app:solo"))
        assertEquals(DockSlot.Empty, DockSlot.decode("cualquier cosa"))
    }
}
