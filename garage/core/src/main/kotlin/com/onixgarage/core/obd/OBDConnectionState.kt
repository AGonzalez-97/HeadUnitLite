package com.onixgarage.core.obd

sealed class OBDConnectionState {
    object Disconnected : OBDConnectionState()
    object Connecting : OBDConnectionState()
    object Initializing : OBDConnectionState()
    object Connected : OBDConnectionState()
    data class Error(val message: String, val recoverable: Boolean = true) : OBDConnectionState()
}
