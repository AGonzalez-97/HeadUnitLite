package com.onixgarage.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_records")
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceType: String,        // ServiceType.toDbValue()
    val timestampMs: Long,
    val odometerKm: Int,
    val nextServiceAtKm: Int? = null,
    val nextServiceAtMs: Long? = null,
    val costARS: Double,
    val notes: String? = null,
)
