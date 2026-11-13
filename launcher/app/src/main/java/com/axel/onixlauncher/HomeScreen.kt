package com.axel.onixlauncher

import android.content.Context
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

private val Dias = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
private val Meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/*
 * Pantalla de inicio (diseño de v0.25.1, según la imagen de referencia del autor): fondo según
 * hora y clima, ruta y tu Onix al centro; reloj y clima arriba a la izquierda, pronóstico arriba a
 * la derecha, música y navegación a la izquierda, telemetría a la derecha, dock y franja abajo.
 * Los paneles se pueden mover y escalar (mantener apretado el fondo).
 */

@Composable
internal fun HomeScreen(
    now: Calendar,
    ambient: Ambient,
    lightsOn: Boolean,
    media: MediaResult,
    speed: Int?,
    weather: Weather?,
    obd: ObdData? = null,
    place: String? = null,
    editing: Boolean = false,
    onEditingChange: (Boolean) -> Unit = {},
    onRadios: () -> Unit = {},
    onHome: () -> Unit = {},
    onApps: () -> Unit = {},
    onNav: () -> Unit = {},
    lightsMode: LightsMode = LightsMode.RADIO,
    obdCodes: ObdCodes? = ObdState.codes.value,
    onDiag: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LayoutStore(context) }
    var name by remember { mutableStateOf(GreetingName.load(context)) }
    var askName by remember { mutableStateOf(false) }
    var showCodes by remember { mutableStateOf(false) }
    Box(Modifier.size(1024.dp, 600.dp)) {
        // Fondo de la imagen de referencia (cielo, cerros, ciudad y piso), en su versión de día,
        // atardecer, noche o lluvia, con transición suave
        Crossfade(targetState = ambient, animationSpec = tween(1500), label = "fondo") { a ->
            Image(
                painterResource(backgroundFor(a)), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        // Velos: arriba para el reloj y el pronóstico, abajo para el dock
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(Color(0x8C000000), Color.Transparent)))
        )
        Box(
            Modifier.offset(0.dp, 400.dp).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3050816), Color(0xF2050816))))
        )

        // Mantener apretado el fondo: modo para mover y escalar los paneles
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { onEditingChange(true) })
        })

        // Reloj, fecha y clima de ahora
        HeaderCard(now, weather, place, Modifier.at(20, 14, 452, 122))

        // Pronóstico de los próximos 5 días
        EditablePanel("pronostico", PanelPos(664f, 24f, 1f), 340, 102, editing, store) {
            ForecastCard(weather, Modifier.fillMaxSize())
        }

        // Sombra bajo el auto
        Canvas(Modifier.at(392, 414, 372, 34)) {
            scale(1f, size.height / size.width) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xF2000000), Color(0x99000000), Color(0x00000000))),
                    radius = size.width / 2f
                )
            }
        }
        // Temas festivos (v0.65.0; v0.66.0: decoran el auto)
        remember { Festive.load(context); true }
        val holiday = Festive.current(now)
        // Tu Onix: la foto se tiñe según la luz del ambiente y los faros se prenden solos
        val glowAlpha by animateFloatAsState(if (lightsOn) 1f else 0f, tween(800), label = "luces")
        // Misma caja que el auto de las imágenes de referencia; el tono cambia suave con el ambiente
        val carTint by androidx.compose.animation.animateColorAsState(carTintFor(ambient), tween(1500), label = "tono")
        Box(Modifier.at(380, 182, 391, 256)) {
            Image(
                painterResource(R.drawable.onix_car), "Tu Onix",
                Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(carTint, BlendMode.Modulate)
            )
            Image(
                painterResource(R.drawable.onix_glow), null,
                Modifier.fillMaxSize().alpha(glowAlpha), contentScale = ContentScale.FillBounds
            )
            if (holiday != null) CarDecor(holiday, animate = !LiteMode.isOn && RenderFlags.animate, ambient = ambient)
        }
        if (ambient.raining && !LiteMode.isOn) RainLayer()   // modo liviano: sin lluvia animada (v0.58.0)
        // Temas festivos: detalle en el cielo según la fecha; quieto con el modo liviano
        if (holiday != null) FestiveLayer(holiday, animate = !LiteMode.isOn && RenderFlags.animate, ambient = ambient)

        EditablePanel("musica", PanelPos(20f, 146f, 1f), 322, 190, editing, store) {
            MediaCard(media, Modifier.fillMaxSize(), onRadios)
        }
        EditablePanel("navegacion", PanelPos(20f, 344f, 1f), 322, 106, editing, store) {
            NavCard(Modifier.fillMaxSize(), onNav)
        }
        EditablePanel("motor", PanelPos(772f, 140f, 1f), 232, 310, editing, store) {
            EnginePanel(speed, obd, Modifier.fillMaxSize(), obdCodes, onCodes = { if (!editing) showCodes = true })
        }

        Dock(onDiag, Modifier.at(20, 462, 984, 88), onRadios, homeSelected = true, onHome = onHome, onApps = onApps, onNav = onNav)
        VehicleStrip(
            lightsOn, lightsMode, ambient,
            "Hola, $name · " + greetingFor(now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f),
            Modifier.at(24, 556, 976, 40),
            obd = obd,
            onDiag = onDiag,
            onGreeting = { askName = true }
        )
        if (editing) EditBar(store) { onEditingChange(false) }
        if (showCodes) CodesDialog(obdCodes, speed, onClose = { showCodes = false })
        if (askName) NameDialog(
            current = name,
            recent = GreetingName.recent(context),
            onPick = { picked -> GreetingName.save(context, picked)?.let { name = it }; askName = false },
            onClose = { askName = false }
        )
    }
}


