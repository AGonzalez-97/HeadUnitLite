package com.axel.onixlauncher

import android.content.Intent
import android.content.Context
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Accesos propios que se pueden poner en el dock (además de cualquier app instalada). */
internal enum class Builtin(val label: String, val c1: Color, val c2: Color, val icon: DrawScope.() -> Unit) {
    NAV("Navegación", Color(0xFFFF5C6C), Color(0xFFC21F3A), { iconPin() }),
    MUSIC("Música", Color(0xFF3BE07A), Color(0xFF119A4A), { iconMusic() }),
    RADIO("Radio", Color(0xFFFFA94D), Color(0xFFE5600C), { iconRadio() }),
    PHONE("Teléfono", Color(0xFF4ADE80), Color(0xFF16A34A), { iconPhone() }),
    APPS("Apps", Color(0xFF243049), Color(0xFF172033), { iconApps() }),
    DIAG("Diagnóstico", Color(0xFFA78BFA), Color(0xFF7C3AED), { iconPulse() }),
    /** v0.52.0: viajes, kilometraje y recordatorios. */
    CAR("Mi auto", Color(0xFF38BDF8), Color(0xFF0369A1), { iconWrench() }),
    /** v0.54.0 / v0.56.0: Android Auto y CarPlay (motor de CarLink dentro de Onix). */
    PROJECTION("Auto/CarPlay", Color(0xFF60A5FA), Color(0xFF1D4ED8), { iconProjection() }),
    SETTINGS("Ajustes", Color(0xFF475569), Color(0xFF2B3647), { iconGear() })
}

/** Un lugar del dock: un acceso propio, una app instalada o vacío (muestra "+" al editar). */
internal sealed class DockSlot {
    data class Own(val b: Builtin) : DockSlot()
    data class App(val pkg: String, val activity: String, val label: String) : DockSlot()
    data object Empty : DockSlot()

    fun encode(): String = when (this) {
        is Own -> "own:${b.name}"
        is App -> "app:$pkg|$activity|${label.replace(";", " ")}"
        Empty -> "vacio"
    }

    companion object {
        fun decode(s: String): DockSlot = when {
            s.startsWith("own:") -> Builtin.entries.firstOrNull { it.name == s.removePrefix("own:") }?.let { Own(it) } ?: Empty
            s.startsWith("app:") -> s.removePrefix("app:").split("|").let { p ->
                if (p.size >= 3) App(p[0], p[1], p[2]) else Empty
            }
            else -> Empty
        }
    }
}

/**
 * Los 7 lugares del dock después de Inicio (que queda fijo), guardados en el equipo
 * (SharedPreferences `dock`). Mantener apretado un ícono entra en modo edición: × quita, + agrega.
 */
internal object DockStore {
    const val SLOTS = 7
    val defaults: List<DockSlot> = listOf(Builtin.NAV, Builtin.MUSIC, Builtin.RADIO, Builtin.PHONE, Builtin.APPS, Builtin.CAR, Builtin.PROJECTION)
        .map { DockSlot.Own(it) }
    val slots = mutableStateOf(defaults)
    val editing = mutableStateOf(false)
    /** Lugar que espera una app elegida en el cajón (null = ninguno). */
    val picking = mutableStateOf<Int?>(null)
    private var loaded = false

    /** Convierte la lista guardada; si está incompleta, completa con los accesos por defecto (pura, para los tests). */
    fun parse(raw: String?): List<DockSlot> {
        val list = raw?.split(";")?.map { DockSlot.decode(it) }.orEmpty()
        return List(SLOTS) { i -> list.getOrNull(i) ?: defaults[i] }
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences("dock", Context.MODE_PRIVATE)
        val raw = prefs.getString("lugares", null)
        slots.value = parse(raw)
        // v0.52.0: "Mi auto" toma el lugar de Diagnóstico una sola vez (Diagnóstico sigue en la franja de Inicio)
        if (raw != null && !prefs.getBoolean("mi_auto_agregado", false)) {
            val migrated = withCar(slots.value)
            if (migrated != slots.value) {
                slots.value = migrated
                prefs.edit().putString("lugares", migrated.joinToString(";") { it.encode() }).apply()
            }
            prefs.edit().putBoolean("mi_auto_agregado", true).apply()
        }
        // v0.54.0: "Auto/CarPlay" toma el lugar de Ajustes una sola vez (Ajustes sigue en Apps)
        if (raw != null && !prefs.getBoolean("auto_carplay_agregado", false)) {
            val migrated = withProjection(slots.value)
            if (migrated != slots.value) {
                slots.value = migrated
                prefs.edit().putString("lugares", migrated.joinToString(";") { it.encode() }).apply()
            }
            prefs.edit().putBoolean("auto_carplay_agregado", true).apply()
        }
    }

