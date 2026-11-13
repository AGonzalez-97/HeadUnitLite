package com.onixgarage.auto.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onixgarage.auto.di.AutoServiceLocator
import com.onixgarage.core.obd.BluetoothService
import com.onixgarage.core.obd.OBDConnectionState
import com.onixgarage.core.obd.OBDSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OBDScreen(carContext: CarContext) : Screen(carContext) {

    private val pidReader = AutoServiceLocator.pidReader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var snapshot: OBDSnapshot? = null
    private var connectionState: OBDConnectionState = OBDConnectionState.Disconnected

    init {
        scope.launch { pidReader.snapshot.collect { snapshot = it; invalidate() } }
        scope.launch { BluetoothService.appConnectionState.collect { connectionState = it; invalidate() } }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = scope.cancel()
        })
    }

    override fun onGetTemplate(): Template {
        val snap = snapshot
        val isConnecting = connectionState is OBDConnectionState.Connecting
                || connectionState is OBDConnectionState.Initializing

        val pane = if (snap == null) {
            Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle(connectionLabel())
                        .addText("Waiting for OBD adapter")
                        .build(),
                )
                .setLoading(isConnecting)
                .build()
        } else {
            Pane.Builder()
                .addRow(Row.Builder().setTitle("Speed").addText("${snap.speedKmh} km/h").build())
                .addRow(Row.Builder().setTitle("RPM").addText("${snap.rpm.toInt()} rpm").build())
                .addRow(Row.Builder().setTitle("Fuel").addText("%.0f%%".format(snap.fuelLevelPct)).build())
                .addRow(Row.Builder().setTitle("Coolant").addText("${snap.coolantTempC}°C").build())
                .addAction(
                    Action.Builder()
                        .setTitle("Fault Codes")
                        .setOnClickListener { screenManager.push(DtcScreen(carContext)) }
                        .build(),
                )
                .build()
        }

        return PaneTemplate.Builder(pane)
            .setTitle("Onix Garage")
            .build()
    }

    private fun connectionLabel(): String = when (connectionState) {
        is OBDConnectionState.Connected -> "Connected"
        is OBDConnectionState.Connecting -> "Connecting..."
        is OBDConnectionState.Initializing -> "Initializing..."
        is OBDConnectionState.Disconnected -> "Disconnected"
        is OBDConnectionState.Error -> "Connection error"
    }
}
