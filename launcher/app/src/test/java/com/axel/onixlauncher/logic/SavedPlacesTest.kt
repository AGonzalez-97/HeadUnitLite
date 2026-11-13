package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Place
import com.axel.onixlauncher.SavedPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lugares guardados del mapa: Casa, Trabajo, favoritos y recientes. */
class SavedPlacesTest {

    @Test
    fun ida_y_vuelta_de_un_lugar() {
        val p = Place("Plaza Independencia | Centro", -26.830312, -65.203845)
        val back = SavedPlaces.decode(SavedPlaces.encode(p))!!
        assertEquals("Plaza Independencia / Centro", back.name)
        assertEquals(-26.830312, back.lat, 1e-6)
        assertNull(SavedPlaces.decode("roto"))
        assertNull(SavedPlaces.decode(null))
    }

    @Test
    fun recientes_sin_repetir_y_hasta_diez() {
        var list = emptyList<Place>()
        for (i in 0 until 12) list = SavedPlaces.pushRecent(list, Place("L$i", -26.0 - i, -65.0))
        assertEquals(10, list.size)
        assertEquals("L11", list[0].name)
        list = SavedPlaces.pushRecent(list, Place("L5 otra vez", -31.0, -65.0))
        assertEquals("L5 otra vez", list[0].name)
        assertEquals(10, list.size)
        assertEquals(1, list.count { Math.abs(it.lat + 31.0) < 1e-9 })
    }

    @Test
    fun distancia_y_cercania_entre_lugares() {
        val casa = Place("Casa · 9 de Julio 550", -26.7368, -65.258)
        val destinoAlLado = Place("9 de Julio 560", -26.7369, -65.2581) // ~15-20 m
        val lejos = Place("Centro", -26.8303, -65.2038) // ~12 km

        assertTrue(SavedPlaces.isNear(casa, casa, 100.0))
        assertTrue(SavedPlaces.isNear(casa, destinoAlLado, 100.0))
        assertFalse(SavedPlaces.isNear(casa, lejos, 100.0))
        assertFalse(SavedPlaces.isNear(casa, null, 100.0))
        assertFalse(SavedPlaces.isNear(null, lejos, 100.0))
        assertEquals(Double.MAX_VALUE, SavedPlaces.distance(casa, null), 0.0)
    }
}