/**
 * Emblema de Chevrolet animado: cada 9 s da una vuelta en 3D sobre su eje (1,2 s) y después le
 * pasa un brillo, como un emblema cromado. Entre animaciones no se redibuja nada (v0.35.1: antes
 * redibujaba la pantalla en cada cuadro y hacía lenta la radio). Sin animaciones, se dibuja fijo.
 */
@Composable
private fun AnimatedEmblem(modifier: Modifier) {
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    val shine = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!RenderFlags.animate || LiteMode.isOn) return@LaunchedEffect   // modo liviano: emblema fijo
        while (true) {
            kotlinx.coroutines.delay(7_500)
            turn.snapTo(0f)
            turn.animateTo(1f, tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            shine.snapTo(0f)
            shine.animateTo(1f, tween(1300, easing = LinearEasing))
            shine.snapTo(0f)
        }
    }
    Image(
        painterResource(R.drawable.chevrolet_emblem), "Chevrolet",
        modifier
            .graphicsLayer {
                rotationY = 360f * turn.value          // se lee al dibujar: no recompone
                cameraDistance = 12f * density
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val p = shine.value
                if (p > 0f && p < 1f) {
                    val x = -size.width * 0.5f + size.width * 2f * p
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                            start = Offset(x - size.width * 0.35f, 0f), end = Offset(x + size.width * 0.35f, size.height)
                        ),
                        blendMode = BlendMode.SrcAtop
                    )
                }
            },
        contentScale = ContentScale.Fit
    )
}

private val Legible = TextStyle(shadow = Shadow(Color(0xCC000000), Offset(0f, 1f), 6f))

