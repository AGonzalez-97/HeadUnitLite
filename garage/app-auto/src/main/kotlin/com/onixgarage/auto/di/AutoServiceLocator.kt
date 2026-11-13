package com.onixgarage.auto.di

import android.app.Application
import com.onixgarage.core.obd.PidReader
import com.onixgarage.core.obd.PidReaderFactory

object AutoServiceLocator {

    lateinit var pidReader: PidReader
        private set

    fun init(app: Application) {
        if (::pidReader.isInitialized) return
        pidReader = PidReaderFactory.get()
    }
}
