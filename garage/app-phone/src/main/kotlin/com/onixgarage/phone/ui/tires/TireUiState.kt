package com.onixgarage.phone.ui.tires

import com.onixgarage.core.domain.TirePressureStatus

data class TireUiState(
    val latestStatus: TirePressureStatus? = null,
    val history: List<TirePressureStatus> = emptyList(),
    val isSaving: Boolean = false,
)
