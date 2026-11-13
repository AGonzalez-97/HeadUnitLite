package com.onixgarage.headunit.di

import android.app.Application
import com.onixgarage.core.obd.PidReader
import com.onixgarage.core.obd.PidReaderFactory

object HuServiceLocator {

    lateinit var pidReader: PidReader
        private set

    fun init(app: Application) {
        if (::pidReader.isInitialized) return
        pidReader = PidReaderFactory.get()
    }
}