/** Reloj (12 h con AM/PM, como la imagen de referencia), fecha y el clima de ahora con la ubicación. */
@Composable
private fun HeaderCard(now: Calendar, weather: Weather?, place: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xD90B1120), Color(0xA60B1120))))
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(196.dp)) {
            val h12 = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.US, "%d:%02d", h12, now.get(Calendar.MINUTE)),
                    color = TextMain, fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 66.sp, letterSpacing = (-1).sp
                )
                Text(
                    if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM",
                    color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 3.dp, bottom = 12.dp)
                )
            }
            Text(
                "${Dias[now.get(Calendar.DAY_OF_WEEK) - 1]}, ${now.get(Calendar.DAY_OF_MONTH)} de ${Meses[now.get(Calendar.MONTH)]}. ${now.get(Calendar.YEAR)}",
                color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
        }
        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(92.dp).background(Color(0x33FFFFFF)))
        Crossfade(targetState = weather, animationSpec = tween(600), label = "clima", modifier = Modifier.weight(1f)) { w ->
            if (w == null) {
                Text("Clima\nsin datos", color = Muted, fontSize = 17.sp, lineHeight = 21.sp)
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                            Canvas(Modifier.size(56.dp)) { weatherIcon(w.code, w.isDay) }
                            Text(w.shortLabel, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${w.tempC}°", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                            if (w.minC != null && w.maxC != null) {
                                Text("${w.minC}° / ${w.maxC}°", color = TextMain, fontSize = 16.sp, maxLines = 1)
                            }
                        }
                    }
                    if (place != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Canvas(Modifier.size(16.dp)) { iconPin() }
                            Spacer(Modifier.width(5.dp))
                            Text(place, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Pronóstico de los próximos días (hasta 5): día, ícono, mínima y máxima. */
@Composable
internal fun ForecastCard(weather: Weather?, modifier: Modifier) {
    val days = weather?.days.orEmpty().take(5)
    Row(modifier.card().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (days.isEmpty()) {
            Text("Pronóstico sin datos", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
        }
        days.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.width(1.dp).height(70.dp).background(Color(0x1FFFFFFF)))
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(d.day.lowercase().replaceFirstChar { it.uppercase() }, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Canvas(Modifier.size(32.dp)) { weatherIcon(d.code, true) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${d.minC}°", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(" / ${d.maxC}°", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}

/**
 * Franja inferior: emblema y modelo a la izquierda; luces a la derecha. El faro se pinta de azul
 * cuando las luces están prendidas. Tocar "Luces" cambia de dónde salen (según la radio, encendidas,
 * apagadas o por hora, ver Lights.kt); tocar el sol/luna y la flecha abre Diagnóstico.
 */
@Composable
private fun VehicleStrip(
    lightsOn: Boolean,
    mode: LightsMode,
    ambient: Ambient,
    greeting: String,
    modifier: Modifier,
    obd: ObdData? = null,
    onDiag: () -> Unit,
    onGreeting: () -> Unit = {}
) {
    val context = LocalContext.current
    val glow by animateFloatAsState(if (lightsOn) 1f else 0f, tween(800), label = "indicador")
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager }
    val wifiConnected by androidx.compose.runtime.produceState(initialValue = false, NetWatch.back.intValue) {
        while (true) {
            val info = cm?.activeNetworkInfo
            value = info != null && info.isConnected
            kotlinx.coroutines.delay(4000)
        }
    }
    val obdConnected = obd != null || ObdState.status.value.startsWith("conectado") || ObdState.status.value.startsWith("leyendo")
    Box(modifier) {
        // Saludo según la hora, centrado en la franja (v0.39.0)
        Text(
            greeting, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = Legible,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onGreeting).padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedEmblem(Modifier.size(66.dp, 22.dp))
            Spacer(Modifier.width(14.dp))
            Text("ONIX JOY", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, style = Legible)
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(16.dp).background(Color(0x2EFFFFFF)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(15.dp)) { iconWifi(if (wifiConnected) Color(0xFF59E08A) else Color(0x66FFFFFF)) }
                Spacer(Modifier.width(4.dp))
                Text(
                    if (wifiConnected) "Wi-Fi" else "Sin red",
                    color = if (wifiConnected) Color(0xFFE4EAF5) else Muted,
                    fontSize = 12.sp,
                    style = Legible
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (obdConnected) Color(0xFF59E08A) else Color(0x66FFFFFF))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "OBD2",
                    color = if (obdConnected) Color(0xFFE4EAF5) else Muted,
                    fontSize = 12.sp,
                    style = Legible
                )
            }
            Spacer(Modifier.weight(1f))
            // Solo muestra el estado: las luces siguen a las del auto (v0.50.0). El modo manual,
            // para casos raros, está en Diagnóstico.
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val lampColor = androidx.compose.ui.graphics.lerp(Color(0xFF8A94A8), Color(0xFF4C8DFF), glow)
                Canvas(Modifier.size(26.dp)) { iconHeadlight(lampColor) }
                Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(22.dp).background(Color(0x33FFFFFF)))
                Text(lightsLabel(lightsOn, mode), color = TextMain, fontSize = 15.sp, style = Legible)
            }
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDiag).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(22.dp)) { iconSunMoon(night = ambient.isNight) }
                Spacer(Modifier.width(14.dp))
                Canvas(Modifier.size(18.dp)) { iconChevronRight(Muted) }
            }
        }
    }
}

