package com.onixgarage.headunit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.obd.BluetoothService
import com.onixgarage.core.obd.PidReader
import com.onixgarage.headunit.di.HuServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HuViewModel(
    private val pidReader: PidReader,
) : ViewModel() {

    val uiState: StateFlow<HuUiState> = combine(
        BluetoothService.appConnectionState,
        pidReader.snapshot,
    ) { conn, snap ->
        HuUiState(connectionState = conn, snapshot = snap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HuUiState())

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HuViewModel(pidReader = HuServiceLocator.pidReader) as T
        }
    }
}
