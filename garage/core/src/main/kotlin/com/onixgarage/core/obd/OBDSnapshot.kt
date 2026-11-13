package com.onixgarage.core.obd

data class OBDSnapshot(
    val coolantTempC: Int,
    val rpm: Float,
    val speedKmh: Int,
    val fuelLevelPct: Float,
    val mafGs: Double?,
    val throttlePct: Float,
    val engineLoadPct: Float,
    val distanceSinceDtcClearKm: Int,
) {
    val fuelConsumptionLPH: Double? =
        mafGs?.let { (it * 3600.0) / (14.7 * 730.0) }

    val fuelConsumptionL100km: Double? =
        if (fuelConsumptionLPH != null && speedKmh > 0)
            (fuelConsumptionLPH / speedKmh) * 100.0
        else null
}
