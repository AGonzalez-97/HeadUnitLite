package com.axel.onixlauncher.logic

import com.axel.onixlauncher.ObdRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.79.10: the radio's ignition notice carries `extra_mcu_state`; `getIntExtra` returns -1 when the
 * radio sends it as a byte or a string, so the ignition handlers never ran. The value is read by its
 * content, whatever its type.
 */
class McuStateTest {

    @Test
    fun an_int_is_read_as_is() = assertEquals(1, ObdRecovery.mcuStateOf(1))

    @Test
    fun a_byte_is_read() = assertEquals(3, ObdRecovery.mcuStateOf(3.toByte()))

    @Test
    fun a_long_is_read() = assertEquals(1, ObdRecovery.mcuStateOf(1L))

    @Test
    fun a_string_is_read() = assertEquals(1, ObdRecovery.mcuStateOf(" 1 "))

    @Test
    fun nothing_or_junk_is_null() {
        assertNull(ObdRecovery.mcuStateOf(null))
        assertNull(ObdRecovery.mcuStateOf("abc"))
        assertNull(ObdRecovery.mcuStateOf(true))
    }
}
