package com.onixgarage.phone.di

import android.app.Application
import com.onixgarage.core.data.AppDatabase
import com.onixgarage.core.data.recorder.TripRecorder
import com.onixgarage.core.obd.BluetoothService
import com.onixgarage.core.obd.PidReader
import com.onixgarage.core.obd.PidReaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ServiceLocator {

    lateinit var db: AppDatabase
        private set
    lateinit var pidReader: PidReader
        private set
    lateinit var tripRecorder: TripRecorder
        private set
    lateinit var appScope: CoroutineScope
        private set

    fun init(app: Application) {
        if (::db.isInitialized) return
        db = AppDatabase.getInstance(app)
        pidReader = PidReaderFactory.get()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tripRecorder = TripRecorder(
            tripDao = db.dailyTripDao(),
            fuelLoadDao = db.fuelLoadDao(),
            connectionState = BluetoothService.appConnectionState,
            snapshot = pidReader.snapshot,
            scope = appScope,
        )
        tripRecorder.start()
    }
}