    /** Cambia Ajustes por Auto/CarPlay si no está (pura, para los tests). */
    fun withProjection(list: List<DockSlot>): List<DockSlot> {
        if (DockSlot.Own(Builtin.PROJECTION) in list) return list
        val i = list.indexOf(DockSlot.Own(Builtin.SETTINGS))
        return if (i < 0) list else list.toMutableList().also { it[i] = DockSlot.Own(Builtin.PROJECTION) }
    }

    /** Cambia Diagnóstico por Mi auto si Mi auto no está (pura, para los tests). */
    fun withCar(list: List<DockSlot>): List<DockSlot> {
        if (DockSlot.Own(Builtin.CAR) in list) return list
        val i = list.indexOf(DockSlot.Own(Builtin.DIAG))
        return if (i < 0) list else list.toMutableList().also { it[i] = DockSlot.Own(Builtin.CAR) }
    }

    fun set(context: Context, index: Int, slot: DockSlot) {
        slots.value = slots.value.toMutableList().also { it[index] = slot }
        context.getSharedPreferences("dock", Context.MODE_PRIVATE).edit()
            .putString("lugares", slots.value.joinToString(";") { it.encode() }).apply()
    }

    fun reset(context: Context) {
        slots.value = defaults
        context.getSharedPreferences("dock", Context.MODE_PRIVATE).edit().remove("lugares").apply()
    }
}

/**
 * Barra inferior de accesos, compartida por las dos pantallas. Inicio queda fijo y resaltado en la
 * pantalla principal; los otros 7 lugares se personalizan (mantener apretado → × quitar, + agregar
 * cualquier app instalada o un acceso propio). En modo edición aparece "Listo" sobre Inicio.
 * El botón de cámara se sacó hasta conocer la app de cámara del head unit (AUD-011).
 */
@Composable
internal fun Dock(
    onDiag: () -> Unit,
    modifier: Modifier,
    onRadios: () -> Unit = {},
    homeSelected: Boolean = false,
    onHome: () -> Unit = {},
    onApps: () -> Unit = {},
    onNav: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DockStore.load(context) }
    val editing = DockStore.editing.value
    Row(
        modifier.card(radius = 22).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val item = Modifier.weight(1f).fillMaxHeight()
        if (editing) {
            DockItem("Listo", Color(0xFF2F7BFF), Color(0xFF1E4FB8), { iconCheck() }, item, true, onClick = { DockStore.editing.value = false })
        } else {
            DockItem("Inicio", Color(0xFF1E2C4D), Color(0xFF14203A), { iconHome() }, item, homeSelected, onClick = onHome)
        }
        DockStore.slots.value.forEachIndexed { i, slot ->
            Sep()
            val onLong = { DockStore.editing.value = true }
            when (slot) {
                is DockSlot.Own -> DockItem(
                    slot.b.label, slot.b.c1, slot.b.c2, slot.b.icon, item,
                    editing = editing, onRemove = { DockStore.set(context, i, DockSlot.Empty) }, onLongClick = onLong
                ) {
                    when (slot.b) {
                        Builtin.NAV -> onNav()   // mapa propio (v0.37.0), sin Google Maps
                        Builtin.MUSIC -> onNav()   // pantalla de música del launcher (regla de oro: sin abrir Spotify)
                        Builtin.RADIO -> onRadios()
                        Builtin.PHONE -> PhoneApp.open(context)
                        Builtin.APPS -> onApps()
                        Builtin.DIAG -> onDiag()
                        Builtin.CAR -> CarCare.open.value = true
                        Builtin.PROJECTION -> Projection.open.value = true
                        Builtin.SETTINGS -> SettingsNav.show()
                    }
                }
                is DockSlot.App -> AppDockItem(
                    slot, item, editing,
                    onRemove = { DockStore.set(context, i, DockSlot.Empty) }, onLongClick = onLong
                ) {
                    startSafe(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(android.content.ComponentName(slot.pkg, slot.activity)))
                }
                DockSlot.Empty -> AddDockItem(item, editing) { DockStore.picking.value = i }
            }
        }
    }
}

