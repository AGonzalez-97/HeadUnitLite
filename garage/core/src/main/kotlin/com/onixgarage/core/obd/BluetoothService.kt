package com.onixgarage.core.obd

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@SuppressLint("MissingPermission")
class BluetoothService : Service(), ObdCommandPort {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<OBDConnectionState>(OBDConnectionState.Disconnected)
    override val connectionState: StateFlow<OBDConnectionState> = _connectionState.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val ioMutex = Mutex()

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "onixgarage_obd_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"

        @Volatile
        var instance: BluetoothService? = null
            private set

        private val _appConnectionState = MutableStateFlow<OBDConnectionState>(OBDConnectionState.Disconnected)
        val appConnectionState: StateFlow<OBDConnectionState> = _appConnectionState.asStateFlow()
    }

    private fun setState(s: OBDConnectionState) {
        _connectionState.value = s
        _appConnectionState.value = s
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
            ?: run { stopSelf(); return START_NOT_STICKY }

        val notification = buildNotification("Conectando…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch { connectionLoop(address) }
        return START_STICKY
    }

    override fun onDestroy() {
        _appConnectionState.value = OBDConnectionState.Disconnected
        instance = null
        serviceJob.cancel()
        closeSocket()
        super.onDestroy()
    }

    private suspend fun connectionLoop(address: String) {
        var attempt = 0
        while (serviceScope.isActive) {
            setState(OBDConnectionState.Connecting)
            updateNotification("Conectando…")
            try {
                connect(address)
                setState(OBDConnectionState.Initializing)
                updateNotification("Configurando adaptador…")
                runInitSequence()
                setState(OBDConnectionState.Connected)
                updateNotification("OBD2 conectado")
                attempt = 0
                awaitDisconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                closeSocket()
                setState(OBDConnectionState.Error(
                    e.message ?: "Permission denied",
                    recoverable = false,
                ))
                // Non-recoverable — stop retrying
                break
            } catch (e: Exception) {
                closeSocket()
                setState(OBDConnectionState.Error(
                    e.message ?: "Connection failed",
                    recoverable = true,
                ))
                updateNotification("Reconectando…")
                val backoffMs = minOf(30_000L, 1_000L shl attempt.coerceAtMost(5))
                delay(backoffMs)
                attempt++
            }
        }
    }

    private suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        val adapter: BluetoothAdapter = resolveAdapter()
            ?: throw IOException("Bluetooth not available")

        adapter.cancelDiscovery()
        val device = adapter.getRemoteDevice(address)
        val socket = device.createInsecureRfcommSocketToServiceRecord(ElmProtocol.SPP_UUID)
        socket.connect()
        bluetoothSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
    }

    @Suppress("DEPRECATION")
    private fun resolveAdapter(): BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(BluetoothManager::class.java).adapter
        } else {
            BluetoothAdapter.getDefaultAdapter()
        }

    private suspend fun runInitSequence() {
        for (step in ElmProtocol.INIT_SEQUENCE) {
            val response = sendCommand(step.command)
            delay(step.delayMs)
            if (step.command !in ElmProtocol.COMMANDS_WITHOUT_OK) {
                if (!response.contains("OK", ignoreCase = true)) {
                    throw IOException("Init failed at '${step.command.trim()}': $response")
                }
            }
        }
    }

    override suspend fun sendCommand(cmd: String): String = ioMutex.withLock {
        withTimeout(3_000L) {
            withContext(Dispatchers.IO) {
                val out = outputStream ?: throw IOException("Output stream unavailable")
                out.write(cmd.toByteArray(Charsets.ISO_8859_1))
                out.flush()
                val buffer = StringBuilder()
                val bytes = ByteArray(1024)
                while (true) {
                    val inp = inputStream ?: throw IOException("Input stream unavailable")
                    val count = inp.read(bytes)
                    if (count == -1) throw IOException("Stream closed by remote")
                    buffer.append(String(bytes, 0, count, Charsets.ISO_8859_1))
                    val idx = buffer.indexOf(ElmProtocol.PROMPT_CHAR)
                    if (idx >= 0) return@withContext buffer.substring(0, idx).trim()
                }
                @Suppress("UNREACHABLE_CODE")
                ""
            }
        }
    }

    private suspend fun awaitDisconnect() = withContext(Dispatchers.IO) {
        while (isActive) {
            delay(5_000)
            if (bluetoothSocket?.isConnected == false) {
                throw IOException("Socket disconnected")
            }
        }
    }

    private fun closeSocket() {
        runCatching { bluetoothSocket?.close() }
        bluetoothSocket = null
        inputStream = null
        outputStream = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "OBD2 Connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("OnixGarage")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
