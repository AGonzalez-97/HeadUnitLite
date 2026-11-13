package com.axel.onixlauncher.logic

import androidx.compose.ui.graphics.Color
import com.axel.onixlauncher.Accent
import com.axel.onixlauncher.AccentPalette
import com.axel.onixlauncher.Ambient
import com.axel.onixlauncher.CrashLog
import com.axel.onixlauncher.HomeDefault
import com.axel.onixlauncher.SettingsCategory
import com.axel.onixlauncher.SettingsNav
import com.axel.onixlauncher.TextMain
import com.axel.onixlauncher.ThemeMode
import com.axel.onixlauncher.ThemePrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ajustes por categorías, tema oscuro/claro/automático, color de acento y registro de fallos (v0.80.0). */
class SettingsAndThemeTest {

    @After
    fun restaurar() {
        ThemePrefs.resetForTests()
        SettingsNav.close()
    }

    @Test
    fun abrir_y_cerrar_ajustes_en_la_categoria_pedida() {
        assertFalse(SettingsNav.open.value)
        SettingsNav.show(SettingsCategory.SOUND)
        assertTrue(SettingsNav.open.value)
        assertEquals(SettingsCategory.SOUND, SettingsNav.category.value)
        SettingsNav.close()
        assertFalse(SettingsNav.open.value)
    }

    @Test
    fun ajustes_tiene_cinco_categorias_y_diagnostico_es_una_de_ellas() {
        // v0.81.11: Diagnóstico dejó de ser una pantalla aparte
        assertEquals(
            listOf("Apariencia", "Sonido y avisos", "Auto y conexiones", "Sistema", "Diagnóstico"),
            SettingsCategory.entries.map { it.label }
        )
        assertFalse(SettingsNav.showsDiagnostics())
        SettingsNav.show(SettingsCategory.DIAG)
        assertTrue(SettingsNav.showsDiagnostics())
        SettingsNav.show(SettingsCategory.SYSTEM)
        assertFalse(SettingsNav.showsDiagnostics())
        SettingsNav.show(SettingsCategory.DIAG)
        SettingsNav.close()
        assertFalse(SettingsNav.showsDiagnostics())
    }

    @Test
    fun el_estado_de_la_pantalla_de_inicio_dice_quien_atiende_el_boton_inicio() {
        val own = "com.axel.onixlauncher"
        assertEquals(HomeDefault.State.NOT_OFFERED, HomeDefault.state(resolved = own, own = own, aliasEnabled = false))
        assertEquals(HomeDefault.State.ONIX, HomeDefault.state(resolved = own, own = own, aliasEnabled = true))
        // "android" es la ventana de Android que pregunta con qué app abrir: nadie quedó elegido
        assertEquals(HomeDefault.State.NOT_CHOSEN, HomeDefault.state(resolved = "android", own = own, aliasEnabled = true))
        assertEquals(HomeDefault.State.NOT_CHOSEN, HomeDefault.state(resolved = null, own = own, aliasEnabled = true))
        assertEquals(HomeDefault.State.OTHER, HomeDefault.state(resolved = "com.nwd.launcher", own = own, aliasEnabled = true))
        assertEquals("Onix es la pantalla de inicio.", HomeDefault.describe(HomeDefault.State.ONIX, own))
        assertTrue("com.nwd.launcher" in HomeDefault.describe(HomeDefault.State.OTHER, "com.nwd.launcher"))
    }

    @Test
    fun modo_automatico_usa_claro_solo_de_dia_y_con_luces_apagadas() {
        assertFalse(ThemePrefs.resolveLight(ThemeMode.DARK, lightsOn = false, ambient = Ambient.DIA))
        assertTrue(ThemePrefs.resolveLight(ThemeMode.LIGHT, lightsOn = true, ambient = Ambient.NOCHE))

        // Automático: de día y sin luces del auto prendidas -> modo claro
        assertTrue(ThemePrefs.resolveLight(ThemeMode.AUTO, lightsOn = false, ambient = Ambient.DIA))
        // Si prende los faros (cable ILL), pasa a oscuro aunque sea de día
        assertFalse(ThemePrefs.resolveLight(ThemeMode.AUTO, lightsOn = true, ambient = Ambient.DIA))
        // Al atardecer o de noche, pasa a oscuro aunque no haya prendido las luces
        assertFalse(ThemePrefs.resolveLight(ThemeMode.AUTO, lightsOn = false, ambient = Ambient.ATARDECER))
        assertFalse(ThemePrefs.resolveLight(ThemeMode.AUTO, lightsOn = false, ambient = Ambient.NOCHE))
    }

    @Test
    fun cambiar_tema_y_color_de_acento_actualiza_la_paleta_en_vivo() {
        ThemePrefs.resetForTests()
        assertEquals(Color(0xFF4C8DFF), Accent)
        assertEquals(Color(0xFFF2F4F8), TextMain)

        ThemePrefs.applyState(ThemeMode.LIGHT, AccentPalette.AMBER, lightsOn = false, ambient = Ambient.DIA)
        assertTrue(ThemePrefs.isLight.value)
        assertEquals(AccentPalette.AMBER.color, Accent)
        assertEquals(Color(0xFF0F172A), TextMain)
    }

