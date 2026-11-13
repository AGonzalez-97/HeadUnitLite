package com.onixgarage.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tire_pressure_records")
data class TirePressureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val frontLeftPsi: Float,
    val frontRightPsi: Float,
    val rearLeftPsi: Float,
    val rearRightPsi: Float,
    val sparePsi: Float? = null,
    val recommendedPsi: Float = 32f,
    val notes: String? = null,
)
