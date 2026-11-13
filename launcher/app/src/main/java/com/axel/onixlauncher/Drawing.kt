package com.axel.onixlauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp

/* ---------- Íconos en un lienzo de 24x24, trazo blanco ---------- */

private val IconStroke = Stroke(width = 2.2f, cap = StrokeCap.Round)

fun DrawScope.iconNav() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(3f, 11f); lineTo(22f, 2f); lineTo(13f, 21f); lineTo(11f, 13f); close() }
        drawPath(p, Color.White)
    }
}

fun DrawScope.iconHome() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val roof = Path().apply { moveTo(2.5f, 11.5f); lineTo(12f, 3f); lineTo(21.5f, 11.5f) }
        drawPath(roof, Color.White, style = IconStroke)
        val body = Path().apply {
            moveTo(5f, 10f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f)
            lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 10f)
        }
        drawPath(body, Color.White, style = IconStroke)
    }
}

fun DrawScope.iconMusic() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawLine(Color.White, Offset(9f, 18f), Offset(9f, 5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(9f, 5f), Offset(21f, 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(21f, 3f), Offset(21f, 16f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawCircle(Color.White, 3f, Offset(6f, 18f), style = IconStroke)
        drawCircle(Color.White, 3f, Offset(18f, 16f), style = IconStroke)
    }
}

fun DrawScope.iconPin() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawCircle(Color.White, 7f, Offset(12f, 10f), style = IconStroke)
        drawCircle(Color.White, 2.5f, Offset(12f, 10f))
        drawLine(Color.White, Offset(12f, 17f), Offset(12f, 22f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

fun DrawScope.iconPhone() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawRoundRect(
            Color.White, Offset(7f, 2f), Size(10f, 20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = IconStroke
        )
        drawCircle(Color.White, 0.9f, Offset(12f, 18.5f))
    }
}

fun DrawScope.iconPulse() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply {
            moveTo(22f, 12f); lineTo(18f, 12f); lineTo(15f, 21f); lineTo(9f, 3f); lineTo(6f, 12f); lineTo(2f, 12f)
        }
        drawPath(p, Color.White, style = IconStroke)
    }
}

fun DrawScope.iconGear() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val c = Offset(12f, 12f)
        drawCircle(Color.White, 6f, c, style = IconStroke)
        drawCircle(Color.White, 2.5f, c, style = IconStroke)
        for (i in 0 until 8) {
            rotate(i * 45f, pivot = c) {
                drawLine(Color.White, Offset(12f, 2f), Offset(12f, 5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            }
        }
    }
}

/* ---------- Íconos del clima, lienzo de 48x48 ---------- */

private val SunColor = Color(0xFFFFC940)
private val CloudColor = Color(0xFFE3EAF7)
private val RainColor = Color(0xFF6CB6FF)

private fun DrawScope.sun(cx: Float, cy: Float, r: Float) {
    drawCircle(SunColor, r, Offset(cx, cy))
    for (i in 0 until 8) {
        rotate(i * 45f, pivot = Offset(cx, cy)) {
            drawLine(SunColor, Offset(cx, cy - r - 3f), Offset(cx, cy - r - 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, r: Float) {
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx + r * 0.55f, cy - r * 0.35f), r * 0.9f)) }
    val crescent = Path().apply { op(full, bite, androidx.compose.ui.graphics.PathOperation.Difference) }
    drawPath(crescent, Color(0xFFF1F4FF))
}

private fun DrawScope.cloud(dx: Float, dy: Float, s: Float, color: Color = CloudColor) {
    drawCircle(color, 8f * s, Offset(dx + 14f * s, dy + 26f * s))
    drawCircle(color, 11f * s, Offset(dx + 24f * s, dy + 20f * s))
    drawCircle(color, 8f * s, Offset(dx + 34f * s, dy + 26f * s))
    drawRoundRect(
        color, Offset(dx + 10f * s, dy + 24f * s), Size(28f * s, 10f * s),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * s, 5f * s)
    )
}

