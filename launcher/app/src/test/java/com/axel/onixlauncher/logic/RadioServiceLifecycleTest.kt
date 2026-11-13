package com.axel.onixlauncher.logic

import com.axel.onixlauncher.RadioService
import com.axel.onixlauncher.RadioState
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * AUD-100 (v0.79.6): si el servicio de la radio se destruye (lo para el sistema o Media3, sin pasar
 * por la orden STOP), una reconexión o una búsqueda que quedó pendiente no tiene que arrancar el
 * reproductor ya liberado. Se levanta el servicio de verdad (Robolectric) y se lo deja en el estado
 * de "sonaba y se cortó", que no se puede armar sin un reproductor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RadioServiceLifecycleTest {

    @Before
    fun setUp() = clearState()

    @After
    fun tearDown() = clearState()

    private fun clearState() {
        RadioState.current.value = null
        RadioState.playing.value = false
        RadioState.buffering.value = false
    }

    private fun field(svc: RadioService, name: String) =
        RadioService::class.java.getDeclaredField(name).apply { isAccessible = true }.let { f -> f to f.get(svc) }

    private fun set(svc: RadioService, name: String, value: Any) =
        RadioService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(svc, value)

    @Test
    fun una_reconexion_pendiente_no_corre_despues_de_destruir_el_servicio() {
        val controller = Robolectric.buildService(RadioService::class.java).create()
        val svc = controller.get()
        // "Sonaba y se cortó": reconectar a la misma señal dentro de 1 s
        set(svc, "currentName", "Radio de Prueba")
        set(svc, "currentUrl", "http://127.0.0.1:9/audio")
        set(svc, "playedOk", true)
        RadioService::class.java.getDeclaredMethod("reconnectOrNext", String::class.java)
            .apply { isAccessible = true }.invoke(svc, "la transmisión se cortó")
        RadioState.current.value = null   // startDirect es quien pone la emisora: si corre, se nota

        controller.destroy()
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)

        assertNull("el reintento pendiente no tiene que arrancar nada", RadioState.current.value)
    }

    @Test
    fun destruir_el_servicio_invalida_las_busquedas_en_curso() {
        val controller = Robolectric.buildService(RadioService::class.java).create()
        val svc = controller.get()
        val before = field(svc, "generation").second as Int
        controller.destroy()
        val after = field(svc, "generation").second as Int
        assertTrue("la respuesta de una búsqueda vieja se descarta (generación $before -> $after)", after > before)
    }
}
