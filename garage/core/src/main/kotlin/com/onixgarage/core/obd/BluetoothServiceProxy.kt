package com.onixgarage.core.obd

import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

internal object BluetoothServiceProxy : ObdCommandPort {

    override val connectionState: StateFlow<OBDConnectionState> = BluetoothService.appConnectionState

    override suspend fun sendCommand(cmd: String): String =
        BluetoothService.instance?.sendCommand(cmd)
            ?: throw IOException("BluetoothService not running")
}
