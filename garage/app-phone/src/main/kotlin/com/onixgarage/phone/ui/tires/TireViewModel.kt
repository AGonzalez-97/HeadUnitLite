package com.onixgarage.phone.ui.tires

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.data.dao.TirePressureRecordDao
import com.onixgarage.core.data.entity.TirePressureRecord
import com.onixgarage.core.domain.TirePressureEvaluator
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.ui.common.AppViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TireViewModel(
    private val dao: TirePressureRecordDao,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _formState = MutableStateFlow(TireFormState())

    val formState: StateFlow<TireFormState> = _formState

    val uiState: StateFlow<TireUiState> = combine(
        dao.getAll(),
        _isSaving,
    ) { records, saving ->
        val statuses = records.map { TirePressureEvaluator.compute(it) }
        TireUiState(
            latestStatus = statuses.firstOrNull(),
            history = statuses,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), TireUiState())

    fun onFormChange(field: TireFormField) {
        _formState.value = when (field) {
            is TireFormField.FrontLeft -> _formState.value.copy(frontLeft = field.value)
            is TireFormField.FrontRight -> _formState.value.copy(frontRight = field.value)
            is TireFormField.RearLeft -> _formState.value.copy(rearLeft = field.value)
            is TireFormField.RearRight -> _formState.value.copy(rearRight = field.value)
            is TireFormField.Spare -> _formState.value.copy(spare = field.value)
            is TireFormField.RecommendedPsi -> _formState.value.copy(recommendedPsi = field.value)
            is TireFormField.Notes -> _formState.value.copy(notes = field.value)
            is TireFormField.ShowForm -> _formState.value.copy(showForm = field.value)
        }
    }

    fun onSaveFromForm() {
        val form = _formState.value
        if (!form.isValid) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val record = TirePressureRecord(
                    timestampMs = System.currentTimeMillis(),
                    frontLeftPsi = form.frontLeft.toFloat(),
                    frontRightPsi = form.frontRight.toFloat(),
                    rearLeftPsi = form.rearLeft.toFloat(),
                    rearRightPsi = form.rearRight.toFloat(),
                    sparePsi = form.spare.toFloatOrNull(),
                    recommendedPsi = form.recommendedPsi.toFloatOrNull() ?: 32f,
                    notes = form.notes.ifBlank { null },
                )
                dao.insert(record)
                _formState.value = TireFormState()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    companion object {
        val Factory = AppViewModelFactory {
            TireViewModel(dao = ServiceLocator.db.tirePressureRecordDao())
        }
    }
}
