package com.onixgarage.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_loads")
data class FuelLoad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val liters: Double,
    val fuelType: String,           // FuelType.toDbValue()
    val pricePerLiter: Double,
    val totalCost: Double,          // stored: pricePerLiter * liters
    val isTankFull: Boolean,
    val odometerKm: Int,
    val fuelConsumptionL100km: Double? = null,  // null until 2 consecutive full fills
)
