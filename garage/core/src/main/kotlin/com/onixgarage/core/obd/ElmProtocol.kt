package com.onixgarage.core.obd

import java.util.UUID

internal object ElmProtocol {

    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val CMD_DEFAULTS = "ATD\r"
    const val CMD_RESET = "ATZ\r"
    const val CMD_ECHO_OFF = "ATE0\r"
    const val CMD_LINEFEED_OFF = "ATL0\r"
    const val CMD_SPACES_OFF = "ATS0\r"
    const val CMD_HEADERS_OFF = "ATH0\r"
    const val CMD_PROTOCOL_AUTO = "ATSP0\r"
    const val CMD_PROBE = "0100\r"

    const val PROMPT_CHAR = '>'

    const val PID_COOLANT_TEMP = "0105\r"
    const val PID_RPM          = "010C\r"
    const val PID_SPEED        = "010D\r"
    const val PID_FUEL_LEVEL   = "012F\r"
    const val PID_MAF          = "0110\r"
    const val PID_THROTTLE     = "0111\r"
    const val PID_ENGINE_LOAD  = "0104\r"
    const val PID_DISTANCE_DTC = "0131\r"
    const val PID_DTC_CODES    = "0103\r"
    const val PID_CLEAR_DTC    = "04\r"

    data class InitStep(val command: String, val delayMs: Long)

    val INIT_SEQUENCE = listOf(
        InitStep(CMD_DEFAULTS, 200),
        InitStep(CMD_RESET, 1000),
        InitStep(CMD_ECHO_OFF, 200),
        InitStep(CMD_LINEFEED_OFF, 200),
        InitStep(CMD_SPACES_OFF, 200),
        InitStep(CMD_HEADERS_OFF, 200),
        InitStep(CMD_PROTOCOL_AUTO, 200),
        InitStep(CMD_PROBE, 1000),
    )

    // Commands that don't return "OK" — skip strict validation for these
    val COMMANDS_WITHOUT_OK = setOf(CMD_RESET, CMD_PROBE)
}
