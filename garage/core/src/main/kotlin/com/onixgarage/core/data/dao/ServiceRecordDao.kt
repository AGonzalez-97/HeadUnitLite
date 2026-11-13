package com.onixgarage.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.onixgarage.core.data.entity.ServiceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {

    @Insert
    suspend fun insert(record: ServiceRecord): Long

    @Query("SELECT * FROM service_records ORDER BY timestampMs DESC")
    fun getAll(): Flow<List<ServiceRecord>>

    @Query("SELECT * FROM service_records WHERE serviceType = :type ORDER BY timestampMs DESC")
    fun getByType(type: String): Flow<List<ServiceRecord>>

    @Query(
        "SELECT * FROM service_records " +
            "WHERE nextServiceAtKm IS NOT NULL AND nextServiceAtKm <= :currentOdometer " +
            "ORDER BY nextServiceAtKm ASC"
    )
    fun getOverdue(currentOdometer: Int): Flow<List<ServiceRecord>>

    @Query("DELETE FROM service_records WHERE id = :id")
    suspend fun delete(id: Long)
}
