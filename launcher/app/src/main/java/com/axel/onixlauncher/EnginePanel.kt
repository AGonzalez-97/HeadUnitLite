package com.axel.onixlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val Line = Color(0x264C8DFF)
private val DialBlue = Color(0xFF2F8BFF)
private val DialCyan = Color(0xFF39D0FF)
private val DialRed = Color(0xFFFF4040)
private val DialOrange = Color(0xFFFF8A1F)
private const val DIAL_START = 135f
private const val DIAL_SWEEP = 270f

/**
 * Panel de telemetría de Inicio (diseño de las imágenes de referencia de v0.29.0): velocímetro
 * y cuentavueltas redondos arriba; temperatura y combustible con barras de segmentos a los
 * costados del auto visto desde arriba; batería abajo. Los datos vienen del OBD2 (Obd.kt); sin
 * datos, los valores son "--".
 *
 * v0.50.0: al lado de la batería, el consumo estimado (km/l); abajo, el estado de los códigos de
 * falla ([codes]), que al tocarlo abre la ventana de códigos ([onCodes]).
 */
@Composable
fun EnginePanel(speed: Int?, obd: ObdData?, modifier: Modifier, codes: ObdCodes? = null, onCodes: () -> Unit = {}) {
    Column(modifier.card().padding(horizontal = 9.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundDial(
                value = speed?.toFloat(), max = 240f, labelStep = 40f, minorStep = 10f, redFrom = 180f,
                text = speed?.toString() ?: "--", unit = if (speed == null) "sin GPS" else "km/h", hot = DialRed,
                labelOf = { it.toInt().toString() }
            )
            RoundDial(
                value = obd?.rpm?.toFloat(), max = 8000f, labelStep = 1000f, minorStep = 500f, redFrom = 6500f,
                text = obd?.rpm?.toString() ?: "--", unit = if (obd?.rpm == null) "sin OBD2" else "rpm", hot = DialOrange,
                labelOf = { (it / 1000).toInt().toString() }
            )
        }
        Spacer(Modifier.height(6.dp))

        // Temperatura | auto | combustible
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SegmentBar(
                top = "H", bottom = "C", topColor = DialRed, bottomColor = DialBlue,
                fraction = obd?.coolantC?.let { ((it - 40) / 80f).coerceIn(0f, 1f) }
            )
            SideValue({ iconThermo() }, obd?.coolantC?.toString() ?: "--", "°C", "Temp. motor", Modifier.weight(1f))
            Canvas(Modifier.size(38.dp, 92.dp)) { carTopView() }
            SideValue({ iconFuel() }, obd?.fuelPct?.toString() ?: "--", "%", "Combustible", Modifier.weight(1f))
            SegmentBar(
                top = "F", bottom = "E", topColor = Color(0xFF9AA6C0), bottomColor = DialOrange,
                fraction = obd?.fuelPct?.let { (it / 100f).coerceIn(0f, 1f) }
            )
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Line))

        // Batería y consumo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(22.dp)) { iconBattery() }
                Spacer(Modifier.width(6.dp))
                Text(obd?.volts?.let { String.format(Locale.US, "%.1f", it) } ?: "--", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(" V", color = TextMain, fontSize = 13.sp)
            }
            Box(Modifier.width(1.dp).height(22.dp).background(Line))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(obd?.kmPerLiter?.let { String.format(Locale.US, "%.1f", it) } ?: "--", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(" km/l", color = TextMain, fontSize = 13.sp)
            }
        }
        // Estado de los códigos de falla. Sin lector no se muestran valores inventados.
        val (status, color) = codesStatus(obd, codes)
        Text(
            status, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
                .clickable(enabled = obd != null, onClick = onCodes).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Texto y color de la línea de códigos del panel (v0.50.0). */
internal fun codesStatus(obd: ObdData?, codes: ObdCodes?): Pair<String, Color> = when {
    obd == null -> "Batería · OBD2 sin conectar" to Muted
    codes == null -> "Leyendo códigos de falla…" to Muted
    codes.total == 0 && codes.mil != true -> "✓ Motor sin fallas" to Color(0xFF5BD68A)
    codes.total == 0 -> "Luz del motor prendida · tocá" to DialRed
    else -> "⚠ ${codes.total} ${if (codes.total == 1) "falla" else "fallas"} · tocá para ver" to
        (if (codes.mil == true || codes.stored.isNotEmpty()) DialRed else DialOrange)
}

/**
 * Reloj redondo: aro azul con la zona alta en [hot], marcas y números, el valor actual marcado con
 * un arco brillante y el número grande al centro. [value] null = sin datos (solo la escala).
 */
@Composable
private fun RoundDial(
    value: Float?,
    max: Float,
    labelStep: Float,
    minorStep: Float,
    redFrom: Float,
    text: String,
    unit: String,
    hot: Color,
    labelOf: (Float) -> String
) {
    val shown by animateFloatAsState((value ?: 0f).coerceIn(0f, max), tween(600), label = "dial")
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            // Disco y halo
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0E1A2E), Color(0xFF060B16)), c, r), r, c)
            for (i in 1..3) drawCircle(DialBlue.copy(alpha = 0.10f / i), r - 1.5f * density + i * 1.2f * density, c, style = Stroke(2.5f * density))
            drawCircle(DialBlue.copy(alpha = 0.55f), r - 1.5f * density, c, style = Stroke(1.2f * density))
            // Aro de color: azul → celeste, y la zona alta en rojo/naranja
            val ringR = r - 6f * density
            val ring = Stroke(4.5f * density, cap = StrokeCap.Butt)
            val redAt = DIAL_SWEEP * redFrom / max
            drawArc(
                Brush.sweepGradient(listOf(DialBlue, DialCyan, DialBlue), c), DIAL_START, redAt, false,
                Offset(c.x - ringR, c.y - ringR), Size(ringR * 2, ringR * 2), style = ring
            )
            drawArc(
                hot, DIAL_START + redAt, DIAL_SWEEP - redAt, false,
                Offset(c.x - ringR, c.y - ringR), Size(ringR * 2, ringR * 2), style = ring
            )
            // Marcas y números
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 7.5f * density
            }
            var v = 0f
            while (v <= max + 0.1f) {
                val major = (v % labelStep) < 0.1f
                val a = Math.toRadians((DIAL_START + DIAL_SWEEP * v / max).toDouble())
                val r0 = ringR - 5f * density
                val r1 = ringR - (if (major) 10f else 7.5f) * density
                val col = if (v >= redFrom) hot else Color.White.copy(alpha = if (major) 0.9f else 0.45f)
                drawLine(
                    col, Offset(c.x + r0 * cos(a).toFloat(), c.y + r0 * sin(a).toFloat()),
                    Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    strokeWidth = (if (major) 1.4f else 0.9f) * density
                )
                if (major) {
                    val rl = ringR - 16.5f * density
                    paint.color = (if (v >= redFrom) hot else Color(0xFFD7DEEA)).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        labelOf(v), c.x + rl * cos(a).toFloat(), c.y + rl * sin(a).toFloat() + 2.6f * density, paint
                    )
                }
                v += minorStep
            }
            // Valor actual: arco brillante por dentro del aro
            if (value != null && shown > 0f) {
                val vr = ringR + 0.5f * density
                drawArc(
                    Color.White.copy(alpha = 0.85f), DIAL_START, DIAL_SWEEP * shown / max, false,
                    Offset(c.x - vr, c.y - vr), Size(vr * 2, vr * 2), style = Stroke(1.6f * density, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = TextMain, fontSize = if (text.length > 3) 20.sp else 26.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Text(unit, color = Muted, fontSize = 10.sp, lineHeight = 11.sp)
        }
    }
}

