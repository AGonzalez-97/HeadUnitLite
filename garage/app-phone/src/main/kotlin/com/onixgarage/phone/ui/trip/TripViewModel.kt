package com.onixgarage.phone.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.entity.DailyTrip
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.ui.common.AppViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModel(
    private val dao: DailyTripDao,
) : ViewModel() {

    private val period = MutableStateFlow(TripPeriod.Week)

    private val tripsFlow = period.flatMapLatest { p ->
        val now = System.currentTimeMillis()
        dao.getInRange(p.fromMs(now), now)
    }

    private val ongoingTripFlow: Flow<DailyTrip?> = flow {
        while (true) {
            emit(dao.getOngoingTrip())
            delay(5_000L)
        }
    }

    val uiState: StateFlow<TripUiState> = combine(
        period,
        tripsFlow,
        ongoingTripFlow,
    ) { p, trips, ongoing ->
        val totalKm = trips.sumOf { it.distanceKm }
        val totalFuelL = trips.sumOf { it.fuelConsumedLiters }
        TripUiState(
            period = p,
            trips = trips,
            totalKm = totalKm,
            totalCostARS = trips.sumOf { it.costARS },
            totalFuelL = totalFuelL,
            avgConsumption = if (totalKm > 0) (totalFuelL / totalKm) * 100 else null,
            tripCount = trips.size,
            ongoingTrip = ongoing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), TripUiState())

    fun onPeriodChanged(newPeriod: TripPeriod) {
        period.value = newPeriod
    }

    companion object {
        val Factory = AppViewModelFactory {
            TripViewModel(dao = ServiceLocator.db.dailyTripDao())
        }
    }
}
