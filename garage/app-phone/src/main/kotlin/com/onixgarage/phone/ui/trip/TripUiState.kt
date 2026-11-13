package com.onixgarage.phone.ui.trip

import com.onixgarage.core.data.entity.DailyTrip

data class TripUiState(
    val period: TripPeriod = TripPeriod.Week,
    val trips: List<DailyTrip> = emptyList(),
    val totalKm: Double = 0.0,
    val totalCostARS: Double = 0.0,
    val totalFuelL: Double = 0.0,
    val avgConsumption: Double? = null,
    val tripCount: Int = 0,
    val ongoingTrip: DailyTrip? = null,
)
