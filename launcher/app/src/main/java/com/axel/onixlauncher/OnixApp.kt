package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.HostIntegration
import com.carlink.core.connection.CarLinkConfig
import com.carlink.core.connection.CarLinkCore
import com.carlink.core.connection.CarLinkHost
import com.carlink.core.core.PhoneKind
import com.shilapi.xcertplay.CarPlayHostIntegration

/*
 * Clase Application de Onix (v0.56.0, ADR-0012). Tiene que heredar de la `App` del motor de
 * Android Auto (DiAuto): el motor convierte el contexto de la app a esa clase en muchos lugares.
 * Corre en todos los procesos de la app; el motor de Android Auto vive en el proceso ":aa".
 * Integración según vendor/CARLINK_INTEGRATION.md (CarLink, AGPL-3.0).
 */
class OnixApp : App() {

    private val isAndroidAutoProcess: Boolean by lazy { CarLinkCore.isAndroidAutoProcess(this) }

    override fun initializesAndroidAutoEngine(): Boolean = isAndroidAutoProcess

    override fun onCreate() {
        // Antes de App.onCreate: crea los canales de notificación del motor con estos nombres
        HostIntegration.homeActivity = MainActivity::class.java
        HostIntegration.appName = NAME
        HostIntegration.mediaSessionTag = "Onix Android Auto"
        HostIntegration.toastHandler = { _, text, forced ->
            Log.i(TAG, "Android Auto: $text")
            !forced
        }
        CarPlayHostIntegration.homeActivity = MainActivity::class.java
        CarPlayHostIntegration.appName = NAME
        CarPlayHostIntegration.mediaSessionTag = "Onix CarPlay"
        CarPlayHostIntegration.toastHandler = { _, text ->
            Log.i(TAG, "CarPlay: $text")
            EngineTrace.add("CarPlay avisa: $text")
            true
        }
        super.onCreate()
        // El motor: detección del celular por USB, sesiones y registro (en todos los procesos)
        CarLinkCore.install(this, CarLinkConfig(accessoryName = NAME, host = OnixHost))
        // v0.79.1: los pasos de CarPlay para la Prueba completa (CarPlay corre en el proceso principal)
        if (!isAndroidAutoProcess) {
            runCatching { EngineTrace.install() }
            runCatching { CrashLog.install(this) }
        }
    }

    private companion object {
        /** Cómo ve el celular al auto. */
        const val NAME = "Onix"
        const val TAG = "OnixProjection"
    }
}

/** Lo que el motor le pide a Onix: volver a la pantalla de inicio (por ejemplo, al desenchufar). */
object OnixHost : CarLinkHost {
    override fun openHome(context: Context, reason: CarLinkHost.OpenReason, address: String?, kind: PhoneKind?, name: String?) {
        // Android 7.1 deja abrir la pantalla desde segundo plano; Onix ya es la pantalla de inicio
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
        }.onFailure { Log.w("OnixProjection", "No se pudo volver a Inicio ($reason): $it") }
    }
}