/** Ícono según el código de clima de Open-Meteo. */
fun DrawScope.weatherIcon(code: Int, isDay: Boolean) {
    val s = size.width / 48f
    scale(s, s, pivot = Offset.Zero) {
        when (code) {
            0 -> if (isDay) sun(24f, 24f, 9f) else moon(24f, 24f, 12f)
            1, 2 -> {
                if (isDay) sun(17f, 17f, 7f) else moon(18f, 16f, 9f)
                cloud(4f, 6f, 1f)
            }
            45, 48 -> for (i in 0 until 3) {
                drawLine(CloudColor, Offset(8f + i * 3f, 16f + i * 8f), Offset(40f - i * 3f, 16f + i * 8f), strokeWidth = 3.5f, cap = StrokeCap.Round)
            }
            in 51..67, in 80..82 -> {
                cloud(0f, -6f, 1f)
                for (i in 0 until 3) {
                    drawLine(RainColor, Offset(16f + i * 8f, 34f), Offset(13f + i * 8f, 42f), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
            in 95..99 -> {
                cloud(0f, -6f, 1f, Color(0xFFB9C3D6))
                val bolt = Path().apply {
                    moveTo(25f, 30f); lineTo(19f, 39f); lineTo(24f, 39f); lineTo(21f, 47f); lineTo(30f, 36f); lineTo(25f, 36f); close()
                }
                drawPath(bolt, SunColor)
            }
            else -> cloud(0f, 0f, 1f)
        }
    }
}

fun DrawScope.iconRadio() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawRoundRect(
            Color.White, Offset(2f, 8f), Size(20f, 13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = IconStroke
        )
        drawLine(Color.White, Offset(6f, 8f), Offset(18f, 3f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawCircle(Color.White, 3f, Offset(16f, 14.5f), style = IconStroke)
        drawLine(Color.White, Offset(6f, 12f), Offset(9f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(6f, 16f), Offset(9f, 16f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

fun DrawScope.iconBattery() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawRoundRect(
            Color.White, Offset(2f, 7f), Size(20f, 13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = IconStroke
        )
        drawLine(Color.White, Offset(6f, 4f), Offset(9f, 4f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(15f, 4f), Offset(18f, 4f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(6f, 13.5f), Offset(9f, 13.5f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(15f, 12f), Offset(15f, 15f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(13.5f, 13.5f), Offset(16.5f, 13.5f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

fun DrawScope.iconThermo() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawLine(Color.White, Offset(12f, 3f), Offset(12f, 14f), strokeWidth = 2.6f, cap = StrokeCap.Round)
        drawCircle(Color.White, 4f, Offset(12f, 17.5f))
        for (y in listOf(5f, 8f, 11f)) {
            drawLine(Color.White, Offset(15f, y), Offset(18f, y), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
    }
}

fun DrawScope.iconFuel() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawRoundRect(
            Color.White, Offset(4f, 3f), Size(10f, 18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f), style = IconStroke
        )
        drawLine(Color.White, Offset(4f, 10f), Offset(14f, 10f), strokeWidth = 2f)
        val hose = Path().apply {
            moveTo(14f, 8f); lineTo(18f, 8f); lineTo(20f, 11f); lineTo(20f, 18f)
            quadraticBezierTo(20f, 20f, 18f, 20f); quadraticBezierTo(16.5f, 20f, 16.5f, 18f); lineTo(16.5f, 13f)
        }
        drawPath(hose, Color.White, style = IconStroke)
    }
}

fun DrawScope.iconPrev() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(19f, 20f); lineTo(9f, 12f); lineTo(19f, 4f); close() }
        drawPath(p, Color.White)
        drawLine(Color.White, Offset(5f, 19f), Offset(5f, 5f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

fun DrawScope.iconNext() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(5f, 4f); lineTo(15f, 12f); lineTo(5f, 20f); close() }
        drawPath(p, Color.White)
        drawLine(Color.White, Offset(19f, 5f), Offset(19f, 19f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

fun DrawScope.iconPlay(color: Color) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(6f, 4f); lineTo(20f, 12f); lineTo(6f, 20f); close() }
        drawPath(p, color)
    }
}

fun DrawScope.iconPause(color: Color) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawRect(color, Offset(6f, 4f), Size(4f, 16f))
        drawRect(color, Offset(14f, 4f), Size(4f, 16f))
    }
}

/* ---------- Íconos del rediseño v0.25.1 (lienzo de 24x24) ---------- */

fun DrawScope.iconShuffle(color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val a = Path().apply { moveTo(3f, 7f); lineTo(7f, 7f); cubicTo(12f, 7f, 12f, 17f, 17f, 17f); lineTo(20f, 17f) }
        val b = Path().apply { moveTo(3f, 17f); lineTo(7f, 17f); cubicTo(12f, 17f, 12f, 7f, 17f, 7f); lineTo(20f, 7f) }
        drawPath(a, color, style = IconStroke)
        drawPath(b, color, style = IconStroke)
        for (y in listOf(7f, 17f)) {
            drawLine(color, Offset(20f, y), Offset(17.5f, y - 2.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(20f, y), Offset(17.5f, y + 2.5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

fun DrawScope.iconHeart(filled: Boolean, color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply {
            moveTo(12f, 20.5f)
            cubicTo(4f, 15f, 2.5f, 11f, 2.5f, 8.5f)
            cubicTo(2.5f, 5.5f, 4.8f, 3.5f, 7.5f, 3.5f)
            cubicTo(9.5f, 3.5f, 11f, 4.7f, 12f, 6.3f)
            cubicTo(13f, 4.7f, 14.5f, 3.5f, 16.5f, 3.5f)
            cubicTo(19.2f, 3.5f, 21.5f, 5.5f, 21.5f, 8.5f)
            cubicTo(21.5f, 11f, 20f, 15f, 12f, 20.5f)
            close()
        }
        if (filled) drawPath(p, color) else drawPath(p, color, style = IconStroke)
    }
}

/** Faro (símbolo de luces): semicírculo con haces. */
fun DrawScope.iconHeadlight(color: Color) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply {
            moveTo(13f, 5f); cubicTo(19.5f, 5f, 21.5f, 9f, 21.5f, 12f)
            cubicTo(21.5f, 15f, 19.5f, 19f, 13f, 19f); close()
        }
        drawPath(p, color, style = IconStroke)
        for (y in listOf(6.5f, 10f, 13.5f, 17f)) {
            drawLine(color, Offset(2.5f, y + 1f), Offset(9.5f, y), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}

/** Cuadrícula de 4 cuadrados redondeados. */
fun DrawScope.iconApps() {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        for (x in listOf(3f, 13.5f)) for (y in listOf(3f, 13.5f)) {
            drawRoundRect(
                Color.White, Offset(x, y), Size(7.5f, 7.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = IconStroke
            )
        }
    }
}

fun DrawScope.iconCheck(color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f) }
        drawPath(p, color, style = Stroke(width = 2.8f, cap = StrokeCap.Round))
    }
}

fun DrawScope.iconChevronRight(color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val p = Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        drawPath(p, color, style = IconStroke)
    }
}

/** Ícono de señal Wi-Fi / hotspot. */
fun DrawScope.iconWifi(color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        drawCircle(color, 2f, Offset(12f, 19.5f))
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(7.5f, 13f),
            size = Size(9f, 9f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(3.5f, 7.5f),
            size = Size(17f, 17f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

/** Sol chico para la franja inferior (de día) o luna (de noche). */
fun DrawScope.iconSunMoon(night: Boolean, color: Color = Color.White) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        if (night) {
            val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(12f, 12f), 8f)) }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(16.5f, 9f), 7f)) }
            drawPath(Path().apply { op(full, bite, androidx.compose.ui.graphics.PathOperation.Difference) }, color, style = IconStroke)
        } else {
            drawCircle(color, 4.5f, Offset(12f, 12f), style = IconStroke)
            for (i in 0 until 8) {
                rotate(i * 45f, pivot = Offset(12f, 12f)) {
                    drawLine(color, Offset(12f, 2.5f), Offset(12f, 4.5f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** El auto visto desde arriba (plateado), para el panel de motor. Lienzo de 40x90. */
fun DrawScope.carTopView() {
    val sx = size.width / 40f
    val sy = size.height / 90f
    scale(sx, sy, pivot = Offset.Zero) {
        val cr = androidx.compose.ui.geometry.CornerRadius(12f, 14f)
        // Sombra y carrocería
        drawRoundRect(Color(0x66000000), Offset(2f, 3f), Size(37f, 86f), cornerRadius = cr)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF8E96A3), Color(0xFFE9EDF2), Color(0xFFF6F8FA), Color(0xFFE9EDF2), Color(0xFF8E96A3))),
            Offset(1f, 1f), Size(38f, 86f), cornerRadius = cr
        )
        // Espejos
        drawRoundRect(Color(0xFFB8BFC9), Offset(-2f, 28f), Size(5f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
        drawRoundRect(Color(0xFFB8BFC9), Offset(37f, 28f), Size(5f, 6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
        // Parabrisas, techo y luneta
        val glass = Color(0xFF1B2333)
        drawPath(Path().apply { moveTo(6f, 25f); lineTo(34f, 25f); lineTo(31f, 37f); lineTo(9f, 37f); close() }, glass)
        drawRoundRect(Color(0xFF2A3346), Offset(9f, 38f), Size(22f, 28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f))
        drawPath(Path().apply { moveTo(9f, 67f); lineTo(31f, 67f); lineTo(33f, 76f); lineTo(7f, 76f); close() }, glass)
        // Faros y luces traseras
        drawRoundRect(Color(0xFFFFF4D6), Offset(5f, 3f), Size(8f, 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f))
        drawRoundRect(Color(0xFFFFF4D6), Offset(27f, 3f), Size(8f, 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f))
        drawRoundRect(Color(0xFFE5484D), Offset(4f, 82f), Size(9f, 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f))
        drawRoundRect(Color(0xFFE5484D), Offset(27f, 82f), Size(9f, 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f, 1.5f))
    }
}
