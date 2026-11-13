package com.axel.onixlauncher.logic

import com.axel.onixlauncher.BtDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.81.24: the full report lists the Bluetooth devices paired with the radio (name, kind, connected). */
class BtDevicesTest {
    @Test
    fun cada_equipo_se_describe_con_nombre_tipo_y_estado() {
        assertEquals("OBDII (otro, conectado, …EE:FF)", BtDevices.describe("OBDII", "AA:BB:CC:DD:EE:FF", true, 0x1F00))
        assertEquals("iPhone (teléfono, sin conexión, …11:22)", BtDevices.describe("iPhone", "00:00:00:00:11:22", false, 0x0200))
        assertEquals("sin nombre (otro, estado desconocido, …33:44)", BtDevices.describe(" ", "99:99:99:99:33:44", null, null))
    }

    @Test
    fun la_direccion_completa_no_va_al_informe() {
        val line = BtDevices.describe("OBDII", "AA:BB:CC:DD:EE:FF", true, null)
        assertFalse(line.contains("AA:BB:CC"))
        assertTrue(line.contains("EE:FF"))
        assertEquals("…?", BtDevices.mask(null))
    }

    @Test
    fun la_lista_dice_cuantos_hay_y_cuales_estan_conectados() {
        assertEquals("ninguno", BtDevices.summary(emptyList()))
        assertEquals("2: OBDII (otro, conectado, …EE:FF); iPhone (teléfono, sin conexión, …11:22)",
            BtDevices.summary(listOf("OBDII (otro, conectado, …EE:FF)", "iPhone (teléfono, sin conexión, …11:22)")))
    }
}
