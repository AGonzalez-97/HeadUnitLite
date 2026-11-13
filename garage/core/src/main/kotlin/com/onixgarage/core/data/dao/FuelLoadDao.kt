package com.onixgarage.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onixgarage.core.data.entity.FuelLoad
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLoadDao {

    @Insert
    suspend fun insert(load: FuelLoad): Long

    @Query("SELECT * FROM fuel_loads ORDER BY timestampMs DESC")
    fun getAll(): Flow<List<FuelLoad>>

    @Query("SELECT * FROM fuel_loads WHERE isTankFull = 1 ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLastFullFill(): FuelLoad?

    @Query("SELECT * FROM fuel_loads WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs DESC")
    fun getInRange(fromMs: Long, toMs: Long): Flow<List<FuelLoad>>

    @Query("DELETE FROM fuel_loads WHERE id = :id")
    suspend fun delete(id: Long)
}
