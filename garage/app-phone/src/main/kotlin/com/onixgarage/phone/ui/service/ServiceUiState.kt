package com.onixgarage.phone.ui.service

import com.onixgarage.core.domain.ServiceStatus

data class ServiceUiState(
    val statuses: List<ServiceStatus> = emptyList(),
    val alertStatuses: List<ServiceStatus> = emptyList(),
    val estimatedOdometerKm: Int = 0,
    val avgDailyKm: Double = 0.0,
    val isSaving: Boolean = false,
)
