package com.axel.onixlauncher.logic

import com.axel.onixlauncher.EngineAlert
import com.axel.onixlauncher.VoiceClips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Voces grabadas (v0.61.0). */
class VoiceClipsTest {

    @Test
    fun todasLasFrasesEstanEnLaLista() {
        val file = listOf(File("../tools/voces/frases.txt"), File("tools/voces/frases.txt")).first { it.exists() }
        val keys = file.readLines().filter { !it.startsWith("#") && '|' in it }.map { it.substringBefore('|') }.toSet()
        val missing = VoiceClips.ALL.filter { it !in keys }
        assertTrue("faltan en frases.txt: $missing", missing.isEmpty())
        EngineAlert.entries.forEach { assertTrue(VoiceClips.alert(it) in keys) }
    }

    @Test
    fun maniobras() {
        assertEquals("girar_der", VoiceClips.maneuver("turn", "right", 0))
        assertEquals("leve_izq", VoiceClips.maneuver("continue", "slight left", 0))
        assertEquals("rotonda_2", VoiceClips.maneuver("roundabout", "right", 2))
        assertEquals("rotonda", VoiceClips.maneuver("rotary", "", 9))
        assertEquals("bifurcacion_izq", VoiceClips.maneuver("fork", "slight left", 0))
        assertEquals("llegaste", VoiceClips.maneuver("arrive", "", 0))
        assertEquals("derecho", VoiceClips.maneuver("new name", "straight", 0))
    }

    @Test
    fun avisoPrevioConDistancia() {
        assertEquals(listOf("en_200", "girar_izq"), VoiceClips.step("turn", "left", 0, 210.0))
        assertEquals(listOf("en_300", "girar_der"), VoiceClips.step("turn", "right", 0, 299.0))
        assertEquals(listOf("en_50", "giro_u"), VoiceClips.step("turn", "uturn", 0, 20.0))
        assertEquals(listOf("girar_der"), VoiceClips.step("turn", "right", 0, null))
    }

    @Test
    fun frasesConElLocutorAr() {
        // v0.72.2: indicaciones y avisos con la versión de ElevenLabs; el slogan y los avisos por voz quedan igual
        assertEquals("ia_girar_der", VoiceClips.aiName("girar_der"))
        assertEquals("ia_luces_apagadas", VoiceClips.aiName("luces_apagadas"))
        assertEquals("luces_ar_locutor", VoiceClips.aiName("luces_ar_locutor"))
        assertEquals("inicio_ar_locutor", VoiceClips.aiName("inicio_ar_locutor"))
    }
}
