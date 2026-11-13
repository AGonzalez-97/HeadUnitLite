package com.onixgarage.core.data.recorder

import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.dao.FuelLoadDao
import com.onixgarage.core.data.entity.DailyTrip
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.core.obd.OBDSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripRecorder(
    private val tripDao: DailyTripDao,
    private val fuelLoadDao: FuelLoadDao,
    private val connectionState: StateFlow<OBDConnectionState>,
    private val snapshot: StateFlow<OBDSnapshot?>,
    private val scope: CoroutineScope,
) {
    private var currentTripId: Long? = null
    private var lastSnapshotTime: Long = 0L
    private var accumulatedDistanceKm: Double = 0.0
    private var accumulatedFuelLiters: Double = 0.0
    private var maxSpeedKmh: Double = 0.0
    private var speedSamples: MutableList<Double> = mutableListOf()
    private var startCoolantTempC: Int = 0

    fun start() {
        scope.launch { recoverOngoingTrip() }
        scope.launch { observeConnection() }
        scope.launch { observeSnapshot() }
    }

    private suspend fun recoverOngoingTrip() {
        val ongoing = tripDao.getOngoingTrip() ?: return
        val ageMs = System.currentTimeMillis() - ongoing.startTimestampMs
        if (ageMs > 5 * 60 * 1000L) {
            // Stale trip — finalize it with whatever was persisted.
            tripDao.update(
                ongoing.copy(
                    endTimestampMs = ongoing.startTimestampMs + ageMs,
                    durationMs = ageMs,
                )
            )
        } else {
            // Recent trip — resume accumulators from persisted row.
            currentTripId = ongoing.id
            accumulatedDistanceKm = ongoing.distanceKm
            accumulatedFuelLiters = ongoing.fuelConsumedLiters
            maxSpeedKmh = ongoing.maxSpeedKmh
            startCoolantTempC = ongoing.startCoolantTempC
        }
    }

    private suspend fun observeConnection() {
        var wasConnected = false
        connectionState.collect { state ->
            when {
                state is OBDConnectionState.Connected && !wasConnected -> {
                    wasConnected = true
                    startTrip()
                }
                state !is OBDConnectionState.Connected && wasConnected -> {
                    wasConnected = false
                    finalizeTrip()
                }
            }
        }
    }

    private suspend fun observeSnapshot() {
        var lastTime = 0L
        snapshot.collect { snap ->
            val now = System.currentTimeMillis()
            if (snap != null && currentTripId != null && lastTime > 0) {
                val elapsedHours = (now - lastTime) / 3_600_000.0
                accumulatedDistanceKm += snap.speedKmh * elapsedHours
                accumulatedFuelLiters += (snap.fuelConsumptionLPH ?: 0.0) * elapsedHours
                if (snap.speedKmh > maxSpeedKmh) {
                    maxSpeedKmh = snap.speedKmh.toDouble()
                }
                speedSamples.add(snap.speedKmh.toDouble())
            }
            if (snap != null) lastTime = now
        }
    }

    private suspend fun startTrip() {
        resetAccumulators()
        val snap = snapshot.value
        startCoolantTempC = snap?.coolantTempC ?: 0
        val tripId = tripDao.insert(
            DailyTrip(
                startTimestampMs = System.currentTimeMillis(),
                startCoolantTempC = startCoolantTempC,
            )
        )
        currentTripId = tripId
        lastSnapshotTime = System.currentTimeMillis()
    }

    private suspend fun finalizeTrip() {
        val tripId = currentTripId ?: return
        val endTime = System.currentTimeMillis()
        val lastPrice = fuelLoadDao.getLastFullFill()?.pricePerLiter ?: 0.0
        val avgSpeed = if (speedSamples.isNotEmpty()) speedSamples.average() else 0.0

        val trip = tripDao.getById(tripId) ?: return
        tripDao.update(
            trip.copy(
                endTimestampMs = endTime,
                durationMs = endTime - trip.startTimestampMs,
                distanceKm = accumulatedDistanceKm,
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = maxSpeedKmh,
                endCoolantTempC = snapshot.value?.coolantTempC,
                fuelConsumedLiters = accumulatedFuelLiters,
                costARS = accumulatedFuelLiters * lastPrice,
            )
        )
        currentTripId = null
        resetAccumulators()
    }

    private fun resetAccumulators() {
        accumulatedDistanceKm = 0.0
        accumulatedFuelLiters = 0.0
        maxSpeedKmh = 0.0
        speedSamples = mutableListOf()
        startCoolantTempC = 0
        lastSnapshotTime = 0L
    }
}
