package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Colores y piezas comunes de la pantalla de inicio, el dock, Ajustes, Diagnóstico y Acerca de. */

/** Modo de tema (v0.80.0): oscuro fijo, claro fijo o automático según las luces del auto y la hora. */
internal enum class ThemeMode(val id: String, val label: String) {
    DARK("dark", "Oscuro"),
    LIGHT("light", "Claro"),
    AUTO("auto", "Automático");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: DARK
    }
}

/** Colores de acento elegibles desde Ajustes → Apariencia (v0.80.0). */
internal enum class AccentPalette(val id: String, val label: String, val color: Color) {
    BLUE("blue", "Azul", Color(0xFF4C8DFF)),
    AMBER("amber", "Ámbar", Color(0xFFF59E0B)),
    GREEN("green", "Verde", Color(0xFF10B981)),
    RED("red", "Rojo", Color(0xFFEF4444)),
    PURPLE("purple", "Violeta", Color(0xFF8B5CF6)),
    CYAN("cyan", "Celeste", Color(0xFF06B6D4));

    companion object {
        fun fromId(id: String?): AccentPalette = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

/** Nivel de transparencia y efecto vidriado (Glassmorphism) para las pantallas superpuestas (v0.81.6). */
internal enum class GlassStyle(val id: String, val label: String, val alpha: Float, val backdropAlpha: Float) {
    GLASS("glass", "Vidriado", 0.68f, 0.55f),
    BALANCED("balanced", "Equilibrado", 0.82f, 0.72f),
    SOLID("solid", "Sólido", 0.96f, 0.88f);

    companion object {
        fun fromId(id: String?): GlassStyle = entries.firstOrNull { it.id == id } ?: GLASS
    }
}

/**
 * Estado reactivo del tema y color de acento (v0.80.0).
 * En modo `AUTO`, usa el modo claro únicamente de día (`Ambient.DIA`) cuando los faros del auto
 * (`lightsOn`, cable ILL) están apagados; apenas se prenden las luces o cae la tarde/noche, pasa a oscuro.
 */
internal object ThemePrefs {
    private const val PREFS = "apariencia"
    private const val KEY_MODE = "tema"
    private const val KEY_ACCENT = "acento"
    private const val KEY_GLASS = "vidriado"

    val mode = mutableStateOf(ThemeMode.DARK)
    val accent = mutableStateOf(AccentPalette.BLUE)
    val glass = mutableStateOf(GlassStyle.GLASS)
    val isLight = mutableStateOf(false)

    private var lastLightsOn = false
    private var lastAmbient = Ambient.DIA

    /** Regla pura (testeable): ¿corresponde usar paleta clara? */
    fun resolveLight(mode: ThemeMode, lightsOn: Boolean, ambient: Ambient): Boolean = when (mode) {
        ThemeMode.DARK -> false
        ThemeMode.LIGHT -> true
        ThemeMode.AUTO -> !lightsOn && ambient == Ambient.DIA
    }

    fun applyState(
        newMode: ThemeMode,
        newAccent: AccentPalette,
        newGlass: GlassStyle = glass.value,
        lightsOn: Boolean = lastLightsOn,
        ambient: Ambient = lastAmbient
    ) {
        lastLightsOn = lightsOn
        lastAmbient = ambient
        mode.value = newMode
        accent.value = newAccent
        glass.value = newGlass
        isLight.value = resolveLight(newMode, lightsOn, ambient)
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val m = ThemeMode.fromId(p.getString(KEY_MODE, ThemeMode.DARK.id))
        val a = AccentPalette.fromId(p.getString(KEY_ACCENT, AccentPalette.BLUE.id))
        val g = GlassStyle.fromId(p.getString(KEY_GLASS, GlassStyle.GLASS.id))
        applyState(m, a, g)
    }

    fun setMode(context: Context, newMode: ThemeMode) {
        applyState(newMode, accent.value, glass.value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, newMode.id).apply()
    }

    fun setAccent(context: Context, newAccent: AccentPalette) {
        applyState(mode.value, newAccent, glass.value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, newAccent.id).apply()
    }

    fun setGlass(context: Context, newGlass: GlassStyle) {
        applyState(mode.value, accent.value, newGlass)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GLASS, newGlass.id).apply()
    }

    fun syncAuto(lightsOn: Boolean, ambient: Ambient) {
        lastLightsOn = lightsOn
        lastAmbient = ambient
        isLight.value = resolveLight(mode.value, lightsOn, ambient)
    }

    fun resetForTests() {
        applyState(ThemeMode.DARK, AccentPalette.BLUE, GlassStyle.GLASS, lightsOn = false, ambient = Ambient.DIA)
    }
}

// Paleta reactiva: lee el estado de Compose en ThemePrefs, actualizando todas las pantallas en vivo.
internal val Accent: Color get() = ThemePrefs.accent.value.color
internal val TextMain: Color get() = if (ThemePrefs.isLight.value) Color(0xFF0F172A) else Color(0xFFF2F4F8)
internal val Muted: Color get() = if (ThemePrefs.isLight.value) Color(0xFF475569) else Color(0xFF9DA8C2)
internal val CardBorder: Color
    get() = when {
        ThemePrefs.isLight.value -> ThemePrefs.accent.value.color.copy(alpha = 0.28f)
        ThemePrefs.accent.value == AccentPalette.BLUE -> Color(0x337FA6FF)
        else -> ThemePrefs.accent.value.color.copy(alpha = 0.25f)
    }
internal val Track: Color get() = if (ThemePrefs.isLight.value) Color(0xFFCBD5E1) else Color(0xFF253049)
internal val OnAccent: Color get() = Color(0xFF0A0F1A)
/** Vidrio de los paneles: grafito azulado en oscuro, cristal claro en modo día. */
internal val GlassTop: Color get() = if (ThemePrefs.isLight.value) Color(0xEBEEF3FA) else Color(0xE6151C2E)
internal val GlassBottom: Color get() = if (ThemePrefs.isLight.value) Color(0xEBDCE5F2) else Color(0xE60A0F1C)
internal val PanelBg: Color get() = if (ThemePrefs.isLight.value) Color(0xFFE2E8F0) else Color(0xFF111634)

/** Posiciona un elemento en el lienzo de diseño de 1024x600. */
internal fun Modifier.at(x: Int, y: Int, w: Int, h: Int) = this.offset(x.dp, y.dp).size(w.dp, h.dp)

/** Fondo de tarjeta: vidrio grafito (o claro) con borde del color de acento. */
internal fun Modifier.card(radius: Int = 20): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this.clip(shape)
        .background(Brush.verticalGradient(listOf(GlassTop, GlassBottom)))
        .border(1.dp, CardBorder, shape)
}

@Composable
internal fun PillButton(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

/** Abre una pantalla de otra app sin cerrar el launcher si falla. */
internal fun startSafe(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLog.warn("No se pudo abrir ${intent.action ?: intent.component?.packageName}", e)
        Toast.makeText(context, "No se pudo abrir", Toast.LENGTH_SHORT).show()
    }
}
