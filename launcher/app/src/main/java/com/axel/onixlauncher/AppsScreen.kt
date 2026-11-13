package com.axel.onixlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Cajón de aplicaciones (botón Apps del dock) e íconos de las apps instaladas.
 * Los íconos de Spotify, Maps y Waze que se ven en Inicio salen de acá: son los de cada app
 * instalada en el equipo, como en cualquier launcher.
 */

/** Una app que se puede abrir desde el launcher. */
class LaunchableApp(val label: String, val pkg: String, val activity: String)

private val iconCache = HashMap<String, ImageBitmap?>()

/** Ícono de una app instalada, o null si no está. Se guarda en memoria. Llamar fuera del hilo principal. */
internal fun appIcon(context: Context, pkg: String): ImageBitmap? = synchronized(iconCache) {
    iconCache.getOrPut(pkg) {
        try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

/** Nombre visible de una app instalada, o null si no está. */
internal fun appLabel(context: Context, pkg: String): String? = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    null
}

/** Ícono de una app para Compose: se carga en segundo plano. */
@Composable
internal fun rememberAppIcon(pkg: String?): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = if (pkg == null) null else withContext(Dispatchers.IO) { appIcon(context, pkg) }
    }
    return icon
}

/** Apps con ícono en el launcher, ordenadas por nombre, sin el propio Onix Launcher. */
internal fun launchableApps(context: Context): List<LaunchableApp> = try {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    context.packageManager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        .sortedBy { it.label.lowercase() }
} catch (e: Exception) {
    AppLog.warn("Apps: no se pudo leer la lista", e)
    emptyList()
}

/**
 * Pantalla de apps sobre el lienzo de 1024x600. Con [onPick] es el selector del dock: arriba los
 * accesos propios (Radio, Diagnóstico…) y abajo las apps; tocar una la devuelve en vez de abrirla.
 */
@Composable
internal fun AppsOverlay(onClose: () -> Unit, onPick: ((DockSlot) -> Unit)? = null) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2070B16)).clickable(onClick = onClose)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (onPick != null) "Elegí qué agregar al dock" else "Apps", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Text(apps?.let { "${it.size} instaladas" } ?: "Cargando…", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                if (onPick == null) {
                    PillButton("Ajustes", Track, TextMain, Modifier.width(130.dp).padding(end = 10.dp)) { onClose(); SettingsNav.show() }
                }
                PillButton("Cerrar", Track, TextMain, Modifier.width(120.dp), onClose)
            }
            Spacer(Modifier.size(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onPick != null) {
                    items(Builtin.entries.toList(), key = { "own:" + it.name }) { b ->
                        BuiltinTile(b) { onPick(DockSlot.Own(b)) }
                    }
                }
                items(apps.orEmpty(), key = { it.pkg + "/" + it.activity }) { app ->
                    AppTile(app) {
                        if (onPick != null) {
                            onPick(DockSlot.App(app.pkg, app.activity, app.label))
                        } else {
                            startSafe(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .setComponent(ComponentName(app.pkg, app.activity)))
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltinTile(b: Builtin, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(b.c1, b.c2))),
            contentAlignment = Alignment.Center
        ) { Canvas(Modifier.size(32.dp)) { b.icon(this) } }
        Text(
            b.label, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun AppTile(app: LaunchableApp, onClick: () -> Unit) {
    val icon = rememberAppIcon(app.pkg)
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Track), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(28.dp)) { iconApps() }
            }
        }
        Text(
            app.label, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}
