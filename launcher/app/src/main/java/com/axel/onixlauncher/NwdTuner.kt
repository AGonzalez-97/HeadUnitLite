package com.axel.onixlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/*
 * Radio FM por la antena del auto (v0.51.0), con el sintonizador de la radio NWD.
 *
 * La radio de fábrica tiene un servicio (`com.nwd.radio.service`) que maneja el chip de FM. Se le
 * habla por Binder con los códigos de su interfaz `com.nwd.radio.service.RadioFeature`. Los datos
 * de esa interfaz (acción, paquete, códigos, formato de los mensajes, ids de fuente de audio)
 * salen de CleanLauncher, un launcher para la misma radio; la implementación es propia.
 *
 * El audio del FM no pasa por Android: lo enruta el microcontrolador de la radio. Para que suene,
 * se le pide la fuente "radio" (id 4) y se despierta el chip; para volver a Spotify o a la radio
 * por internet, se pide la fuente "música de Android" (id −128).
 */

/** Cálculos del sintonizador (funciones puras, para los tests). */
object TunerMath {
    const val F_MIN = 87.5f
    const val F_MAX = 108.0f

    /** MHz → como lo pide el servicio (MHz × 100: 95.5 → 9550). Ajusta al rango de FM y a pasos de 0,1. */
    fun toService(mhz: Float): Int = (Math.round(mhz.coerceIn(F_MIN, F_MAX) * 10f) * 10)

    /** Como lo da el servicio → MHz (9550 → 95.5). */
    fun toMhz(value: Int): Float = Math.round(value / 10f) / 10f

    /** Banda FM (0 a 2 = FM1 a FM3, 6 a 8 = FML1 a FML3); 3 a 5 son AM. */
    fun isFm(band: Int): Boolean = band in 0..2 || band in 6..8

    /** "95.5" para mostrar. */
    fun label(mhz: Float): String = String.format(Locale.US, "%.1f", mhz)

    /** Si la fuente de audio actual (`mcu_current_source`) es la radio. */
    fun isRadioSource(source: Int): Boolean = source == SOURCE_RADIO

    const val SOURCE_RADIO = 4
    const val SOURCE_ANDROID = -128
}

/** Lo que el servicio informa: banda, frecuencia (MHz) y nombre RDS de la emisora (puede venir vacío). */
class TunerStation(val band: Int, val mhz: Float, val rds: String)

object NwdTuner {
    private const val PACKAGE = "com.nwd.radio.service"
    private const val ACTION_SERVICE = "com.nwd.radio.service.ACTION_RADIO_SERVICE"
    private const val DESCRIPTOR = "com.nwd.radio.service.RadioFeature"
    private const val CALLBACK = "com.nwd.radio.service.RadioCallback"
    private const val TX_SET_FREQUENCY = 1
    private const val TX_GET_FREQUENCY = 2
    private const val TX_SEEK = 3
    private const val TX_REGISTER = 24
    private const val TX_UNREGISTER = 25

    /** Si el servicio de la radio NWD está conectado (en el celular u otro equipo: false). */
    val available = mutableStateOf(false)
    /** Modo antena elegido en la pantalla de radio (se guarda). */
    val antenna = mutableStateOf(false)
    /** Lo último que informó el servicio. */
    val station = mutableStateOf<TunerStation?>(null)
    /** Para Diagnóstico y el informe: qué pasó con la última orden. */
    val lastResult = mutableStateOf("sin usar")

