package com.onixgarage.phone.ui.dashboard

import com.onixgarage.core.data.entity.DailyTrip
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.core.obd.OBDSnapshot

private const val TANK_CAPACITY_L = 50f

data class DashboardUiState(
    val connection: OBDConnectionState = OBDConnectionState.Disconnected,
    val snapshot: OBDSnapshot? = null,
    val ongoingTrip: DailyTrip? = null,
    val lastFuelPricePerLiter: Double? = null,
    val estimatedCostPerHour: Double? = null,
)

val DashboardUiState.litersRemaining: Float?
    get() = snapshot?.fuelLevelPct?.let { it / 100f * TANK_CAPACITY_L }

val DashboardUiState.autonomyKm: Double?
    get() {
        val liters = litersRemaining?.toDouble() ?: return null
        val consumption = snapshot?.fuelConsumptionL100km ?: return null
        if (consumption <= 0.0) return null
        return liters / (consumption / 100.0)
    }

val DashboardUiState.tripConsumptionL100km: Double?
    get() {
        val trip = ongoingTrip ?: return null
        if (trip.distanceKm <= 0.0) return null
        return (trip.fuelConsumedLiters / trip.distanceKm) * 100.0
    }
