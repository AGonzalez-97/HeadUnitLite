package com.shilapi.xcertplay.transport

/**
 * Onix (vendor/ONIX_CHANGES.md, 2026-11-05): USB bulk OUT transfers end with a short packet. A
 * transfer whose length is a whole number of max-size packets needs a zero-length packet after it,
 * or the device keeps waiting for the rest (usbmuxd sends one in the same case).
 */
object UsbZeroLengthPacket {
    fun needed(size: Int, maxPacketSize: Int): Boolean =
        maxPacketSize > 0 && size > 0 && size % maxPacketSize == 0
}
