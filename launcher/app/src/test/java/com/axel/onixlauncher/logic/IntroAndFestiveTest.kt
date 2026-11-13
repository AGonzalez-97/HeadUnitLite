package com.axel.onixlauncher.logic

import com.axel.onixlauncher.Festive
import com.axel.onixlauncher.Holiday
import com.axel.onixlauncher.IntroChime
import com.axel.onixlauncher.IntroSound
import com.axel.onixlauncher.IntroVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Sonido de inicio (v0.65.0) y temas festivos de Argentina (v0.66.0). */
class IntroAndFestiveTest {
    @Test
    fun sonidoDeInicioSinSaturarNiCortes() {
        val s = IntroChime.samples(22_050)
        assertEquals((22_050 * IntroChime.TOTAL_S).toInt(), s.size)
        val peak = s.maxOf { abs(it.toInt()) }
        assertTrue("pico $peak", peak in 8_000..(0.55 * Short.MAX_VALUE).toInt())
        assertEquals(0, s.first().toInt())
        assertTrue("termina en silencio", s.takeLast(200).all { abs(it.toInt()) < peak / 50 })
    }

    /** v0.81.15: sonido de inicio nuevo, que entra suave (sin golpe) como en los autos actuales. */
    @Test
    fun sonidoDeInicioEntraSuave() {
        val rate = 22_050
        val s = IntroChime.samples(rate)
        assertEquals(2.6, IntroChime.TOTAL_S, 0.001)
        val peak = s.maxOf { abs(it.toInt()) }
        val first = s.take(rate / 20).maxOf { abs(it.toInt()) }
        assertTrue("los primeros 50 ms ($first) son suaves frente al pico ($peak)", first < peak / 4)
    }

    /** v0.81.15: sin elección guardada suena solo el sonido; la voz del slogan queda como opción. */
    @Test
    fun porDefectoSoloElSonido() {
        assertEquals(IntroSound.Mode.CHIME, IntroSound.savedMode(null))
        assertEquals(IntroSound.Mode.CHIME, IntroSound.savedMode("otra cosa"))
        assertEquals(IntroSound.Mode.VOICE, IntroSound.savedMode("VOICE"))
        assertEquals(IntroSound.Mode.OFF, IntroSound.savedMode("OFF"))
    }

    @Test
    fun opcionesDelSonido() {
        assertEquals(IntroSound.Mode.VOICE, IntroSound.next(IntroSound.Mode.OFF))
        assertEquals(IntroSound.Mode.CHIME, IntroSound.next(IntroSound.Mode.VOICE))
        assertEquals(IntroSound.Mode.OFF, IntroSound.next(IntroSound.Mode.CHIME))
    }

    @Test
    fun fechasFijas() {
        assertEquals(Holiday.NAVIDAD, Festive.on(2026, 12, 24))
        assertEquals(Holiday.ANIO_NUEVO, Festive.on(2026, 12, 31))
        assertEquals(Holiday.ANIO_NUEVO, Festive.on(2027, 1, 1))
        assertEquals(Holiday.PATRIA_MAYO, Festive.on(2026, 5, 25))
        assertEquals(Holiday.INDEPENDENCIA, Festive.on(2026, 7, 9))
        assertEquals(Holiday.HALLOWEEN, Festive.on(2026, 10, 31))
        assertEquals(Holiday.MALVINAS, Festive.on(2026, 4, 2))
        assertNull(Festive.on(2026, 3, 10))
    }

    @Test
    fun fechasQueCambianCadaAnio() {
        // Pascua 2026: 5 de abril; Carnaval: lunes y martes antes del Miércoles de Ceniza (16 y 17 de febrero)
        assertEquals(Holiday.PASCUA, Festive.on(2026, 4, 5))
        assertEquals(Holiday.CARNAVAL, Festive.on(2026, 2, 16))
        assertEquals(Holiday.CARNAVAL, Festive.on(2026, 2, 17))
        // Día del Padre: tercer domingo de junio (21/6/2026); Día de la Madre: tercer domingo de octubre (18/10/2026)
        assertEquals(Holiday.DIA_DEL_PADRE, Festive.on(2026, 6, 21))
        assertEquals(Holiday.DIA_DE_LA_MADRE, Festive.on(2026, 10, 18))
        assertEquals(3 to 28, Festive.easter(2027))   // (mes, día); sin java.time, que no existe en Android 7
    }

    /** v0.67.0: las luces y adornos se reparten parejos a lo largo de un contorno del auto. */
    @Test
    fun repartirALoLargoDeUnContorno() {
        val line = listOf(0f to 0f, 1f to 0f, 1f to 1f)          // dos tramos de largo 1
        val pts = Festive.along(line, 5)
        assertEquals(5, pts.size)
        assertEquals(0f to 0f, pts.first())
        assertEquals(1f to 1f, pts.last())
        assertEquals(1f to 0f, pts[2])                           // la mitad del recorrido cae en la esquina
        assertEquals(listOf(0.5f to 0.5f), Festive.along(listOf(0.5f to 0.5f), 1))
    }

    @Test
    fun vocesDelInicioParaElegir() {
        assertEquals("inicio", IntroVoices.clip("original"))
        assertEquals("inicio_locutor", IntroVoices.clip("locutor"))
        // Solo las que trae el APK, en el orden de la pantalla
        val files = listOf("inicio.ogg", "inicio_epico.ogg", "inicio_locutor.ogg", "giro_der.ogg", "LEEME_inicio.txt")
        assertEquals(listOf("locutor", "epico", "original"), IntroVoices.available(files))
        // Elegida que sigue estando; si no, el locutor; si no hay locutor, la original
        assertEquals("epico", IntroVoices.resolve("epico", listOf("locutor", "epico", "original")))
        assertEquals("locutor", IntroVoices.resolve("britanico", listOf("locutor", "original")))
        assertEquals("locutor", IntroVoices.resolve(null, listOf("locutor", "original")))
        assertEquals("original", IntroVoices.resolve(null, listOf("original")))
        // v0.70.0: con las voces de IA, por defecto el locutor argentino
        assertEquals("ar_locutor", IntroVoices.resolve(null, listOf("ar_locutor", "locutor", "original")))
        assertEquals(listOf("ar_locutor", "asistente", "original"),
            IntroVoices.available(listOf("inicio.ogg", "inicio_asistente.ogg", "inicio_ar_locutor.ogg")))
    }

    @Test
    fun vozComercialPorDefectoYAvisoDeLucesConLaVozElegida() {
        // v0.72.3: la Comercial va primero y es la de por defecto (elegida por el autor); después el Locutor AR
        val all = listOf("comercial", "ar_locutor", "locutor", "original")
        assertEquals("comercial", IntroVoices.resolve(null, all))
        assertEquals("ar_locutor", IntroVoices.resolve(null, listOf("ar_locutor", "locutor", "original")))
        assertEquals("comercial", IntroVoices.available(listOf("inicio_ar_locutor.ogg", "inicio_comercial.ogg")).first())
        // Aviso de luces: con la voz elegida si tiene su frase; si no, la de siempre
        val files = listOf("luces_apagadas.ogg", "luces_comercial.ogg", "inicio_comercial.ogg")
        assertEquals("luces_comercial", IntroVoices.lightsClip("comercial", files))
        assertEquals("luces_apagadas", IntroVoices.lightsClip("locutor", files))
    }
}
