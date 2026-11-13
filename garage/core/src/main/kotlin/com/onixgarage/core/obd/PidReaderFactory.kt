package com.onixgarage.core.obd

object PidReaderFactory {
    @Volatile private var instance: PidReader? = null

    fun get(): PidReader =
        instance ?: synchronized(this) {
            instance ?: PidReader(BluetoothServiceProxy).also { instance = it }
        }
}
