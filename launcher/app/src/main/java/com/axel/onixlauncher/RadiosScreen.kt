package com.axel.onixlauncher

import java.util.Locale
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// Paleta de estéreo de auto: panel negro, display ámbar
private val Amber = Color(0xFFFFB238)
private val AmberDim = Color(0x55FFB238)
private val AmberOff = Color(0x22FFB238)
private val Lcd = Color(0xFF0D0B06)
private val Panel = Color(0xFF0C0E16)
private val Edge = Color(0xFF2A2F44)
private val Needle = Color(0xFFFF3B30)
private val RadioMuted = Color(0xFF8C93AB)

private const val F_MIN = 87.5f
private const val F_MAX = 108f

private val ScalePaint = android.graphics.Paint().apply {
    textSize = 17f
    textAlign = android.graphics.Paint.Align.CENTER
    isAntiAlias = true
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}
private val MarkPaint = android.graphics.Paint().apply {
    textSize = 12f
    textAlign = android.graphics.Paint.Align.CENTER
    isAntiAlias = true
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

/** Pantalla de radio sobre el inicio: carga las emisoras y maneja la reproducción. */
@Composable
internal fun RadiosOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        RadioCatalog.load(context)
        RadioCatalog.startCheck(context)
    }
    // Android 13+: permiso para mostrar los controles de la radio en la notificación
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        // Se pide una sola vez; si el usuario lo rechaza, no se vuelve a molestar (AUD-025)
        val prefs = context.getSharedPreferences("permisos", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notificaciones_pedido", false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("notificaciones_pedido", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Por la antena andan todas las del dial (v0.51.0); por internet, solo las que funcionan
    val antenna = Radio.onAntenna()
    val all = if (antenna) TucumanFm.stations else RadioCatalog.visible(RadioFavorites.names.value)
    val progress = RadioCatalog.progress.value
    // Mientras está abierta, lee el nombre (RDS) de lo que suena por la antena
    LaunchedEffect(antenna) {
        while (antenna) { NwdTuner.refresh(); kotlinx.coroutines.delay(2000) }
    }
    LaunchedEffect(Unit) {
        Radio.loadMode(context)
        RadioFavorites.load(context)
    }
    FmRadioPanel(
        stations = all,
        loading = RadioCatalog.loading.value,
        checking = progress,
        hidden = if (antenna) 0 else RadioCatalog.stations.value.size - all.size,
        onCheck = { RadioCatalog.startCheck(context, force = true) },
        current = RadioState.current.value,
        offline = RadioState.offline.value,
        note = RadioState.note.value,
        pauseReason = RadioState.pauseReason.value,
        national = RadioState.national.value,
        onNationalChange = { Radio.setNational(context, it) },
        favorites = RadioFavorites.names.value,
        onToggleFavorite = { RadioFavorites.toggle(context, it) },
        playing = RadioState.playing.value,
        buffering = RadioState.buffering.value,
        onPlay = { Radio.play(context, it) },
        onStop = { Radio.stop(context) },
        onClose = onClose,
        antennaAvailable = NwdTuner.available.value,
        antenna = antenna,
        onAntennaChange = { on ->
            Radio.stop(context)
            NwdTuner.setAntenna(context, on)
        },
        onTuneFree = { mhz ->
            // Frecuencia sin emisora conocida: igual se sintoniza por la antena
            NwdTuner.tune(context, mhz)
            RadioState.station.value = null
            RadioState.current.value = null
            RadioState.playing.value = true
            RadioState.note.value = ANTENNA_NOTE
        },
        onSeekAntenna = { up -> NwdTuner.seek(context, up) },
        rds = NwdTuner.station.value?.rds?.ifBlank { null },
        tunerMhz = NwdTuner.station.value?.takeIf { TunerMath.isFm(it.band) }?.mhz
    )
}

/**
 * Radio con interfaz de FM. Las emisoras con frecuencia se ubican en el dial; al dejar la aguja
 * sobre una, sintoniza y suena. Las que no tienen frecuencia aparecen como presintonías "WEB".
 */
