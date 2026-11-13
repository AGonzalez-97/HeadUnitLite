package com.onixgarage.headunit.ui

import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.core.obd.OBDSnapshot

data class HuUiState(
    val connectionState: OBDConnectionState = OBDConnectionState.Disconnected,
    val snapshot: OBDSnapshot? = null,
)