    private var binder: IBinder? = null
    private var bound = false
    private val main = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            available.value = service != null
            AppLog.info("Radio FM: servicio de la radio NWD conectado")
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            available.value = false
        }
    }

    /** Se conecta al servicio de la radio (una vez). Lee también el modo guardado. */
    fun bind(context: Context) {
        antenna.value = context.getSharedPreferences("radio", Context.MODE_PRIVATE).getBoolean("antena", false)
        if (bound) return
        bound = try {
            context.applicationContext.bindService(
                Intent(ACTION_SERVICE).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            false
        }
        if (!bound) lastResult.value = "esta radio no tiene el sintonizador NWD"
    }

    fun setAntenna(context: Context, on: Boolean) {
        antenna.value = on
        context.getSharedPreferences("radio", Context.MODE_PRIVATE).edit().putBoolean("antena", on).apply()
    }

    /** Si la radio está sonando ahora (la fuente de audio de la radio es el FM). */
    fun isSource(context: Context): Boolean =
        TunerMath.isRadioSource(Settings.System.getInt(context.contentResolver, "mcu_current_source", 0))

    /** Pone el FM como fuente de audio y despierta el chip (lo prende y le saca el silencio). */
    fun select(context: Context) {
        requestSource(context, TunerMath.SOURCE_RADIO)
        main.postDelayed({ wake(context) }, 800)
    }

    /** Vuelve el audio a Android (Spotify, radio por internet). */
    fun release(context: Context) {
        requestSource(context, TunerMath.SOURCE_ANDROID)
    }

    /** Conmuta a radio FM y vuelve a Android para destrabar el audio del MCU al dar contacto. */
    fun bounceToAndroid(context: Context) {
        val mhz = station.value?.mhz ?: 95.5f
        select(context)
        transact(TX_SET_FREQUENCY) { data ->
            data.writeInt(TunerMath.toService(mhz))
            data.writeByte(0)
            data.writeInt(-1)
        }?.recycle()
        main.postDelayed({ release(context) }, 1_800L)
    }

    /** Sintoniza [mhz] en FM1 y la pone a sonar. */
    fun tune(context: Context, mhz: Float) {
        if (!isSource(context)) select(context)
        val ok = transact(TX_SET_FREQUENCY) { data ->
            data.writeInt(TunerMath.toService(mhz))
            data.writeByte(0)                 // FM1
            data.writeInt(-1)                 // sin lugar de presintonía (no pisa las de la radio)
        }?.also { it.recycle() } != null
        lastResult.value = if (ok) "sintonizada ${TunerMath.label(mhz)}" else "no se pudo sintonizar ${TunerMath.label(mhz)}"
        main.postDelayed({ refresh() }, 400)
    }

    /** Busca la siguiente emisora con señal hacia arriba o abajo. */
    fun seek(context: Context, up: Boolean) {
        if (!isSource(context)) select(context)
        val ok = transact(TX_SEEK) { it.writeInt(if (up) 1 else 0) }?.also { it.recycle() } != null
        lastResult.value = if (ok) "buscando ${if (up) "arriba" else "abajo"}" else "no se pudo buscar"
        // La búsqueda tarda: se lee varias veces
        for (ms in listOf(800L, 2000L, 4000L)) main.postDelayed({ refresh() }, ms)
    }

    /** Lee la emisora actual del servicio. */
    fun refresh(): TunerStation? {
        val s = current()
        if (s != null) station.value = s
        return s
    }

    private fun current(): TunerStation? = transact(TX_GET_FREQUENCY) { } ?.let { reply ->
        try {
            if (reply.readInt() == 0) return@let null
            val band = reply.readByte().toInt()
            val rds = reply.readString().orEmpty().trim()
            val value = reply.readInt()
            TunerStation(band, TunerMath.toMhz(value), rds)
        } catch (e: Exception) {
            null
        } finally {
            reply.recycle()
        }
    }

    /**
     * Despierta el chip: el servicio lo prende y le saca el silencio cuando recibe el cambio de
     * fuente a radio, cuando "la app de radio pasa al frente" y cuando alguien registra un aviso.
     */
    private fun wake(context: Context) {
        context.sendBroadcast(Intent("com.nwd.action.ACTION_CHANGE_SOURCE").setPackage(PACKAGE).putExtra("extra_source_id", TunerMath.SOURCE_RADIO.toByte()))
        context.sendBroadcast(Intent("com.nwd.action.ACTION_APP_IN_OUT").setPackage(PACKAGE).putExtra("extra_app_id", 8))
        val callback = object : android.os.Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = true
        }
        callback.attachInterface(null, CALLBACK)
        for (code in listOf(TX_REGISTER, TX_UNREGISTER)) transact(code) { it.writeStrongBinder(callback) }?.recycle()
        main.postDelayed({ refresh() }, 600)
    }

    private fun requestSource(context: Context, id: Int) {
        context.sendBroadcast(Intent("com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE").putExtra("extra_source_id", id.toByte()))
    }

    /** Manda una orden al servicio. Devuelve la respuesta (hay que reciclarla) o null si falló. */
    private fun transact(code: Int, write: (Parcel) -> Unit): Parcel? {
        val b = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            b.transact(code, data, reply, 0)
            reply.readException()
            reply
        } catch (e: Exception) {
            AppLog.warn("Radio FM: falló la orden $code", e)
            reply.recycle()
            null
        } finally {
            data.recycle()
        }
    }
}