@Composable
internal fun FmRadioPanel(
    stations: List<Station>,
    loading: Boolean,
    checking: Pair<Int, Int>? = null,
    hidden: Int = 0,
    onCheck: () -> Unit = {},
    current: String?,
    playing: Boolean,
    buffering: Boolean,
    offline: String? = null,
    note: String? = null,
    pauseReason: String? = null,
    national: Boolean = false,
    onNationalChange: (Boolean) -> Unit = {},
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onPlay: (Station) -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    /** v0.51.0: hay sintonizador FM de la radio NWD; [antenna] = suena por la antena del auto. */
    antennaAvailable: Boolean = false,
    antenna: Boolean = false,
    onAntennaChange: (Boolean) -> Unit = {},
    /** Sintonizar una frecuencia sin emisora conocida (solo por la antena). */
    onTuneFree: (Float) -> Unit = {},
    /** Buscar la siguiente emisora con señal (solo por la antena, mantener apretado ⏮ ⏭). */
    onSeekAntenna: (Boolean) -> Unit = {},
    /** Nombre que transmite la emisora por RDS (por la antena). */
    rds: String? = null,
    /** Frecuencia que informa el sintonizador (después de buscar), para mover el dial. */
    tunerMhz: Float? = null
) {
    val onDial = remember(stations) { stations.filter { it.freq != null }.sortedBy { it.freq } }
    val currentStation = stations.firstOrNull { it.name == current }
    var freq by remember { mutableFloatStateOf(currentStation?.freq ?: onDial.firstOrNull()?.freq ?: 99.9f) }
    var dragging by remember { mutableStateOf(false) }
    // Filtro: todas, favoritas o una categoría. Afecta las presintonías y la búsqueda con ⏮ ⏭
    var filter by remember { mutableStateOf<String?>(null) } // null = todas, "fav", o el nombre de la categoría
    fun passes(s: Station) = when (filter) {
        null -> true
        "fav" -> s.name in favorites
        else -> categoryOf(s).name == filter
    }
    val inFilter = onDial.filter { passes(it) }
    val seekList = if (filter != null && inFilter.isNotEmpty()) inFilter else onDial
    val categories = remember(stations) { stations.groupBy { categoryOf(it) }.mapValues { it.value.size } }

    fun stationAt(f: Float) = onDial.firstOrNull { abs(it.freq!! - f) < 0.06f }

    fun tuneTo(f: Float) {
        val snapped = (f * 10).roundToInt() / 10f
        freq = snapped.coerceIn(F_MIN, F_MAX)
        val s = stationAt(freq)
        when {
            s != null && s.name != current -> onPlay(s)
            s == null && antenna -> onTuneFree(freq)
            s == null && current != null && currentStation?.freq != null -> onStop()
        }
    }

    // Por la antena, ⏮ ⏭ buscan en el aire la siguiente emisora con señal; el dial sigue al sintonizador
    LaunchedEffect(antenna, tunerMhz) {
        if (antenna && tunerMhz != null && !dragging) freq = tunerMhz
    }

    fun seek(up: Boolean) {
        if (antenna) return onSeekAntenna(up)
        if (seekList.isEmpty()) return
        val next = if (up) seekList.firstOrNull { it.freq!! > freq + 0.05f } ?: seekList.first()
        else seekList.lastOrNull { it.freq!! < freq - 0.05f } ?: seekList.last()
        freq = next.freq!!
        if (next.name != current) onPlay(next)
    }

    val tuned = stationAt(freq)
    val webStation = currentStation?.takeIf { it.freq == null }
    val showing = webStation ?: tuned
    val status = when {
        showing == null && antenna && rds != null -> "EN VIVO · ANTENA"
        showing == null -> "SIN SEÑAL"
        showing.name == offline -> "SIN TRANSMISIÓN ONLINE"
        showing.name != current -> "SINTONIZANDO"
        buffering -> "CONECTANDO"
        playing -> if (note != null) "EN VIVO · $note" else "EN VIVO"
        else -> pauseReason?.let { "EN PAUSA · $it" } ?: "EN PAUSA"
    }

    Box(
        Modifier.size(1024.dp, 600.dp).background(Color(0xE6040612))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(
            Modifier.offset(40.dp, 28.dp).size(944.dp, 548.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF161A28), Panel)))
                .border(1.dp, Edge, RoundedCornerShape(26.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RADIO FM", color = Color(0xFFE8EAF2), fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        loading -> "buscando emisoras…"
                        checking != null -> "revisando… ${checking.first} de ${checking.second}" +
                            (RadioCatalog.checkingNow.value?.let { " · $it" } ?: "")
                        antenna -> "${stations.size} emisoras · por la antena del auto"
                        hidden > 0 -> "${stations.size} funcionan · $hidden ocultas sin señal"
                        else -> "${stations.size} emisoras · por internet"
                    },
                    color = RadioMuted, fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                if (antennaAvailable) {
                    SourceSwitch(antenna, onAntennaChange)
                    Spacer(Modifier.width(12.dp))
                }
                if (checking == null && !antenna) {
                    Box(
                        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF1A1E2C))
                            .border(1.dp, Edge, RoundedCornerShape(21.dp)).clickable(onClick = onCheck)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("REVISAR", color = RadioMuted, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
                    Spacer(Modifier.width(12.dp))
                }
                if (!antenna) {
                    ModeSwitch(national, onNationalChange)
                    Spacer(Modifier.width(12.dp))
                }
                Box(
                    Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF262B40))
                        .clickable(onClick = onClose).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cerrar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }

            // Display LCD
            Row(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).background(Lcd)
                    .border(2.dp, Color(0xFF2A2412), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicadores
                Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LcdTag(if (webStation != null) "WEB" else if (antenna) "ANT" else "FM", true)
                    LcdTag("ST", playing && showing != null)
                    SignalBars(if (showing == null) 0 else if (playing) 5 else if (buffering) 2 else 3)
                }
                // Frecuencia
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    Text(
                        if (webStation != null) "WEB" else String.format(Locale.US, "%.1f", freq),
                        color = Amber, fontSize = 92.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, lineHeight = 94.sp
                    )
                    Text(
                        if (webStation != null) "" else " MHz", color = AmberDim, fontSize = 26.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 18.dp)
                    )
                }
                // Logo, emisora y estado
                if (showing != null) {
                    StationLogo(showing, 76)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.width(230.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        showing?.name ?: rds ?: "— — —", color = Amber, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace, lineHeight = 26.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        status, color = if (showing == null) AmberDim else Amber, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        showing?.detail ?: "Mové el dial", color = AmberDim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Marcar o desmarcar como favorita
                if (showing != null) {
                    val fav = showing.name in favorites
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(if (fav) Color(0x33FFB238) else Color.Transparent)
                            .border(1.5.dp, if (fav) Amber else AmberOff, CircleShape)
                            .clickable { onToggleFavorite(showing.name) },
                        contentAlignment = Alignment.Center
                    ) { Canvas(Modifier.size(26.dp)) { iconStar(if (fav) Amber else AmberDim, fav) } }
                }
            }

            // Dial: tocá o arrastrá para sintonizar
            Box(
                Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B1F2E), Color(0xFF0F121C))))
                    .border(1.dp, Edge, RoundedCornerShape(14.dp))
                    .pointerInput(onDial, current) {
                        val pad = 28f * density
                        fun toFreq(x: Float) = F_MIN + ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f) * (F_MAX - F_MIN)
                        detectTapGestures { tuneTo(toFreq(it.x)) }
                    }
                    .pointerInput(onDial, current) {
                        val pad = 28f * density
                        val perPx = (F_MAX - F_MIN) / (size.width - 2 * pad)
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false; tuneTo(freq) },
                            onDragCancel = { dragging = false }
                        ) { change, dx ->
                            change.consume()
                            freq = (freq + dx * perPx).coerceIn(F_MIN, F_MAX)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) { drawDial(freq, onDial, current) }
            }

            // Categorías
            LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip("TODAS ${stations.size}", filter == null) { filter = null } }
                item { FilterChip("★ FAVORITAS ${stations.count { it.name in favorites }}", filter == "fav") { filter = if (filter == "fav") null else "fav" } }
                for (c in RadioCategory.values()) {
                    val n = categories[c] ?: 0
                    if (n > 0) item { FilterChip("${c.label.uppercase()} $n", filter == c.name) { filter = if (filter == c.name) null else c.name } }
                }
            }

            // Controles y presintonías
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RoundKey(64, Color(0xFF262B40), { seek(false) }) { iconPrev() }
                Spacer(Modifier.width(12.dp))
                RoundKey(76, if (current != null) Needle else Color(0xFF3A4060), {
                    if (current != null) onStop() else (tuned ?: onDial.firstOrNull())?.let {
                        freq = it.freq ?: freq
                        onPlay(it)
                    }
                }) { iconPower() }
                Spacer(Modifier.width(12.dp))
                RoundKey(64, Color(0xFF262B40), { seek(true) }) { iconNext() }
                Spacer(Modifier.width(20.dp))
                // Favoritas primero; mantener apretada una presintonía la marca o desmarca
                val presets = stations
                    .filter { passes(it) }
                    .sortedWith(compareBy({ it.name !in favorites }, { it.url.isEmpty() }, { it.freq ?: 999f }))
                if (presets.isEmpty()) {
                    Text(
                        if (filter == "fav") "Todavía no hay favoritas: tocá la estrella del display o mantené apretada una presintonía."
                        else "No hay radios en esta categoría.",
                        color = RadioMuted, fontSize = 15.sp, modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(presets) { s ->
                            Preset(s, s.name == current, s.name in favorites, onLongPress = { onToggleFavorite(s.name) }) {
                                s.freq?.let { freq = it }
                                if (s.name != current) onPlay(s)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Interruptor ANTENA / INTERNET (v0.51.0): de dónde sale la radio. Solo aparece en la radio NWD. */
@Composable
private fun SourceSwitch(antenna: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF1A1E2C))
            .border(1.dp, Edge, RoundedCornerShape(21.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((label, value) in listOf("ANTENA" to true, "INTERNET" to false)) {
            val on = antenna == value
            Box(
                Modifier.fillMaxHeight().clip(RoundedCornerShape(17.dp))
                    .background(if (on) Amber else Color.Transparent)
                    .clickable { if (!on) onChange(value) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color(0xFF1A1205) else RadioMuted, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

/** Interruptor LOCAL / NACIONAL: cuál señal de cada cadena se busca primero. */
@Composable
private fun ModeSwitch(national: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF1A1E2C))
            .border(1.dp, Edge, RoundedCornerShape(21.dp)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((label, value) in listOf("LOCAL" to false, "NACIONAL" to true)) {
            val on = national == value
            Box(
                Modifier.fillMaxHeight().clip(RoundedCornerShape(17.dp))
                    .background(if (on) Amber else Color.Transparent)
                    .clickable { if (!on) onChange(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, color = if (on) Color(0xFF1A1205) else RadioMuted, fontSize = 14.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.2.sp
                )
            }
        }
    }
}

/** Escala de 87.5 a 108 MHz, marcas de emisoras y aguja. */
private fun DrawScope.drawDial(freq: Float, stations: List<Station>, current: String?) {
    val pad = 28.dp.toPx()
    val w = size.width - 2 * pad
    fun x(f: Float) = pad + (f - F_MIN) / (F_MAX - F_MIN) * w
    val base = size.height * 0.62f

    // Marcas cada 0.2 MHz, largas cada 1 MHz y números cada 2
    var f = 88f
    while (f <= 108.01f) {
        val whole = abs(f - f.roundToInt()) < 0.01f
        val len = if (whole) 22f else 10f
        drawLine(
            if (whole) Color(0xFFB8BED2) else Color(0xFF555C78),
            Offset(x(f), base), Offset(x(f), base - len),
            strokeWidth = if (whole) 2.5f else 1.5f
        )
        if (whole && f.roundToInt() % 2 == 0) {
            ScalePaint.color = 0xFFB8BED2.toInt()
            drawContext.canvas.nativeCanvas.drawText(f.roundToInt().toString(), x(f), base + 26f, ScalePaint)
        }
        f += 0.2f
    }
    drawLine(Color(0xFF555C78), Offset(pad, base), Offset(pad + w, base), strokeWidth = 1.5f)

    // Emisoras: marca ámbar arriba
    var activeLabel: Pair<Float, String>? = null
    for (s in stations) {
        val sx = x(s.freq!!)
        val on = s.name == current
        drawLine(if (on) Amber else AmberDim, Offset(sx, 8f), Offset(sx, if (on) 26f else 18f), strokeWidth = if (on) 4f else 2f)
        if (on) activeLabel = sx to s.name.take(18)
    }

    // Aguja con brillo
    val nx = x(freq)
    drawLine(Needle.copy(alpha = 0.3f), Offset(nx, 6f), Offset(nx, size.height - 6f), strokeWidth = 12f, cap = StrokeCap.Round)
    drawLine(Needle, Offset(nx, 6f), Offset(nx, size.height - 6f), strokeWidth = 3.5f, cap = StrokeCap.Round)

    // Nombre de la emisora activa en una cápsula sobre la aguja para que la línea roja no lo corte al medio
    activeLabel?.let { (sx, label) ->
        val tw = MarkPaint.measureText(label) + 14f
        drawRoundRect(
            Color(0xEE141824),
            Offset(sx - tw / 2f, 24f),
            androidx.compose.ui.geometry.Size(tw, 20f),
            androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        MarkPaint.color = 0xFFFFB238.toInt()
        drawContext.canvas.nativeCanvas.drawText(label, sx, 39f, MarkPaint)
    }
}

@Composable
private fun LcdTag(text: String, on: Boolean) {
    Box(
        Modifier.border(1.5.dp, if (on) Amber else AmberOff, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = if (on) Amber else AmberOff, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SignalBars(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..5) {
            Box(Modifier.size(6.dp, (6 + i * 4).dp).background(if (i <= level) Amber else AmberOff))
        }
    }
}

@Composable
private fun RoundKey(size: Int, bg: Color, onClick: () -> Unit, icon: DrawScope.() -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 1f), bg.copy(red = bg.red * 0.7f, green = bg.green * 0.7f, blue = bg.blue * 0.7f))))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Canvas(Modifier.size((size * 0.38f).dp)) { icon() } }
}

@Composable
private fun Preset(s: Station, active: Boolean, favorite: Boolean, onLongPress: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.width(196.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF2B2310) else Color(0xFF171B29))
            .border(1.5.dp, if (active) Amber else Edge, RoundedCornerShape(14.dp))
            .pointerInput(s.name) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() }) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(s, 52)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.freq?.let { String.format(Locale.US, "%.1f", it) } ?: "WEB",
                    color = if (active) Amber else Color(0xFFE8EAF2), fontSize = 21.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    Canvas(Modifier.size(16.dp)) { iconStar(Amber, true) }
                }
            }
            Text(
                s.name, color = if (active) Amber else RadioMuted, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp
            )
        }
    }
}

/** Botón de filtro (todas, favoritas o una categoría). */
@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(if (on) Amber else Color(0xFF1A1E2C))
            .border(1.dp, if (on) Amber else Edge, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (on) Color(0xFF1A1205) else Color(0xFFC9CEDD), fontSize = 14.sp,
            fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1
        )
    }
}

/** Ícono de encendido. */
private fun DrawScope.iconPower() {
    val s = size.width
    drawArc(
        Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(s * 0.15f, s * 0.18f), size = Size(s * 0.7f, s * 0.7f),
        style = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
    )
    drawLine(Color.White, Offset(s * 0.5f, s * 0.05f), Offset(s * 0.5f, s * 0.48f), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
}
