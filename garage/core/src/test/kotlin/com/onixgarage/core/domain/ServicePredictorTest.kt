package com.onixgarage.core.domain

import com.onixgarage.core.data.entity.ServiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServicePredictorTest {

    private val baseRecord = ServiceRecord(
        serviceType = "oil_change",
        timestampMs = 0L,
        odometerKm = 10_000,
        costARS = 0.0,
    )

    private val nowMs = 1_000_000_000L

    @Test
    fun `no thresholds returns GREEN`() {
        val result = ServicePredictor.compute(baseRecord, 11_000, 50.0, nowMs)
        assertEquals(AlertLevel.GREEN, result.alert)
        assertNull(result.daysUntilDueByKm)
        assertNull(result.daysUntilDueByTime)
    }

    @Test
    fun `km threshold far away returns GREEN`() {
        val record = baseRecord.copy(nextServiceAtKm = 16_000)
        val result = ServicePredictor.compute(record, 11_000, 100.0, nowMs)
        // 5000 km remaining / 100 km per day = 50 days → GREEN
        assertEquals(AlertLevel.GREEN, result.alert)
        assertEquals(50.0, result.daysUntilDueByKm!!, 0.001)
    }

    @Test
    fun `km threshold within 14 days returns AMBER`() {
        val record = baseRecord.copy(nextServiceAtKm = 11_500)
        val result = ServicePredictor.compute(record, 11_000, 100.0, nowMs)
        // 500 km / 100 km per day = 5 days → AMBER
        assertEquals(AlertLevel.AMBER, result.alert)
    }

    @Test
    fun `km threshold already passed returns RED`() {
        val record = baseRecord.copy(nextServiceAtKm = 10_500)
        val result = ServicePredictor.compute(record, 11_000, 50.0, nowMs)
        // -500 km → overdue → RED
        assertEquals(AlertLevel.RED, result.alert)
    }

    @Test
    fun `time threshold within 14 days returns AMBER`() {
        val tenDaysMs = 10 * 86_400_000L
        val record = baseRecord.copy(nextServiceAtMs = nowMs + tenDaysMs)
        val result = ServicePredictor.compute(record, 11_000, 50.0, nowMs)
        assertEquals(AlertLevel.AMBER, result.alert)
    }

    @Test
    fun `time threshold already passed returns RED`() {
        val record = baseRecord.copy(nextServiceAtMs = nowMs - 1L)
        val result = ServicePredictor.compute(record, 11_000, 50.0, nowMs)
        assertEquals(AlertLevel.RED, result.alert)
    }

    @Test
    fun `zero avgDailyKm skips km threshold`() {
        val record = baseRecord.copy(nextServiceAtKm = 11_000)
        val result = ServicePredictor.compute(record, 11_000, 0.0, nowMs)
        assertNull(result.daysByKm)
        assertEquals(AlertLevel.GREEN, result.alert)
    }

    @Test
    fun `worst of km and time wins — km RED time AMBER gives RED`() {
        val tenDaysMs = 10 * 86_400_000L
        val record = baseRecord.copy(
            nextServiceAtKm = 10_500,  // overdue → RED
            nextServiceAtMs = nowMs + tenDaysMs,  // 10 days → AMBER
        )
        val result = ServicePredictor.compute(record, 11_000, 50.0, nowMs)
        assertEquals(AlertLevel.RED, result.alert)
    }
}
