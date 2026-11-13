package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Guidance
import com.axel.onixlauncher.Route
import com.axel.onixlauncher.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Navegación giro a giro propia: distancias, textos, avisos y desvíos. */
class GuidanceTest {

    @Test
    fun distancias_e_indicaciones() {
        // 0,001° de longitud a la latitud de Tucumán ≈ 99,5 m
        assertEquals(99.5, Guidance.distance(-26.83, -65.200, -26.83, -65.199), 1.0)
        assertEquals("300 metros", Guidance.roundDistance(310.0))
        assertEquals("1,5 kilómetros", Guidance.roundDistance(1500.0))
        assertEquals("En la rotonda, tomá la 2.ª salida hacia Ruta 9", Guidance.instruction("roundabout", "right", "Ruta 9", 2))
        assertEquals("Doblá levemente a la izquierda en Laprida", Guidance.instruction("turn", "slight left", "Laprida", 0))
        assertEquals("Seguí por Av. Roca", Guidance.instruction("new name", "straight", "Av. Roca", 0))
    }

    @Test
    fun avisa_a_300_metros_y_al_llegar_a_la_maniobra() {
        // Ruta recta hacia el este de ~1 km con un giro a la derecha a ~500 m
        val pts = (0..10).map { -26.83 to -65.20 + it * 0.001 }
        val steps = listOf(
            Step("depart", "", "Av. Sarmiento", 0, -26.83, -65.20, 500.0),
            Step("turn", "right", "Laprida", 0, -26.83, -65.195, 500.0),
            Step("arrive", "", "", 0, -26.83, -65.19, 0.0)
        )
        val g = Guidance(Route(1000.0, 120.0, pts, steps))
        val lejos = g.update(-26.83, -65.20)
        assertEquals("Laprida", lejos.step!!.name)
        val cerca = g.update(-26.83, -65.1975)   // ~250 m antes del giro
        assertNotNull(cerca.speak)
        assertTrue(cerca.speak!!.startsWith("En 250 metros, girá a la derecha"))
        val ahora = g.update(-26.83, -65.1955)   // ~50 m
        assertEquals("Girá a la derecha en Laprida", ahora.speak)
        val pasada = g.update(-26.83, -65.19499)
        assertEquals("arrive", pasada.step!!.type)
        assertFalse(pasada.offRoute)
    }

    @Test
    fun detecta_que_se_salio_del_camino() {
        val pts = (0..10).map { -26.83 to -65.20 + it * 0.001 }
        val g = Guidance(Route(1000.0, 120.0, pts, listOf(Step("depart", "", "", 0, -26.83, -65.2, 1000.0), Step("arrive", "", "", 0, -26.83, -65.19, 0.0))))
        repeat(2) { assertFalse(g.update(-26.832, -65.195).offRoute) }   // ~220 m al sur
        assertTrue(g.update(-26.832, -65.195).offRoute)
    }
}
