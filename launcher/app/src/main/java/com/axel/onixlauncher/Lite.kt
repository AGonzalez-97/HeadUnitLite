package com.axel.onixlauncher

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateOf

/*
 * Modo liviano (v0.58.0): menos trabajo de dibujo en radios lentas como la del autor (Allwinner T3,
 * 4 núcleos de 32 bits). Idea del "Lite" de CarLink (docs/LOW_END_PERFORMANCE.md); código propio.
 * En Automático se prende solo en equipos de gama baja. Con el modo liviano:
 *  - no se dibuja la lluvia animada ni el giro del emblema;
 *  - la presentación no se repite cada vez que se prende la pantalla (solo al abrir la app);
 *  - el mapa arranca en el clásico (osmdroid) si nunca se eligió otro: el 3D es lo más pesado.
 * Se cambia en Diagnóstico → Liviano (Automático / Sí / No).
 */
object LiteMode {
    enum class Choice(val label: String) { AUTO("automático"), ON("sí"), OFF("no") }

    val choice = mutableStateOf(Choice.AUTO)
    /** Si el equipo es de gama baja (se calcula al arrancar). */
    val lowEnd = mutableStateOf(false)
    private var loaded = false

    /** Si el modo liviano está prendido ahora. */
    val isOn: Boolean get() = on(choice.value, lowEnd.value)

    /** Prendido según la elección y el equipo (pura, para los tests). */
    fun on(choice: Choice, lowEnd: Boolean): Boolean = when (choice) {
        Choice.ON -> true
        Choice.OFF -> false
        Choice.AUTO -> lowEnd
    }

    /**
     * Gama baja: sin procesador de 64 bits, con 4 núcleos o menos, o con 2 GB de memoria o menos
     * (los mismos criterios que CarLink). Pura, para los tests.
     */
    fun isLowEnd(has64Bit: Boolean, cores: Int, ramMb: Long): Boolean = !has64Bit || cores <= 4 || ramMb in 1..2048

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val name = context.getSharedPreferences("liviano", Context.MODE_PRIVATE).getString("modo", null)
        choice.value = Choice.entries.firstOrNull { it.name == name } ?: Choice.AUTO
        val ramMb = try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            mi.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
        lowEnd.value = isLowEnd(Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(), Runtime.getRuntime().availableProcessors(), ramMb)
    }

    /** Automático → Sí → No → Automático. */
    fun cycle(context: Context) {
        choice.value = Choice.entries[(choice.value.ordinal + 1) % Choice.entries.size]
        context.getSharedPreferences("liviano", Context.MODE_PRIVATE).edit().putString("modo", choice.value.name).apply()
    }

    /** "Liviano: automático (sí)" para el botón de Diagnóstico. */
    fun label(): String = "Liviano: ${choice.value.label}" + if (choice.value == Choice.AUTO) " (${if (lowEnd.value) "sí" else "no"})" else ""
}