/** Barra vertical de 10 segmentos con letras arriba y abajo (H/C o F/E); prende hasta [fraction]. */
@Composable
private fun SegmentBar(top: String, bottom: String, topColor: Color, bottomColor: Color, fraction: Float?) {
    Column(Modifier.width(14.dp).fillMaxHeight().padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, color = topColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
        Canvas(Modifier.width(6.dp).weight(1f).padding(vertical = 3.dp)) {
            val n = 10
            val gap = 2f * density
            val h = (size.height - gap * (n - 1)) / n
            val lit = ((fraction ?: 0f) * n + 0.5f).toInt()
            for (i in 0 until n) {
                val fromBottom = i
                val t = fromBottom / (n - 1f)
                val col = androidx.compose.ui.graphics.lerp(bottomColor, topColor, t)
                val y = size.height - (i + 1) * h - i * gap
                drawRoundRect(
                    if (fraction != null && fromBottom < lit) col else col.copy(alpha = 0.28f),
                    Offset(0f, y), Size(size.width, h), CornerRadius(1.5f * density)
                )
            }
        }
        Text(bottom, color = bottomColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
    }
}

/** Ícono, valor grande y etiqueta, centrados. */
@Composable
private fun SideValue(icon: DrawScope.() -> Unit, value: String, unit: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(26.dp)) { icon() }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            Text(unit, color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * Ventana de códigos de falla (v0.50.0): luz del motor, códigos guardados y pendientes con su
 * significado, leer de nuevo y borrar (confirmando; v0.79.0: también andando, como Torque).
 */
@Composable
internal fun CodesDialog(codes: ObdCodes?, speed: Int?, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    // AUD-051: con el lector conectado (si no, el pedido quedaba esperando). v0.79.0: también andando,
    // como Torque (pedido del autor: su Onix acepta el borrado con el motor en marcha y en movimiento)
    val stopped = ObdState.status.value == "conectado"
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(640.dp).heightIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF20B1120))
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp)
        ) {
            Text("Códigos de falla", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    codes == null -> "Todavía no se leyeron (hace falta el lector OBD2 conectado)."
                    codes.mil == true -> "Luz del motor: PRENDIDA"
                    codes.mil == false -> "Luz del motor: apagada"
                    else -> "Luz del motor: el auto no la informa"
                },
                color = if (codes?.mil == true) DialRed else Muted, fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            HealthGrid(CarHealth.of(codes, ObdState.alert.value))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (codes != null && codes.total == 0) {
                    Text("✓ Sin códigos de falla", color = Color(0xFF5BD68A), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                codes?.stored?.forEach { CodeRow(it, "Guardado", DialRed) }
                codes?.pending?.filter { it !in codes.stored }?.forEach { CodeRow(it, "Pendiente", DialOrange) }
            }
            Spacer(Modifier.height(12.dp))
            ObdState.clearResult.value?.let { Text(it, color = Accent, fontSize = 14.sp) }
            Text(
                "Borrar apaga la luz y borra los códigos (y los monitores de emisiones, que se rearman solos). Si la falla sigue, el código vuelve.",
                color = Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                DialogButton("Leer de nuevo", Accent) { ObdState.clearResult.value = null; ObdState.codesRequested = true }
                if (codes != null && (codes.total > 0 || codes.mil == true)) {
                    DialogButton(
                        when {
                            !stopped -> "Borrar (sin lector)"
                            confirm -> "¿Seguro? Tocá de nuevo"
                            else -> "Borrar códigos y apagar la luz"
                        },
                        if (stopped) DialRed else Muted
                    ) {
                        if (!stopped) return@DialogButton
                        if (confirm) {
                            ObdState.clearRequested = true
                            confirm = false
                        } else {
                            confirm = true
                        }
                    }
                }
                DialogButton("Cerrar", Muted, onClose)
            }
        }
    }
}

@Composable
private fun CodeRow(code: String, kind: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(code, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
        Column(Modifier.weight(1f)) {
            Text(Dtc.describe(code), color = TextMain, fontSize = 16.sp)
            Text(kind, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DialogButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

/** Salud del auto (v0.64.0): un punto de color por sistema, en dos filas de cuatro. */
@Composable
private fun HealthGrid(items: List<Pair<CarSystem, HealthLevel>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in items.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((system, level) in row) {
                    val color = when (level) {
                        HealthLevel.GOOD -> Color(0xFF5BD68A)
                        HealthLevel.WARN -> DialOrange
                        HealthLevel.FAULT -> DialRed
                        HealthLevel.UNKNOWN -> Muted
                    }
                    Row(
                        Modifier.width(144.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(6.dp))
                        Text(system.label, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