    @Test
    fun registro_de_fallos_formatea_el_error_con_primera_linea_del_stack() {
        val ex = IllegalStateException("fallo de prueba")
        val text = CrashLog.format(ex, "2026-11-08 19:00")
        assertTrue(text.contains("2026-11-08 19:00"))
        assertTrue(text.contains("IllegalStateException: fallo de prueba"))
    }

    @Test
    fun actualizador_lee_release_de_github_y_compara_versiones() {
        val json = """{
            "tag_name": "build-166",
            "name": "Onix Launcher v0.81.0 (build 166)",
            "assets": [
                {"name": "tests.txt", "url": "https://api.github.com/assets/1"},
                {"name": "onix-launcher.apk", "url": "https://api.github.com/repos/AGonzalez-97/onix-launcher/releases/assets/99", "browser_download_url": "https://github.com/dl/onix-launcher.apk"}
            ]
        }"""
        val rel = com.axel.onixlauncher.Updates.parseRelease(json)!!
        assertEquals("0.81.0", rel.version)
        assertEquals("https://api.github.com/repos/AGonzalez-97/onix-launcher/releases/assets/99", rel.assetApiUrl)
        assertTrue(com.axel.onixlauncher.Updates.isNewer("0.80.0", rel.version))
        assertFalse(com.axel.onixlauncher.Updates.isNewer("0.81.0", rel.version))
        assertFalse(com.axel.onixlauncher.Updates.isNewer("0.82.0", rel.version))
        assertEquals("built_in_tok", com.axel.onixlauncher.Updates.pickToken(null, " built_in_tok "))
        assertEquals("saved_tok", com.axel.onixlauncher.Updates.pickToken("saved_tok", "built_in_tok"))
    }

    /** v0.81.24: un texto pegado que no es un token (un informe, por ejemplo) no tapa al token de la app. */
    @Test
    fun un_texto_pegado_que_no_es_token_se_ignora() {
        val u = com.axel.onixlauncher.Updates
        val informe = "=== Prueba completa · Onix Launcher ===\nFecha: 12/11/2026"
        assertFalse(u.isValidToken(informe))
        assertFalse(u.isValidToken("ghp_abc def"))
        assertFalse(u.isValidToken("corto"))
        assertTrue(u.isValidToken("github_pat_11ABCDEF0abcdef_ghijkl1234567890"))
        assertTrue(u.isValidToken("ghp_0123456789abcdefABCDEF0123456789abcd\n"))
        assertEquals("built_in_tok", u.pickToken(informe, "built_in_tok"))
        assertEquals(null, u.pickToken(informe, null))
    }

    /** v0.81.12: con la misma versión, una compilación más nueva también es una actualización. */
    @Test
    fun actualizador_compara_tambien_el_numero_de_compilacion() {
        val u = com.axel.onixlauncher.Updates
        assertEquals(178, u.buildNumber("build-178"))
        assertEquals(null, u.buildNumber("v0.81.0"))
        assertEquals(null, u.buildNumber(""))
        fun rel(version: String, tag: String) = com.axel.onixlauncher.ReleaseInfo(version, tag, "a", "b")
        // Versión mayor: siempre
        assertTrue(u.hasUpdate("0.81.10", 177, rel("0.81.11", "build-178")))
        // Misma versión: solo si la compilación publicada es mayor que la instalada
        assertTrue(u.hasUpdate("0.81.11", 178, rel("0.81.11", "build-179")))
        assertFalse(u.hasUpdate("0.81.11", 178, rel("0.81.11", "build-178")))
        assertFalse(u.hasUpdate("0.81.11", 179, rel("0.81.11", "build-178")))
        // Versión menor: nunca, aunque la compilación sea mayor
        assertFalse(u.hasUpdate("0.81.11", 178, rel("0.81.10", "build-200")))
        // Etiqueta sin número de compilación: decide solo la versión
        assertFalse(u.hasUpdate("0.81.11", 178, rel("0.81.11", "v0.81.11")))
    }

    /** v0.81.13: el launcher que protege el firmware de la radio NWD (propiedad `persist.nwd.launcher.default`). */
    @Test
    fun inicio_segun_la_radio() {
        val own = "com.axel.onixlauncher"
        assertEquals("persist.nwd.launcher.default", HomeDefault.RADIO_PROP)
        assertEquals("La radio no informa un launcher protegido.", HomeDefault.describeRadio(null, own))
        assertEquals("La radio no informa un launcher protegido.", HomeDefault.describeRadio("  ", own))
        assertEquals("La radio protege a Onix.", HomeDefault.describeRadio("com.axel.onixlauncher/.MainActivity", own))
        assertEquals("La radio protege a com.nwd.launcher.", HomeDefault.describeRadio("com.nwd.launcher", own))
    }
}
