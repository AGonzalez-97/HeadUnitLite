package com.onixgarage.phone.ui.tires

data class TireFormState(
    val frontLeft: String = "",
    val frontRight: String = "",
    val rearLeft: String = "",
    val rearRight: String = "",
    val spare: String = "",
    val recommendedPsi: String = "32",
    val notes: String = "",
    val showForm: Boolean = false,
) {
    val isValid: Boolean
        get() = listOf(frontLeft, frontRight, rearLeft, rearRight)
            .all { it.toFloatOrNull()?.let { v -> v > 0f } == true } &&
            recommendedPsi.toFloatOrNull()?.let { it > 0f } == true
}

sealed interface TireFormField {
    data class FrontLeft(val value: String) : TireFormField
    data class FrontRight(val value: String) : TireFormField
    data class RearLeft(val value: String) : TireFormField
    data class RearRight(val value: String) : TireFormField
    data class Spare(val value: String) : TireFormField
    data class RecommendedPsi(val value: String) : TireFormField
    data class Notes(val value: String) : TireFormField
    data class ShowForm(val value: Boolean) : TireFormField
}
