package com.axel.onixlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/*
 * Presentación animada (v0.39.0): emblema de Chevrolet y "ONIX LAUNCHER" al abrir el launcher y
 * cada vez que la radio vuelve de estar apagada o en suspensión (la pantalla se prende). Dura unos
 * 3,5 s; tocando la pantalla se saltea. El emblema es la imagen que aportó el autor (marca de
 * General Motors, ver THIRD_PARTY_LICENSES.md); la animación se hace con Animatable y graphicsLayer,
 * que se leen al dibujar y no recomponen la pantalla en cada cuadro.
 */
object Intro {
    /** Visible al arrancar el proceso (el launcher recién abierto). */
    val visible = mutableStateOf(true)
    private var registered = false

    /** Vuelve a mostrarla cuando se prende la pantalla, solo si el launcher es la pantalla de inicio (no en el celular). */
    fun register(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                // Modo liviano (v0.58.0): la presentación solo al abrir la app, no en cada encendido
                if (LastAudio.isHomeLauncher(app) && !LiteMode.isOn) main.post { visible.value = true }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}

/** Tiempos de la presentación, en fracciones del recorrido (para los tests y para leerla de un vistazo). */
object IntroTiming {
    const val TOTAL_MS = 2800
    const val HOLD_MS = 500L
    const val FADE_MS = 500

    /** 0 → 1 dentro del tramo [a, b] del recorrido. */
    fun phase(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
}

/**
 * Pantalla de presentación sobre el lienzo de 1024 × 600. `still` la dibuja quieta en su último
 * cuadro (capturas de la CI).
 */
@Composable
fun IntroOverlay(onDone: () -> Unit, still: Boolean = false) {
    val run = remember { Animatable(if (still) 1f else 0f) }
    val out = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (still || !RenderFlags.animate) return@LaunchedEffect
        run.animateTo(1f, tween(IntroTiming.TOTAL_MS, easing = LinearEasing))
        kotlinx.coroutines.delay(IntroTiming.HOLD_MS)
        out.animateTo(1f, tween(IntroTiming.FADE_MS))
        onDone()
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - out.value }
            .background(Brush.radialGradient(listOf(Color(0xFF141B2B), Color(0xFF05070C)), radius = 900f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Halo dorado detrás del emblema
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(420.dp, 200.dp).drawBehind {
                        val a = IntroTiming.phase(run.value, 0.15f, 0.5f) * 0.55f
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color(0xFFE8B63A).copy(alpha = a), Color.Transparent),
                                center = Offset(size.width / 2, size.height / 2), radius = size.width / 2
                            )
                        )
                    }
                )
                Image(
                    painterResource(R.drawable.chevrolet_emblem), "Chevrolet",
                    Modifier.size(270.dp, 90.dp)
                        .graphicsLayer {
                            val e = FastOutSlowInEasing.transform(IntroTiming.phase(run.value, 0f, 0.4f))
                            alpha = e
                            scaleX = 0.7f + 0.3f * e
                            scaleY = 0.7f + 0.3f * e
                            rotationY = 90f * (1f - e)          // gira hasta quedar de frente
                            cameraDistance = 12f * density
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            val s = IntroTiming.phase(run.value, 0.4f, 0.7f)   // brillo que cruza el emblema
                            if (s > 0f && s < 1f) {
                                val x = -size.width * 0.5f + size.width * 2f * s
                                drawRect(
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                                        start = Offset(x - size.width * 0.3f, 0f), end = Offset(x + size.width * 0.3f, size.height)
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(8.dp))
            // Línea que se abre desde el centro
            Box(
                Modifier.width(320.dp).height(2.dp).drawBehind {
                    val w = size.width * FastOutSlowInEasing.transform(IntroTiming.phase(run.value, 0.5f, 0.75f))
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFFE8B63A), Color.Transparent),
                            startX = (size.width - w) / 2, endX = (size.width + w) / 2
                        ),
                        topLeft = Offset((size.width - w) / 2, 0f),
                        size = androidx.compose.ui.geometry.Size(w, size.height)
                    )
                }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "ONIX LAUNCHER",
                color = Color(0xFFDCE2EC), fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 9.sp,
                modifier = Modifier.graphicsLayer {
                    val t = FastOutSlowInEasing.transform(IntroTiming.phase(run.value, 0.6f, 1f))
                    alpha = t
                    translationY = 14f * density * (1f - t)
                    scaleX = 1.08f - 0.08f * t
                }
            )
        }
    }
}
