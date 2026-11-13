package com.onixgarage.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.onixgarage.core.data.dao.DailyTripDao
import com.onixgarage.core.data.dao.FuelLoadDao
import com.onixgarage.core.data.dao.ServiceRecordDao
import com.onixgarage.core.data.dao.TirePressureRecordDao
import com.onixgarage.core.data.entity.DailyTrip
import com.onixgarage.core.data.entity.FuelLoad
import com.onixgarage.core.data.entity.ServiceRecord
import com.onixgarage.core.data.entity.TirePressureRecord
import com.onixgarage.core.data.migration.MIGRATION_1_2

@Database(
    entities = [
        FuelLoad::class,
        ServiceRecord::class,
        TirePressureRecord::class,
        DailyTrip::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fuelLoadDao(): FuelLoadDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun tirePressureRecordDao(): TirePressureRecordDao
    abstract fun dailyTripDao(): DailyTripDao

    companion object {
        private const val DB_NAME = "onixgarage.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
