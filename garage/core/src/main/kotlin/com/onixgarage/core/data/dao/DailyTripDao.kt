package com.onixgarage.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.onixgarage.core.data.entity.DailyTrip
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTripDao {

    @Insert
    suspend fun insert(trip: DailyTrip): Long

    @Update
    suspend fun update(trip: DailyTrip)

    @Query("SELECT * FROM daily_trips WHERE endTimestampMs IS NULL LIMIT 1")
    suspend fun getOngoingTrip(): DailyTrip?

    @Query("SELECT * FROM daily_trips WHERE id = :id")
    suspend fun getById(id: Long): DailyTrip?

    @Query(
        "SELECT * FROM daily_trips " +
            "WHERE startTimestampMs BETWEEN :dayStartMs AND :dayEndMs " +
            "ORDER BY startTimestampMs DESC"
    )
    fun getTodayTrips(dayStartMs: Long, dayEndMs: Long): Flow<List<DailyTrip>>

    @Query(
        "SELECT * FROM daily_trips " +
            "WHERE startTimestampMs BETWEEN :fromMs AND :toMs " +
            "ORDER BY startTimestampMs DESC"
    )
    fun getInRange(fromMs: Long, toMs: Long): Flow<List<DailyTrip>>

    @Query(
        "SELECT COALESCE(SUM(distanceKm), 0.0) FROM daily_trips " +
            "WHERE startTimestampMs BETWEEN :fromMs AND :toMs"
    )
    suspend fun getTotalKmInPeriod(fromMs: Long, toMs: Long): Double

    @Query(
        "SELECT CASE WHEN SUM(distanceKm) > 0 " +
            "THEN (SUM(fuelConsumedLiters) * 100.0) / SUM(distanceKm) " +
            "ELSE NULL END " +
            "FROM daily_trips WHERE startTimestampMs BETWEEN :fromMs AND :toMs"
    )
    suspend fun getAvgConsumptionInPeriod(fromMs: Long, toMs: Long): Double?
}
