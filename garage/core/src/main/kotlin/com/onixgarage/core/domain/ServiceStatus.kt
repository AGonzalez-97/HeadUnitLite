package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.ServiceRecord

enum class AlertLevel { GREEN, AMBER, RED }

data class ServiceStatus(
    val record: ServiceRecord,
    val daysUntilDueByKm: Double?,
    val daysUntilDueByTime: Double?,
    val alert: AlertLevel,
) {
    val minDaysUntilDue: Double?
        get() = listOfNotNull(daysUntilDueByKm, daysUntilDueByTime).minOrNull()
}
