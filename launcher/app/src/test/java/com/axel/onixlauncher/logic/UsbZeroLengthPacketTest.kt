package com.axel.onixlauncher.logic

import com.shilapi.xcertplay.transport.UsbZeroLengthPacket
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.79.3: un envío USB que termina justo en el borde de un paquete necesita un paquete vacío
 * después; sin él el iPhone espera más datos y no contesta el pedido de "Confiar" (Prueba del
 * 05/11 20:01: el pedido Pair de Onix medía 4572 + 36 = 4608 bytes = 9 × 512).
 */
class UsbZeroLengthPacketTest {
    @Test
    fun paquete_vacio_solo_cuando_el_envio_termina_en_el_borde() {
        assertEquals(true, UsbZeroLengthPacket.needed(4572 + 36, 512))   // el pedido Pair del iPhone del autor
        assertEquals(true, UsbZeroLengthPacket.needed(512, 512))
        assertEquals(false, UsbZeroLengthPacket.needed(4610, 512))      // con otro nombre de equipo (DiPlay sola)
        assertEquals(false, UsbZeroLengthPacket.needed(36, 512))
        assertEquals(false, UsbZeroLengthPacket.needed(0, 512))
        assertEquals(false, UsbZeroLengthPacket.needed(1024, 0))        // tamaño de paquete desconocido
    }
}
