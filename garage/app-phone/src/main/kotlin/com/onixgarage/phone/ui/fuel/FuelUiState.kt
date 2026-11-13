package com.onixgarage.phone.ui.fuel

import com.onixgarage.core.data.entity.FuelLoad

data class FuelUiState(
    val loads: List<FuelLoad> = emptyList(),
    val lastFullFill: FuelLoad? = null,
    val currentFuelLevelPct: Float? = null,
    val isSaving: Boolean = false,
    val lastFuelPrice: Double? = null,
    val avgConsumptionL100km: Double? = null,
    val avgDailyKm: Double? = null,
    val dailyCostARS: Double? = null,
) {
    val litersRemaining: Double?
        get() = currentFuelLevelPct?.let { it.toDouble() / 100.0 * TANK_CAPACITY_LITERS }

    val daysUntilEmpty: Double?
        get() {
            val l = litersRemaining ?: return null
            val d = avgDailyKm ?: return null
            val c = avgConsumptionL100km ?: return null
            if (d <= 0.0 || c <= 0.0) return null
            return l / (d * c / 100.0)
        }

    val autonomyKm: Double?
        get() {
            val l = litersRemaining ?: return null
            val c = avgConsumptionL100km ?: return null
            if (c <= 0.0) return null
            return l / c * 100.0
        }

    val costPerKm: Double?
        get() {
            val p = lastFuelPrice ?: return null
            val c = avgConsumptionL100km ?: return null
            if (p <= 0.0 || c <= 0.0) return null
            return p * c / 100.0
        }

    val monthlyProjectedCostARS: Double?
        get() = dailyCostARS?.takeIf { it > 0.0 }?.let { it * 30.0 }

    companion object {
        const val TANK_CAPACITY_LITERS: Double = 50.0
    }
}
