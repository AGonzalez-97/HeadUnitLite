package com.onixgarage.core.obd

import kotlinx.coroutines.flow.StateFlow

interface ObdCommandPort {
    val connectionState: StateFlow<OBDConnectionState>
    suspend fun sendCommand(cmd: String): String
}
