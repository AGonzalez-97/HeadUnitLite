package com.onixgarage.phone.ui.dtc

data class DtcUiState(
    val codes: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val isClearing: Boolean = false,
    val lastError: String? = null,
)
