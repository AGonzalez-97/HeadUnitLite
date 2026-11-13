package com.onixgarage.phone.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.dao.FuelLoadDao
import com.onixgarage.core.data.entity.FuelLoad
import com.onixgarage.core.obd.PidReader
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.ui.common.AppViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class FuelViewModel(
    private val fuelDao: FuelLoadDao,
    private val tripDao: DailyTripDao,
    private val pidReader: PidReader,
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)

    private val _formState = MutableStateFlow(FuelFormState())
    val formState: StateFlow<FuelFormState> = _formState

    private val aggregatesFlow = flow {
        while (true) {
            val now = System.currentTimeMillis()
            val from = now - THIRTY_DAYS_MS
            val avgConsumption = tripDao.getAvgConsumptionInPeriod(from, now)
            val totalKm = tripDao.getTotalKmInPeriod(from, now)
            emit(Aggregates(avgConsumption = avgConsumption, avgDailyKm = totalKm / 30.0))
            delay(60_000L)
        }
    }

    private val todayTripsFlow = run {
        val (start, end) = todayBounds()
        tripDao.getTodayTrips(start, end)
    }

    val uiState: StateFlow<FuelUiState> = combine(
        fuelDao.getAll(),
        pidReader.snapshot,
        isSaving,
        todayTripsFlow,
        aggregatesFlow,
    ) { loads, snapshot, saving, todayTrips, agg ->
        val lastFull = loads.firstOrNull { it.isTankFull }
        FuelUiState(
            loads = loads,
            lastFullFill = lastFull,
            currentFuelLevelPct = snapshot?.fuelLevelPct,
            isSaving = saving,
            lastFuelPrice = lastFull?.pricePerLiter,
            avgConsumptionL100km = agg.avgConsumption,
            avgDailyKm = agg.avgDailyKm.takeIf { it > 0.0 },
            dailyCostARS = todayTrips.sumOf { it.costARS },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), FuelUiState())

    fun onFormChange(field: FuelFormField) {
        _formState.value = when (field) {
            is FuelFormField.Liters -> _formState.value.copy(liters = field.value)
            is FuelFormField.PricePerLiter -> _formState.value.copy(pricePerLiter = field.value)
            is FuelFormField.OdometerKm -> _formState.value.copy(odometerKm = field.value)
            is FuelFormField.Type -> _formState.value.copy(fuelType = field.value)
            is FuelFormField.TankFull -> _formState.value.copy(isTankFull = field.value)
            is FuelFormField.ShowForm -> _formState.value.copy(showForm = field.value)
        }
    }

    fun onSaveFromForm() {
        val form = _formState.value
        if (!form.isValid || isSaving.value) return
        val liters = form.litersValue ?: return
        val price = form.priceValue ?: return
        val odo = form.odometerValue ?: return

        viewModelScope.launch {
            isSaving.value = true
            try {
                val priorFull = fuelDao.getLastFullFill()
                val consumption: Double? =
                    if (form.isTankFull && priorFull != null && odo > priorFull.odometerKm) {
                        liters * 100.0 / (odo - priorFull.odometerKm)
                    } else null

                fuelDao.insert(
                    FuelLoad(
                        timestampMs = System.currentTimeMillis(),
                        liters = liters,
                        fuelType = form.fuelType.toDbValue(),
                        pricePerLiter = price,
                        totalCost = liters * price,
                        isTankFull = form.isTankFull,
                        odometerKm = odo,
                        fuelConsumptionL100km = consumption,
                    )
                )
                _formState.value = FuelFormState()
            } finally {
                isSaving.value = false
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { fuelDao.delete(id) }
    }

    private data class Aggregates(val avgConsumption: Double?, val avgDailyKm: Double)

    private fun todayBounds(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24L * 3600L * 1000L - 1L
        return start to end
    }

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24L * 3600L * 1000L

        val Factory = AppViewModelFactory {
            FuelViewModel(
                fuelDao = ServiceLocator.db.fuelLoadDao(),
                tripDao = ServiceLocator.db.dailyTripDao(),
                pidReader = ServiceLocator.pidReader,
            )
        }
    }
}
