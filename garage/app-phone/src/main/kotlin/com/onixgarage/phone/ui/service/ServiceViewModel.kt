package com.onixgarage.phone.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.dao.FuelLoadDao
import com.onixgarage.core.data.dao.ServiceRecordDao
import com.onixgarage.core.data.entity.ServiceRecord
import com.onixgarage.core.data.entity.ServiceType
import com.onixgarage.core.domain.AlertLevel
import com.onixgarage.core.domain.ServicePredictor
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

class ServiceViewModel(
    private val dao: ServiceRecordDao,
    private val tripDao: DailyTripDao,
    private val fuelLoadDao: FuelLoadDao,
) : ViewModel() {

    private val odometer = MutableStateFlow(0)

    private val avgDailyKmFlow = flow {
        while (true) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -30)
            val since = cal.timeInMillis
            val km = tripDao.getTotalKmInPeriod(since, System.currentTimeMillis())
            emit(km / 30.0)
            delay(60_000L)
        }
    }

    private val _isSaving = MutableStateFlow(false)
    private val _formState = MutableStateFlow(ServiceFormState())

    val formState: StateFlow<ServiceFormState> = _formState

    val uiState: StateFlow<ServiceUiState> = combine(
        dao.getAll(),
        odometer,
        avgDailyKmFlow,
        _isSaving,
    ) { records, km, avgKm, saving ->
        val statuses = records
            .map { ServicePredictor.compute(it, km, avgKm) }
            .sortedWith(
                compareByDescending<com.onixgarage.core.domain.ServiceStatus> { it.alert.ordinal }
                    .thenBy { it.minDaysUntilDue ?: Double.MAX_VALUE }
            )
        val alertStatuses = statuses.filter { it.alert != AlertLevel.GREEN }
        ServiceUiState(
            statuses = statuses,
            alertStatuses = alertStatuses,
            estimatedOdometerKm = km,
            avgDailyKm = avgKm,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ServiceUiState())

    init { refreshOdometer() }

    fun onFormChange(field: ServiceFormField) {
        _formState.value = when (field) {
            is ServiceFormField.Type -> _formState.value.copy(serviceType = field.value)
            is ServiceFormField.Odometer -> _formState.value.copy(odometerKm = field.value)
            is ServiceFormField.Cost -> _formState.value.copy(costARS = field.value)
            is ServiceFormField.Notes -> _formState.value.copy(notes = field.value)
            is ServiceFormField.NextKm -> _formState.value.copy(nextServiceAtKm = field.value)
            is ServiceFormField.NextDays -> _formState.value.copy(nextServiceAtDays = field.value)
            is ServiceFormField.ShowForm -> _formState.value.copy(showForm = field.value)
        }
    }

    fun onSaveFromForm() {
        val form = _formState.value
        if (!form.isValid) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val nowMs = System.currentTimeMillis()
                val days = form.nextServiceAtDays.toIntOrNull() ?: 0
                val nextServiceAtMs = if (days > 0) nowMs + days.toLong() * 86_400_000L else null
                val record = ServiceRecord(
                    serviceType = form.serviceType.toDbValue(),
                    timestampMs = nowMs,
                    odometerKm = form.odometerKm.toInt(),
                    costARS = form.costARS.toDouble(),
                    notes = form.notes.ifBlank { null },
                    nextServiceAtKm = form.nextServiceAtKm.toIntOrNull()?.takeIf { it > 0 },
                    nextServiceAtMs = nextServiceAtMs,
                )
                dao.insert(record)
                _formState.value = ServiceFormState()
                refreshOdometer()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch {
            dao.delete(id)
            refreshOdometer()
        }
    }

    private fun refreshOdometer() {
        viewModelScope.launch {
            val lastFuel = fuelLoadDao.getLastFullFill()
            val anchorKm = lastFuel?.odometerKm ?: 0
            val anchorMs = lastFuel?.timestampMs ?: 0L
            val now = System.currentTimeMillis()
            val accum = tripDao.getTotalKmInPeriod(anchorMs, now)
            odometer.value = anchorKm + accum.toInt()
        }
    }

    companion object {
        val Factory = AppViewModelFactory {
            ServiceViewModel(
                dao = ServiceLocator.db.serviceRecordDao(),
                tripDao = ServiceLocator.db.dailyTripDao(),
                fuelLoadDao = ServiceLocator.db.fuelLoadDao(),
            )
        }
    }
}
