package com.onixgarage.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onixgarage.core.data.entity.TirePressureRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TirePressureRecordDao {

    @Insert
    suspend fun insert(record: TirePressureRecord): Long

    @Query("SELECT * FROM tire_pressure_records ORDER BY timestampMs DESC LIMIT 1")
    fun getLatest(): Flow<TirePressureRecord?>

    @Query("SELECT * FROM tire_pressure_records ORDER BY timestampMs DESC")
    fun getAll(): Flow<List<TirePressureRecord>>

    @Query("DELETE FROM tire_pressure_records WHERE id = :id")
    suspend fun delete(id: Long)
}
