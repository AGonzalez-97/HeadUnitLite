package com.onixgarage.phone.ui.fuel

import com.onixgarage.core.data.entity.FuelType

data class FuelFormState(
    val liters: String = "",
    val pricePerLiter: String = "",
    val odometerKm: String = "",
    val fuelType: FuelType = FuelType.SUPER,
    val isTankFull: Boolean = false,
    val showForm: Boolean = false,
) {
    val litersValue: Double? get() = liters.toDoubleOrNull()?.takeIf { it > 0.0 }
    val priceValue: Double? get() = pricePerLiter.toDoubleOrNull()?.takeIf { it > 0.0 }
    val odometerValue: Int? get() = odometerKm.toIntOrNull()?.takeIf { it > 0 }
    val totalCost: Double?
        get() {
            val l = litersValue ?: return null
            val p = priceValue ?: return null
            return l * p
        }
    val isValid: Boolean
        get() = litersValue != null && priceValue != null && odometerValue != null
}

sealed interface FuelFormField {
    data class Liters(val value: String) : FuelFormField
    data class PricePerLiter(val value: String) : FuelFormField
    data class OdometerKm(val value: String) : FuelFormField
    data class Type(val value: FuelType) : FuelFormField
    data class TankFull(val value: Boolean) : FuelFormField
    data class ShowForm(val value: Boolean) : FuelFormField
}
