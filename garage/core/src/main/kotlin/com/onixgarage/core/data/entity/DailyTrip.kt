package com.onixgarage.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_trips")
data class DailyTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestampMs: Long,
    val endTimestampMs: Long? = null,
    val distanceKm: Double = 0.0,
    val durationMs: Long = 0L,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val startCoolantTempC: Int = 0,
    val endCoolantTempC: Int? = null,
    val fuelConsumedLiters: Double = 0.0,
    val costARS: Double = 0.0,
)
