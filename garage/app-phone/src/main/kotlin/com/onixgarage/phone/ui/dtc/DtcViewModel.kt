package com.onixgarage.phone.ui.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.obd.PidReader
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.ui.common.AppViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DtcViewModel(
    private val pidReader: PidReader,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val isClearing = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DtcUiState> = combine(
        pidReader.dtcCodes,
        isRefreshing,
        isClearing,
        lastError,
    ) { codes, refreshing, clearing, error ->
        DtcUiState(codes, refreshing, clearing, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DtcUiState())

    fun onRefresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            lastError.value = null
            try { pidReader.refreshDtcCodes() }
            catch (t: Throwable) { lastError.value = t.message }
            finally { isRefreshing.value = false }
        }
    }

    fun onClearDtcs() {
        viewModelScope.launch {
            isClearing.value = true
            lastError.value = null
            try { pidReader.clearDtcCodes() }
            catch (t: Throwable) { lastError.value = t.message }
            finally { isClearing.value = false }
        }
    }

    companion object {
        val Factory = AppViewModelFactory {
            DtcViewModel(pidReader = ServiceLocator.pidReader)
        }
    }
}
