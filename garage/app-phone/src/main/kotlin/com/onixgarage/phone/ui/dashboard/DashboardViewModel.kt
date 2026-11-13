package com.onixgarage.phone.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.dao.FuelLoadDao
import com.onixgarage.core.obd.BluetoothService
import com.onixgarage.core.obd.PidReader
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.ui.common.AppViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val pidReader: PidReader,
    private val tripDao: DailyTripDao,
    private val fuelLoadDao: FuelLoadDao,
) : ViewModel() {

    private val lastFuelPriceFlow = flow {
        emit(fuelLoadDao.getLastFullFill()?.pricePerLiter)
    }

    private val ongoingTripFlow = flow {
        emit(tripDao.getOngoingTrip())
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        BluetoothService.appConnectionState,
        pidReader.snapshot,
        ongoingTripFlow,
        lastFuelPriceFlow,
    ) { connection, snapshot, ongoing, price ->
        val costPerHour = snapshot?.fuelConsumptionLPH?.let { lph ->
            price?.let { p -> lph * p }
        }
        DashboardUiState(
            connection = connection,
            snapshot = snapshot,
            ongoingTrip = ongoing,
            lastFuelPricePerLiter = price,
            estimatedCostPerHour = costPerHour,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DashboardUiState())

    companion object {
        val Factory = AppViewModelFactory {
            DashboardViewModel(
                pidReader = ServiceLocator.pidReader,
                tripDao = ServiceLocator.db.dailyTripDao(),
                fuelLoadDao = ServiceLocator.db.fuelLoadDao(),
            )
        }
    }
}
