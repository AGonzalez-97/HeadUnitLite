package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.TirePressureRecord
import kotlin.math.abs

object TirePressureEvaluator {

    private const val MIN_SAFE_PSI = 20f
    private const val RED_DELTA = 5f
    private const val AMBER_DELTA = 2f

    fun evaluate(psi: Float, recommended: Float): AlertLevel {
        val delta = abs(psi - recommended)
        return when {
            psi < MIN_SAFE_PSI || delta > RED_DELTA -> AlertLevel.RED
            delta > AMBER_DELTA -> AlertLevel.AMBER
            else -> AlertLevel.GREEN
        }
    }

    fun compute(record: TirePressureRecord): TirePressureStatus {
        val rec = record.recommendedPsi
        val fl = evaluate(record.frontLeftPsi, rec)
        val fr = evaluate(record.frontRightPsi, rec)
        val rl = evaluate(record.rearLeftPsi, rec)
        val rr = evaluate(record.rearRightPsi, rec)
        val spare = record.sparePsi?.let { evaluate(it, rec) }
        val overall = listOfNotNull(fl, fr, rl, rr, spare)
            .maxByOrNull { it.ordinal } ?: AlertLevel.GREEN
        return TirePressureStatus(record, fl, fr, rl, rr, spare, overall)
    }
}