/** Una app instalada en el dock: su ícono y su nombre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDockItem(
    slot: DockSlot.App,
    modifier: Modifier,
    editing: Boolean,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val icon = rememberAppIcon(slot.pkg)
    Box(
        modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { if (!editing) onClick() }, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, null, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Track))
            }
            Text(slot.label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (editing) RemoveBadge(Modifier.align(Alignment.TopEnd), onRemove)
    }
}

/** Lugar vacío: en modo edición, un "+" para agregar una app; fuera de edición, nada. */
@Composable
private fun AddDockItem(modifier: Modifier, editing: Boolean, onAdd: () -> Unit) {
    Box(
        modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (editing) Accent else Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text("+", color = if (editing) Accent else Muted, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Text("Agregar", color = Muted, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** Círculo rojo con × para quitar un ícono en modo edición. */
@Composable
private fun RemoveBadge(modifier: Modifier, onRemove: () -> Unit) {
    Box(
        modifier.padding(top = 2.dp, end = 10.dp).size(22.dp).clip(CircleShape).background(Color(0xFFE5484D)).clickable(onClick = onRemove),
        contentAlignment = Alignment.Center
    ) { Text("×", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp) }
}

@Composable
private fun Sep() {
    Box(Modifier.width(1.dp).height(46.dp).background(Color(0x1FFFFFFF)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    label: String,
    c1: Color,
    c2: Color,
    icon: DrawScope.() -> Unit,
    modifier: Modifier,
    selected: Boolean = false,
    editing: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.padding(horizontal = 3.dp).clip(shape)
            .then(
                if (selected) Modifier.background(Brush.verticalGradient(listOf(Color(0x664C8DFF), Color(0x1A4C8DFF))))
                    .border(1.dp, Color(0x804C8DFF), shape)
                else Modifier
            )
            .combinedClickable(onClick = { if (!editing || onRemove == null) onClick() }, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(c1, c2))),
                contentAlignment = Alignment.Center
            ) { Canvas(Modifier.size(26.dp)) { icon() } }
            Text(label, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).size(64.dp, 3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFF4CA6FF))
            )
        }
        if (editing && onRemove != null) RemoveBadge(Modifier.align(Alignment.TopEnd), onRemove)
    }
}


/**
 * Botón Teléfono del dock (v0.71.0). La radio NWD no tiene marcador de Android (ACTION_DIAL fallaba:
 * "ActivityNotFoundException", Prueba completa del 04/11): las llamadas por Bluetooth las maneja la
 * app de teléfono de la radio. Se abre esa; si no está, otra app de teléfono; si no, el marcador.
 * Salida documentada en README → "Salidas a otras apps".
 */
object PhoneApp {
    private val PREFERRED = listOf("com.nwd.android.phone")
    private val WORDS = listOf("phone", "dialer", "teléfono", "telefono", "bluetooth")

    /** La app de teléfono entre [apps] (paquete → nombre), sin contar a Onix; o null. Pura, para los tests. */
    fun pick(apps: Map<String, String>): String? =
        PREFERRED.firstOrNull { it in apps } ?: apps.entries.firstOrNull { (pkg, label) ->
            pkg != "com.axel.onixlauncher" && WORDS.any { w -> w in pkg.lowercase() || w in label.lowercase() }
        }?.key

    fun open(context: android.content.Context) {
        val pkg = pick(Projection.installed(context))
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        startSafe(context, launch ?: Intent(Intent.ACTION_DIAL))
    }
}
