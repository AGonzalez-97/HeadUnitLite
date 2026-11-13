package com.onixgarage.phone.ui.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onixgarage.core.obd.BluetoothService
import com.onixgarage.core.obd.OBDConnectionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class BtDeviceInfo(val name: String, val address: String)

class BluetoothViewModel(app: Application) : AndroidViewModel(app) {

    val connectionState: StateFlow<OBDConnectionState> = BluetoothService.appConnectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), OBDConnectionState.Disconnected)

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BtDeviceInfo> {
        val adapter = resolveAdapter() ?: return emptyList()
        return adapter.bondedDevices.orEmpty().map { device ->
            BtDeviceInfo(
                name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                address = device.address,
            )
        }.sortedBy { it.name }
    }

    fun connect(address: String) {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, address)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun disconnect() {
        getApplication<Application>().stopService(
            Intent(getApplication(), BluetoothService::class.java),
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveAdapter(): BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>().getSystemService(BluetoothManager::class.java).adapter
        } else {
            BluetoothAdapter.getDefaultAdapter()
        }
}
