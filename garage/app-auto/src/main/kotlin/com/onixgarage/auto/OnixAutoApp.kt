package com.onixgarage.auto

import android.app.Application
import com.onixgarage.auto.di.AutoServiceLocator

class OnixAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoServiceLocator.init(this)
    }
}
