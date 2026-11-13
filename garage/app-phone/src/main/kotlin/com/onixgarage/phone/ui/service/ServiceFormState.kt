package com.onixgarage.phone.ui.service

import com.onixgarage.core.data.entity.ServiceType

data class ServiceFormState(
    val serviceType: ServiceType = ServiceType.OIL,
    val odometerKm: String = "",
    val costARS: String = "",
    val notes: String = "",
    val nextServiceAtKm: String = "",
    val nextServiceAtDays: String = "",
    val showForm: Boolean = false,
) {
    val isValid: Boolean
        get() = odometerKm.toIntOrNull()?.let { it > 0 } == true &&
                costARS.toDoubleOrNull()?.let { it > 0.0 } == true
}

sealed interface ServiceFormField {
    data class Type(val value: ServiceType) : ServiceFormField
    data class Odometer(val value: String) : ServiceFormField
    data class Cost(val value: String) : ServiceFormField
    data class Notes(val value: String) : ServiceFormField
    data class NextKm(val value: String) : ServiceFormField
    data class NextDays(val value: String) : ServiceFormField
    data class ShowForm(val value: Boolean) : ServiceFormField
}
