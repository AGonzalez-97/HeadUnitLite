package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.TirePressureRecord

data class TirePressureStatus(
    val record: TirePressureRecord,
    val frontLeftAlert: AlertLevel,
    val frontRightAlert: AlertLevel,
    val rearLeftAlert: AlertLevel,
    val rearRightAlert: AlertLevel,
    val spareAlert: AlertLevel?,
    val overall: AlertLevel,
)
