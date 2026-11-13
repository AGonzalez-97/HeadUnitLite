package com.onixgarage.headunit

import android.app.Application
import com.onixgarage.headunit.di.HuServiceLocator

class OnixHeadUnitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HuServiceLocator.init(this)
    }
}
