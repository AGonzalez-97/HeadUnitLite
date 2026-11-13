package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.ServiceRecord

object ServicePredictor {
    private const val AMBER_THRESHOLD_DAYS = 14.0
    private const val MS_PER_DAY = 86_400_000.0

    fun compute(
        record: ServiceRecord,
        currentOdometerKm: Int,
        avgDailyKm: Double,
        nowMs: Long = System.currentTimeMillis(),
    ): ServiceStatus {
        val daysByKm = record.nextServiceAtKm?.let { target ->
            if (avgDailyKm <= 0.0) null else (target - currentOdometerKm) / avgDailyKm
        }
        val daysByTime = record.nextServiceAtMs?.let { (it - nowMs) / MS_PER_DAY }
        val candidates = listOfNotNull(daysByKm, daysByTime)
        val alert = when {
            candidates.isEmpty() -> AlertLevel.GREEN
            candidates.any { it <= 0.0 } -> AlertLevel.RED
            candidates.min() <= AMBER_THRESHOLD_DAYS -> AlertLevel.AMBER
            else -> AlertLevel.GREEN
        }
        return ServiceStatus(record, daysByKm, daysByTime, alert)
    }
}