@Composable
private fun RainLayer() {
    val t = if (RenderFlags.animate) {
        rememberInfiniteTransition(label = "lluvia").animateFloat(
            0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "gotas"
        ).value
    } else 0f
    Canvas(Modifier.size(1024.dp, 600.dp)) {
        val k = size.width / 1024f
        for (i in 0 until 90) {
            val x = ((i * 89 + 13) % 1040).toFloat()
            val y = (((i * 131) % 600) + t * 600f) % 640f - 40f
            drawLine(
                Color(0x66C8D6EA),
                Offset((x - 6f) * k, y * k), Offset(x * k, (y + 24f) * k),
                strokeWidth = 1.5f * k
            )
        }
    }
}

/** Fila de controles: aleatorio, anterior, play/pausa (aro), siguiente y "me gusta". */
@Composable
internal fun TransportRow(
    playing: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    shuffle: (() -> Unit)?,
    like: (() -> Unit)?,
    liked: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTap(40, shuffle) { iconShuffle() }
        IconTap(44, onPrev) { iconPrev() }
        Box(
            Modifier.size(50.dp).clip(CircleShape).border(2.dp, Color(0xE6FFFFFF), CircleShape)
                .background(Color(0x1AFFFFFF)).clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) { Canvas(Modifier.size(22.dp)) { if (playing) iconPause(Color.White) else iconPlay(Color.White) } }
        IconTap(44, onNext) { iconNext() }
        IconTap(40, like) { iconHeart(liked) }
    }
}

/** Ícono tocable sin fondo; si [onClick] es null queda apagado (la app que suena no ofrece esa acción). */
@Composable
private fun IconTap(size: Int, onClick: (() -> Unit)?, icon: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (onClick != null) 1f else 0.3f),
        contentAlignment = Alignment.Center
    ) { Canvas(Modifier.size((size * 0.55f).dp)) { icon() } }
}

/** Tapa del disco (o logo de la radio) cuadrada con esquinas redondeadas. */
@Composable
internal fun CoverArt(art: ImageBitmap?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B4C8C), Color(0xFF16213D)))),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.size((size * 0.4f).dp)) { iconMusic() }
    }
}

/**
 * Botón para cambiar de fuente de música (Radio ↔ Spotify), con el ícono de la app si está.
 * v0.81.3: más grande y con borde de acento para acertarle con el auto en movimiento.
 */
