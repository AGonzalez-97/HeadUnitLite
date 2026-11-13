package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.TirePressureRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class TirePressureEvaluatorTest {

    // --- evaluate() ---

    @Test
    fun `within range returns GREEN`() {
        assertEquals(AlertLevel.GREEN, TirePressureEvaluator.evaluate(32f, 32f))
    }

    @Test
    fun `delta over 2 psi returns AMBER`() {
        assertEquals(AlertLevel.AMBER, TirePressureEvaluator.evaluate(29f, 32f))
    }

    @Test
    fun `delta over 5 psi returns RED`() {
        assertEquals(AlertLevel.RED, TirePressureEvaluator.evaluate(26f, 32f))
    }

    @Test
    fun `below 20 psi returns RED regardless of delta`() {
        assertEquals(AlertLevel.RED, TirePressureEvaluator.evaluate(19f, 20f))
    }

    @Test
    fun `overpressure delta over 5 returns RED`() {
        assertEquals(AlertLevel.RED, TirePressureEvaluator.evaluate(38f, 32f))
    }

    @Test
    fun `overpressure delta between 2 and 5 returns AMBER`() {
        assertEquals(AlertLevel.AMBER, TirePressureEvaluator.evaluate(35f, 32f))
    }

    // --- compute() overall ---

    private fun record(
        fl: Float, fr: Float, rl: Float, rr: Float,
        spare: Float? = null,
        recommended: Float = 32f,
    ) = TirePressureRecord(
        timestampMs = 0L,
        frontLeftPsi = fl,
        frontRightPsi = fr,
        rearLeftPsi = rl,
        rearRightPsi = rr,
        sparePsi = spare,
        recommendedPsi = recommended,
    )

    @Test
    fun `all tires in range — overall GREEN`() {
        val status = TirePressureEvaluator.compute(record(32f, 32f, 32f, 32f))
        assertEquals(AlertLevel.GREEN, status.overall)
    }

    @Test
    fun `one tire AMBER — overall AMBER`() {
        val status = TirePressureEvaluator.compute(record(32f, 32f, 32f, 29f))
        assertEquals(AlertLevel.AMBER, status.overall)
        assertEquals(AlertLevel.AMBER, status.rearRightAlert)
    }

    @Test
    fun `one tire RED — overall RED`() {
        val status = TirePressureEvaluator.compute(record(32f, 32f, 32f, 25f))
        assertEquals(AlertLevel.RED, status.overall)
    }

    @Test
    fun `spare tire below 20 — overall RED`() {
        val status = TirePressureEvaluator.compute(record(32f, 32f, 32f, 32f, spare = 15f))
        assertEquals(AlertLevel.RED, status.overall)
        assertEquals(AlertLevel.RED, status.spareAlert)
    }

    @Test
    fun `spare null — spareAlert null, overall from four tires`() {
        val status = TirePressureEvaluator.compute(record(32f, 32f, 32f, 32f, spare = null))
        assertEquals(null, status.spareAlert)
        assertEquals(AlertLevel.GREEN, status.overall)
    }
}
