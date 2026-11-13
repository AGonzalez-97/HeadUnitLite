package com.axel.onixlauncher.logic

import com.axel.onixlauncher.PlaceRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.79.8: the weather and place name are refreshed when the car moved far from the spot where
 * they were last fetched, not only every 15 minutes.
 */
class PlaceRefreshTest {

    // Tafí Viejo (default spot) and San Miguel de Tucumán centre: roughly 12 km apart.
    private val tafiLat = -26.7368
    private val tafiLon = -65.258
    private val smtLat = -26.8083
    private val smtLon = -65.2176

    @Test
    fun moving_to_another_city_triggers_a_refresh() {
        assertTrue(PlaceRefresh.movedFar(tafiLat, tafiLon, smtLat, smtLon))
    }

    @Test
    fun staying_in_the_same_spot_does_not() {
        assertFalse(PlaceRefresh.movedFar(tafiLat, tafiLon, tafiLat, tafiLon))
    }

    @Test
    fun a_short_drive_under_the_threshold_does_not() {
        // About 1.1 km north.
        assertFalse(PlaceRefresh.movedFar(tafiLat, tafiLon, tafiLat + 0.01, tafiLon))
    }

    @Test
    fun the_threshold_is_3_km() {
        // 0.027 degrees of latitude is about 3.0 km: just over triggers, just under does not.
        assertTrue(PlaceRefresh.movedFar(tafiLat, tafiLon, tafiLat + 0.028, tafiLon))
        assertFalse(PlaceRefresh.movedFar(tafiLat, tafiLon, tafiLat + 0.026, tafiLon))
    }

    @Test
    fun without_a_previous_fetch_position_it_refreshes() {
        assertTrue(PlaceRefresh.movedFar(null, null, tafiLat, tafiLon))
    }
}
