package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Builtin
import com.axel.onixlauncher.DockSlot
import com.axel.onixlauncher.DockStore
import com.axel.onixlauncher.Projection
import com.carlink.core.core.Engine
import com.carlink.core.core.LinkProblem
import com.carlink.core.core.LinkStage
import com.carlink.core.core.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auto / CarPlay (v0.54.0): qué app abre el botón del dock. */
class ProjectionTest {

    @Test
    fun prefiereCarLink() {
        val apps = mapOf("com.zjinnova.zlink" to "ZLink", "com.carlink.app" to "CarLink", "com.spotify.music" to "Spotify")
        assertEquals("com.carlink.app", Projection.find(apps))
    }

    @Test
    fun reconoceOtrasPorElNombre() {
        assertEquals("com.fabrica.cp", Projection.find(mapOf("com.fabrica.cp" to "CarPlay", "com.spotify.music" to "Spotify")))
        assertEquals("com.xyz.autokit", Projection.find(mapOf("com.xyz.autokit" to "Conexión")))
    }

    @Test
    fun estadoEnPalabras() {
        assertEquals("Enchufá el celular por USB", Projection.status(LinkState()).first)
        assertEquals("Conectando CarPlay con iPhone de Axel…",
            Projection.status(LinkState(LinkStage.CONNECTING, Engine.CARPLAY, phoneName = "iPhone de Axel")).first)
        assertEquals("Paso 2 de 5", Projection.status(LinkState(LinkStage.CONNECTING, Engine.CARPLAY, step = 2, stepCount = 5)).second)
        assertEquals("A CarPlay le falta su identidad: cargala en GitHub (docs/CARPLAY.md)",
            Projection.status(LinkState(LinkStage.FAILED, Engine.CARPLAY, problem = LinkProblem.CARPLAY_AUTH_MISSING)).second)
    }

    @Test
    fun seAbreSolaSiHayQueAutorizarAlgo() {
        assertTrue(Projection.needsDriver(LinkState(LinkStage.FAILED, problem = LinkProblem.PERMISSIONS)))
        assertTrue(Projection.needsDriver(LinkState(LinkStage.FAILED, Engine.CARPLAY, problem = LinkProblem.VPN_CONSENT)))
        assertFalse(Projection.needsDriver(LinkState(LinkStage.FAILED, problem = LinkProblem.USB)))
        assertFalse(Projection.needsDriver(LinkState(LinkStage.CONNECTING)))
    }

    @Test
    fun noSeCuentaAOnixComoAppDeProyeccion() {
        assertNull(Projection.find(mapOf("com.axel.onixlauncher" to "Onix Launcher · CarPlay")))
    }

    @Test
    fun sinAppDeProyeccion() {
        assertNull(Projection.find(mapOf("com.spotify.music" to "Spotify", "com.waze" to "Waze")))
        assertNull(Projection.find(emptyMap()))
    }

    @Test
    fun autoCarPlayReemplazaAAjustesEnElDock() {
        val old = listOf(Builtin.NAV, Builtin.MUSIC, Builtin.RADIO, Builtin.PHONE, Builtin.APPS, Builtin.CAR, Builtin.SETTINGS).map { DockSlot.Own(it) }
        val migrated = DockStore.withProjection(old)
        assertEquals(DockSlot.Own(Builtin.PROJECTION), migrated[6])
        assertEquals(migrated, DockStore.withProjection(migrated))
        assertEquals(DockSlot.Own(Builtin.PROJECTION), DockStore.defaults[6])
    }

    @Test
    fun volver_a_la_pantalla_de_cada_motor() {
        assertEquals("com.andrerinas.openheadunit.aap.AapProjectionActivity", Projection.screenClass(Engine.ANDROID_AUTO))
        assertEquals("com.shilapi.xcertplay.CarPlayHostActivity", Projection.screenClass(Engine.CARPLAY))
        assertNull(Projection.screenClass(null))
    }

    /**
     * AUD-081 (v0.79.5): los nombres de las pantallas de los motores están escritos a mano. Si una
     * actualización de CarLink los cambia, "Volver a Android Auto / CarPlay" deja de andar: este
     * test lo detecta al compilar, sin la radio. No inicializa las clases.
     */
    @Test
    fun las_pantallas_de_los_motores_existen() {
        for (engine in Engine.entries) {
            val name = Projection.screenClass(engine)!!
            val found = runCatching { Class.forName(name, false, javaClass.classLoader) }
            assertTrue("$engine: no existe $name en los motores", found.isSuccess)
        }
    }
}
