package com.axel.onixlauncher

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Posición y escala de un panel en el lienzo de 1024x600. */
data class PanelPos(val x: Float, val y: Float, val scale: Float)

/** Guarda la distribución de los paneles en el teléfono / head unit. */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    /** Cambia cada vez que se restablece, para que los paneles vuelvan a su lugar. */
    var resetToken by mutableIntStateOf(0)
        private set

    fun get(id: String, def: PanelPos): PanelPos {
        val raw = prefs.getString(id, null) ?: return def
        val p = raw.split(",").mapNotNull { it.toFloatOrNull() }
        return if (p.size == 3) PanelPos(p[0], p[1], p[2]) else def
    }

    fun save(id: String, pos: PanelPos) {
        prefs.edit().putString(id, "${pos.x},${pos.y},${pos.scale}").apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
        resetToken++
    }
}

/**
 * Un panel que se puede mover y escalar. El contenido se diseña a su tamaño original (w x h)
 * y se escala entero, así no se desarma. En modo edición: arrastrar para mover, y la
 * manija de la esquina para agrandar o achicar.
 */
@Composable
fun EditablePanel(
    id: String,
    default: PanelPos,
    w: Int,
    h: Int,
    editing: Boolean,
    store: LayoutStore,
    content: @Composable () -> Unit
) {
    var pos by remember(id, store.resetToken) { mutableStateOf(store.get(id, default)) }
    val density = LocalDensity.current.density
    Box(Modifier.offset(pos.x.dp, pos.y.dp).size((w * pos.scale).dp, (h * pos.scale).dp)) {
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(w.dp, h.dp)
                .graphicsLayer {
                    scaleX = pos.scale
                    scaleY = pos.scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) { content() }

        if (editing) {
            // Capa de edición: tapa el contenido y lo mueve con el dedo
            Box(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x334C8DFF))
                    .border(2.dp, Color(0xFF4C8DFF), RoundedCornerShape(22.dp))
                    .pointerInput(id) {
                        detectDragGestures(onDragEnd = { store.save(id, pos) }) { change, drag ->
                            change.consume()
                            pos = pos.copy(
                                x = (pos.x + drag.x / density).coerceIn(-w * pos.scale * 0.5f, 1024f - 60f),
                                y = (pos.y + drag.y / density).coerceIn(-h * pos.scale * 0.5f, 600f - 60f)
                            )
                        }
                    }
            )
            // Manija para cambiar el tamaño
            Box(
                Modifier.align(Alignment.BottomEnd).offset(10.dp, 10.dp).size(40.dp)
                    .clip(CircleShape).background(Color(0xFF4C8DFF))
                    .pointerInput(id + "-tam") {
                        detectDragGestures(onDragEnd = { store.save(id, pos) }) { change, drag ->
                            change.consume()
                            val d = (drag.x + drag.y) / 2f / density
                            pos = pos.copy(scale = (pos.scale + d / w).coerceIn(0.6f, 1.6f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val s = size.width
                    drawLine(Color.White, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.8f, s * 0.2f), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.8f, s * 0.2f), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.8f, s * 0.5f), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(s * 0.2f, s * 0.5f), Offset(s * 0.2f, s * 0.8f), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(s * 0.2f, s * 0.8f), Offset(s * 0.5f, s * 0.8f), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Barra de arriba mientras se edita: Restablecer y Listo. */
@Composable
fun EditBar(store: LayoutStore, onDone: () -> Unit) {
    Row(
        Modifier.offset(262.dp, 150.dp).width(500.dp).height(56.dp)
            .clip(RoundedCornerShape(28.dp)).background(Color(0xF00B1030))
            .border(1.dp, Color(0xFF4C8DFF), RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Arrastrá para mover · esquina para el tamaño",
            color = Color(0xFFE3EAF7), fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF2A3160))
                .clickable { store.reset() }.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("Restablecer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF4C8DFF))
                .clickable(onClick = onDone).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) { Text("Listo", color = Color(0xFF0A0F1A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}
