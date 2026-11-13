package com.onixgarage.phone

import android.app.Application
import com.onixgarage.phone.di.ServiceLocator
import com.onixgarage.phone.worker.ServiceAlertWorker

class OnixPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceAlertWorker.schedule(this)
    }
}