@Composable
internal fun SwitchChip(text: String, pkg: String?, onClick: () -> Unit) {
    val icon = rememberAppIcon(pkg)
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Accent.copy(alpha = 0.18f))
            .border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        } else if (pkg == null) {
            Canvas(Modifier.size(18.dp)) { iconRadio() }
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Ícono y nombre de la app que suena (los de la app instalada). */
@Composable
internal fun SourceRow(pkg: String?) {
    val context = LocalContext.current
    val icon = rememberAppIcon(pkg)
    val name = remember(pkg) { pkg?.let { appLabel(context, it) } ?: sourceLabel(pkg) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(name, color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MediaCard(media: MediaResult, modifier: Modifier, onRadios: () -> Unit = {}) {
    val context = LocalContext.current
    // Si suena la radio de la app, la tarjeta la muestra (sin necesitar acceso a notificaciones)
    if (showRadioCard(media, context.packageName)) {
        RadioCard(modifier, onRadios)
        return
    }
    Column(modifier.card().padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (!media.hasAccess) {
            Text("Música", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Para ver lo que suena en Spotify, dale a la app acceso a las notificaciones.",
                color = TextMain, fontSize = 16.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.weight(1f))
            PillButton("Dar acceso", Accent, OnAccent, Modifier.fillMaxWidth()) {
                startSafe(context, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            return@Column
        }
        val info = media.info
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(info?.art?.asImageBitmap(), 82)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SourceRow(info?.pkg) }
                    // Volver a la radio: pausa esta app y sigue la radio (o abre el panel de radios)
                    SwitchChip("Radio", null) {
                        if (info?.playing == true) info.controller?.transportControls?.pause()
                        if (radioActive()) Radio.toggle(context) else onRadios()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    info?.title ?: "Nada sonando", color = TextMain, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    info?.artist ?: "Abrí Spotify y dale play", color = Muted, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val dur = info?.durationMs ?: 0L
        val pos = rememberPlaybackPosition(info)
        val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF2F6BFF), Color(0xFF4CB0FF)))))
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = Muted, fontSize = 13.sp)
            Text(fmt(dur), color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        val c = info?.controller
        val tc = c?.transportControls
        val shuffle = c?.customAction(*ShuffleKeys)
        val like = c?.customAction(*LikeKeys)
        TransportRow(
            playing = info?.playing == true,
            // Sin sesión (algunas apps de Bluetooth): teclas de música (v0.64.0)
            onPrev = { tc?.skipToPrevious() ?: mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
            onPlayPause = {
                when {
                    tc == null -> mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    info?.playing == true -> { tc.pause(); LastAudio.userPaused(context) }
                    else -> tc.play()
                }
            },
            onNext = { tc?.skipToNext() ?: mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT) },
            shuffle = shuffle?.let { a -> { tc?.sendCustomAction(a, null) } },
            like = like?.let { a -> { tc?.sendCustomAction(a, null) } },
            liked = false
        )
    }
}

/**
 * Navegación propia (v0.37.0): tocar la tarjeta abre el mapa; Casa y Trabajo calculan la ruta
 * directo. Sin Google Maps ni Waze.
 */
@Composable
private fun NavCard(modifier: Modifier, onNav: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SavedPlaces.load(context) }
    Column(modifier.card().clickable(onClick = onNav).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1E4FB8)))),
                contentAlignment = Alignment.Center
            ) { Canvas(Modifier.size(24.dp)) { iconPin() } }
            Column {
                Text("Navegación", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("¿A dónde vamos?", color = Muted, fontSize = 15.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((label, primary) in listOf("Casa" to true, "Trabajo" to false)) {
                NavButton(label, primary, null, { iconHome() }, Modifier.weight(1f)) {
                    val p = if (primary) SavedPlaces.home.value else SavedPlaces.work.value
                    // v0.77.0: sin guardar, abre Mis lugares para escribir la dirección
                    if (p == null) NavRequest.editPlaces.value = true
                    else { NavRequest.pending.value = p; onNav() }
                }
            }
        }
    }
}

/** Botón de navegación: el principal con degradé azul, el otro oscuro con borde; con ícono. */
@Composable
private fun NavButton(
    text: String,
    primary: Boolean,
    appIcon: ImageBitmap?,
    icon: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.height(40.dp).clip(shape)
            .background(
                if (primary) Brush.horizontalGradient(listOf(Color(0xFF2F7BFF), Color(0xFF4CA6FF)))
                else Brush.horizontalGradient(listOf(Color(0xFF162036), Color(0xFF162036)))
            )
            .then(if (primary) Modifier else Modifier.border(1.dp, CardBorder, shape))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) Image(appIcon, null, Modifier.size(22.dp))
        else if (icon != null) Canvas(Modifier.size(18.dp)) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

