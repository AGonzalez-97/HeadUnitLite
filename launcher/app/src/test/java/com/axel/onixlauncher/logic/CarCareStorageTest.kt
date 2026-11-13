package com.axel.onixlauncher.logic

import android.content.Context
import com.axel.onixlauncher.CarCare
import com.axel.onixlauncher.Monthly
import com.axel.onixlauncher.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AUD-101 (v0.79.5): si lo guardado de Mi auto no se puede leer, antes se mostraba vacío y el
 * próximo viaje lo pisaba (se perdía el historial). Ahora se guarda una copia antes de seguir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarCareStorageTest {

    private fun prefs() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("mi_auto_prueba", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @Test
    fun historial_danado_se_respalda_antes_de_pisarlo() {
        val p = prefs()
        p.edit().putString("viajes", "[{\"inicio\": 1, roto").commit()
        val trips = CarCare.readOrBackup(p, "viajes", emptyList(), nowMs = 1_000L) { CarCare.parseTrips(it) }
        assertEquals(emptyList<TripSummary>(), trips)
        assertEquals("la copia guarda el texto tal cual", "[{\"inicio\": 1, roto", p.getString("viajes_respaldo_1000", null))
    }

    @Test
    fun meses_danados_tambien_se_respaldan() {
        val p = prefs()
        p.edit().putString("meses", "{\"2026-11\": roto").commit()
        val months = CarCare.readOrBackup(p, "meses", emptyMap(), nowMs = 2_000L) { Monthly.parse(it) }
        assertEquals(emptyMap<String, Any>(), months)
        assertEquals("{\"2026-11\": roto", p.getString("meses_respaldo_2000", null))
    }

    @Test
    fun historial_sano_se_lee_y_no_se_respalda() {
        val p = prefs()
        val trip = TripSummary(1_700_000_000_000L, 600, 12.5f, 90, 60, 88, 1.1f)
        p.edit().putString("viajes", org.json.JSONArray(listOf(trip.toJson())).toString()).commit()
        val trips = CarCare.readOrBackup(p, "viajes", emptyList(), nowMs = 3_000L) { CarCare.parseTrips(it) }
        assertEquals(1, trips.size)
        assertEquals(12.5f, trips[0].km, 0.01f)
        assertFalse(p.all.keys.any { it.contains("respaldo") })
    }

    @Test
    fun sin_nada_guardado_devuelve_el_valor_vacio() {
        val p = prefs()
        assertEquals(emptyList<TripSummary>(), CarCare.readOrBackup(p, "viajes", emptyList(), nowMs = 4_000L) { CarCare.parseTrips(it) })
        assertNull(p.getString("viajes_respaldo_4000", null))
    }
}
