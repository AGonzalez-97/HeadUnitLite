package com.onixgarage.core.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class PidReader(
    private val port: ObdCommandPort,
    private val pollIntervalMs: Long = 2_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow<OBDSnapshot?>(null)
    val snapshot: StateFlow<OBDSnapshot?> = _snapshot.asStateFlow()

    val coolantTempC: StateFlow<Int?> = snapshot
        .map { it?.coolantTempC }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _dtcCodes = MutableStateFlow<List<String>>(emptyList())
    val dtcCodes: StateFlow<List<String>> = _dtcCodes.asStateFlow()

    init { scope.launch { pollLoop() } }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            if (port.connectionState.value is OBDConnectionState.Connected) {
                try { _snapshot.value = buildSnapshot() }
                catch (e: CancellationException) { throw e }
                catch (e: IOException) { _snapshot.value = null }
            } else {
                _snapshot.value = null
            }
            delay(pollIntervalMs)
        }
    }

    private suspend fun buildSnapshot(): OBDSnapshot {
        suspend fun <T> safePoll(cmd: String, parse: (String) -> Result<T>): T? =
            try { parse(port.sendCommand(cmd)).getOrNull() }
            catch (e: Exception) { if (e is CancellationException) throw e; null }

        val coolant  = safePoll(ElmProtocol.PID_COOLANT_TEMP, PidParser::parseCoolantTempC) ?: 0
        val rpm      = safePoll(ElmProtocol.PID_RPM,          PidParser::parseRpm)          ?: 0f
        val speed    = safePoll(ElmProtocol.PID_SPEED,        PidParser::parseSpeedKmh)     ?: 0
        val fuel     = safePoll(ElmProtocol.PID_FUEL_LEVEL,   PidParser::parseFuelLevelPct) ?: 0f
        val maf      = safePoll(ElmProtocol.PID_MAF,          PidParser::parseMafGs)
        val throttle = safePoll(ElmProtocol.PID_THROTTLE,     PidParser::parseThrottlePct)  ?: 0f
        val load     = safePoll(ElmProtocol.PID_ENGINE_LOAD,  PidParser::parseEngineLoadPct) ?: 0f
        val dist     = safePoll(ElmProtocol.PID_DISTANCE_DTC, PidParser::parseDistanceSinceDtcClearKm) ?: 0

        return OBDSnapshot(
            coolantTempC            = coolant,
            rpm                     = rpm,
            speedKmh                = speed,
            fuelLevelPct            = fuel,
            mafGs                   = maf,
            throttlePct             = throttle,
            engineLoadPct           = load,
            distanceSinceDtcClearKm = dist,
        )
    }

    suspend fun refreshDtcCodes() {
        if (port.connectionState.value !is OBDConnectionState.Connected) return
        runCatching {
            val response = port.sendCommand(ElmProtocol.PID_DTC_CODES)
            PidParser.parseDtcCodes(response).getOrNull()?.let { _dtcCodes.value = it }
        }
    }

    suspend fun clearDtcCodes() {
        if (port.connectionState.value !is OBDConnectionState.Connected) return
        port.sendCommand(ElmProtocol.PID_CLEAR_DTC)
        _dtcCodes.value = emptyList()
    }

    fun cancel() = scope.cancel()
}