private fun sourceLabel(pkg: String?): String = when (pkg) {
    "com.spotify.music" -> "Spotify"
    "com.axel.onixlauncher" -> "Radio"
    "com.google.android.apps.youtube.music" -> "YouTube Music"
    null, "" -> "Música"
    else -> "Reproduciendo"
}

internal fun fmt(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** Tarjeta de la radio de la app, con el mismo diseño que la de música. Tocarla abre la radio. */
@Composable
private fun RadioCard(modifier: Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val station = radioStation()
    // Favoritas cargadas antes de usar el corazón (si no, marcar una borraría las demás)
    androidx.compose.runtime.LaunchedEffect(Unit) { RadioFavorites.load(context) }
    Column(modifier.card().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StationLogo(station, 82)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        radioLabel(station), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    // Pasar a Spotify: pausa la radio y le da play a Spotify (o lo abre si está cerrado)
                    SwitchChip("Spotify", SPOTIFY) {
                        if (RadioState.playing.value || RadioState.buffering.value) Radio.toggle(context)
                        spotifyPlay(context)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    station.name, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveDot()
                    Text(radioStatus(), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // En vivo: barra llena, sin duración
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF2F6BFF), Color(0xFF4CB0FF)))))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("En vivo", color = Muted, fontSize = 13.sp)
            Text(station.freq?.let { String.format(Locale.US, "%.1f FM", it) } ?: "", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        val playing = RadioState.playing.value || RadioState.buffering.value
        TransportRow(
            playing = playing,
            onPrev = { Radio.step(context, up = false) },
            onPlayPause = {
                if (RadioState.playing.value || RadioState.buffering.value) LastAudio.userPaused(context)
                Radio.toggle(context)
            },
            onNext = { Radio.step(context, up = true) },
            shuffle = null,
            like = { RadioFavorites.toggle(context, station.name) },
            liked = station.name in RadioFavorites.names.value
        )
    }
}

/** Elegir o escribir el nombre del saludo (v0.40.0): los últimos usados de un toque, o uno nuevo. */
@Composable
private fun NameDialog(current: String, recent: List<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF20B1120))
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp)
        ) {
            Text("¿A quién saludo?", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Abajo: sonido de la presentación y temas festivos", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in recent) {
                    val selected = n.equals(current, ignoreCase = true)
                    Text(
                        n, color = if (selected) Color.White else TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Accent else Color(0x22FFFFFF))
                            .clickable { onPick(n) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color(0x22FFFFFF))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Otro nombre…", color = Muted, fontSize = 18.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = text, onValueChange = { text = it.take(20) }, singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 18.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (text.isNotBlank()) onPick(text) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Guardar", color = if (text.isBlank()) Muted else Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = text.isNotBlank()) { onPick(text) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            // Presentación y fiestas (v0.65.0 / v0.66.0)
            Spacer(Modifier.height(14.dp))
            val ctx = LocalContext.current
            remember { IntroSound.load(ctx); Festive.load(ctx); true }
            var previewLabel by remember { mutableStateOf("Ver tema: " + (Festive.preview.value?.label ?: "el de hoy")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(IntroSound.label(), Track, TextMain, Modifier.width(280.dp)) { IntroSound.cycle(ctx) }
                PillButton(Festive.label(), Track, TextMain, Modifier.width(188.dp)) { Festive.toggle(ctx) }
            }
            Spacer(Modifier.height(8.dp))
            PillButton(previewLabel, Track, TextMain, Modifier.width(476.dp)) { previewLabel = Festive.nextPreview() }
            Spacer(Modifier.height(10.dp))
            Text(
                "Cancelar", color = Muted, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** Texto de las luces en la franja de Inicio (v0.50.0): el estado; si se eligió un modo manual, lo dice. */
internal fun lightsLabel(lightsOn: Boolean, mode: LightsMode): String =
    (if (lightsOn) "Luces prendidas" else "Luces apagadas") + if (mode == LightsMode.RADIO) "" else " (${mode.label})"
