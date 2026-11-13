package com.axel.onixlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/*
 * Segunda pantalla (v0.34.0, reemplaza al Tablero): navegación y música sin salir del launcher.
 * Izquierda: mapa con buscador y ruta (MapNav.kt). Derecha: música con dos pestañas, Spotify
 * (controles de la app que suena) y Radio (emisora actual y lista para elegir).
 */

@Composable
internal fun NavMusicScreen(
    now: Calendar,
    media: MediaResult,
    weather: Weather?,
    onRadios: () -> Unit = {},
    onHome: () -> Unit = {},
    onApps: () -> Unit = {},
    onDiag: () -> Unit
) {
    val context = LocalContext.current
    var musicCollapsed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(1024.dp, 600.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1020), Color(0xFF04070F))))
    ) {
        // Barra superior: hora, mini-reproductor (cuando el panel está minimizado) y clima
        Row(Modifier.at(24, 10, 976, 40), verticalAlignment = Alignment.CenterVertically) {
            val h12 = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            Text(
                String.format(Locale.US, "%d:%02d", h12, now.get(Calendar.MINUTE)) +
                    (if (now.get(Calendar.AM_PM) == Calendar.AM) " AM" else " PM"),
                color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (musicCollapsed) {
                val info = media.info?.takeIf { it.pkg != context.packageName }
                val onRadio = radioActive() && (RadioState.playing.value || RadioState.buffering.value)
                val title = if (onRadio) radioStation().name else (info?.title ?: "Música")
                Row(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x284C8DFF))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(150.dp)
                    )
                    Text(
                        "⏮",
                        color = Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (onRadio) {
                                val list = RadioCatalog.stations.value
                                val idx = list.indexOfFirst { it.name == radioStation().name }
                                if (list.isNotEmpty()) Radio.play(context, list[if (idx > 0) idx - 1 else list.lastIndex])
                            } else {
                                info?.controller?.transportControls?.skipToPrevious()
                                    ?: mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            }
                        }.padding(horizontal = 4.dp)
                    )
                    Text(
                        if ((onRadio && RadioState.playing.value) || info?.playing == true) "❚❚" else "▶",
                        color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (onRadio) Radio.toggle(context)
                            else if (info?.playing == true) { info.controller?.transportControls?.pause(); LastAudio.userPaused(context) }
                            else info?.controller?.transportControls?.play() ?: spotifyPlay(context)
                        }.padding(horizontal = 4.dp)
                    )
                    Text(
                        "⏭",
                        color = Accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (onRadio) {
                                val list = RadioCatalog.stations.value
                                val idx = list.indexOfFirst { it.name == radioStation().name }
                                if (list.isNotEmpty()) Radio.play(context, list[if (idx in 0 until list.lastIndex) idx + 1 else 0])
                            } else {
                                info?.controller?.transportControls?.skipToNext()
                                    ?: mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                            }
                        }.padding(horizontal = 4.dp)
                    )
                    Text(
                        "❮ Música", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { musicCollapsed = false }.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            if (weather != null) {
                Canvas(Modifier.size(28.dp)) { weatherIcon(weather.code, weather.isDay) }
                Spacer(Modifier.width(6.dp))
                Text("${weather.tempC}°  ${weather.shortLabel}", color = TextMain, fontSize = 18.sp)
            }
        }
        MapPanel(Modifier.at(20, 56, if (musicCollapsed) 984 else 604, 436))
        if (!musicCollapsed) {
            MusicPanel(media, Modifier.at(636, 56, 368, 436), onRadios, onCollapse = { musicCollapsed = true })
        }
        Dock(onDiag, Modifier.at(20, 502, 984, 88), onRadios, onHome = onHome, onApps = onApps)
    }
}

/** Panel de música con pestañas Spotify / Radio y botón para minimizarlo (v0.79.19). */
@Composable
private fun MusicPanel(media: MediaResult, modifier: Modifier, onRadios: () -> Unit, onCollapse: () -> Unit = {}) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(if (radioActive() && (RadioState.playing.value || RadioState.buffering.value)) 1 else 0) }
    Column(modifier.card().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp)).background(Color(0x22FFFFFF))) {
                for ((i, label) in listOf("Spotify", "Radio").withIndex()) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(19.dp))
                            .background(if (tab == i) Accent else Color.Transparent).clickable { tab = i },
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (tab == i) OnAccent else TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0x22FFFFFF)).clickable { onCollapse() },
                contentAlignment = Alignment.Center
            ) {
                Text("❯", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) SpotifyTab(media) else RadioTab()
    }
}

/**
 * Spotify (o la app de música que suene): lo que suena con sus controles, y abajo la biblioteca de
 * Spotify (Listas, Recientes, Buscar) para elegir sin salir del launcher (Spotify.kt).
 */
@Composable
private fun SpotifyTab(media: MediaResult) {
    val context = LocalContext.current
    val info = media.info?.takeIf { it.pkg != context.packageName }
    Column(Modifier.fillMaxSize()) {
        if (!media.hasAccess) {
            Text("Para controlar Spotify desde acá, dale a la app acceso a las notificaciones.", color = TextMain, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            PillButton("Dar acceso", Accent, OnAccent, Modifier.fillMaxWidth()) {
                startSafe(context, android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(info?.art?.asImageBitmap(), 64)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info?.title ?: "Nada sonando", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(info?.artist ?: "Elegí algo de tu biblioteca", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val dur = info?.durationMs ?: 0L
                    val pos = rememberPlaybackPosition(info)
                    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
            }
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
                        info?.playing == true -> { tc?.pause(); LastAudio.userPaused(context) }
                        else -> {
                            if (RadioState.playing.value || RadioState.buffering.value) Radio.toggle(context)
                            if (tc != null) tc.play()
                            else spotifyPlay(context)
                        }
                    }
                },
                onNext = { tc?.skipToNext() ?: mediaKey(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT) },
                shuffle = shuffle?.let { a -> { tc?.sendCustomAction(a, null) } },
                like = like?.let { a -> { tc?.sendCustomAction(a, null) } },
                liked = false
            )
        }
        Spacer(Modifier.height(6.dp))
        SpotifyLibrary(info, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Biblioteca de Spotify: conectar (Client ID + permiso) y después Listas / Recientes / Buscar / Letra. */
@Composable
private fun SpotifyLibrary(info: MediaInfo?, modifier: Modifier) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { SpotifyAuth.refreshStatus(context) }
    val status = SpotifyAuth.status.value
    Column(modifier) {
        if (status != "conectado") {
            var id by remember { mutableStateOf(SpotifyAuth.clientId(context).orEmpty()) }
            // v0.79.6: si la app ya trae el Client ID (GitHub Secret), no hay nada que pegar
            val builtIn = SpotifyAuth.builtInClientId() != null
            Text("Tus listas, recientes y el buscador", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                if (status == "desconectado") {
                    if (builtIn) "Tocá Conectar y dá el permiso en Spotify."
                    else "Pegá el Client ID de tu app de Spotify y conectá (se guarda solo en este equipo)."
                } else status,
                color = Muted, fontSize = 13.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            if (!builtIn) SearchField(id, "Client ID", onChange = { id = it }, onDone = {})
            Spacer(Modifier.height(8.dp))
            PillButton("Conectar con Spotify", Accent, OnAccent, Modifier.fillMaxWidth()) {
                if (builtIn) {
                    SpotifyAuth.startLogin(context)
                } else if (id.trim().length >= 20) {
                    SpotifyAuth.setClientId(context, id)
                    SpotifyAuth.startLogin(context)
                } else {
                    SpotifyAuth.status.value = "El Client ID tiene 32 letras y números (developer.spotify.com → tu app → Settings)"
                }
            }
            return@Column
        }
        var tab by remember { mutableStateOf(0) }
        var items by remember { mutableStateOf<List<SpotifyItem>?>(null) }
        var query by remember { mutableStateOf("") }
        var recentQueries by remember { mutableStateOf(SpotifyApi.recentQueries(context)) }
        var note by remember { mutableStateOf<String?>(null) }
        // v0.81.17: lista abierta para elegir un tema (null = se ve la biblioteca)
        var opened by remember { mutableStateOf<SpotifyItem?>(null) }
        var tracks by remember { mutableStateOf<List<SpotifyItem>?>(null) }
        var tracksLoading by remember { mutableStateOf(false) }
        fun playItem(item: SpotifyItem) {
            if (RadioState.playing.value || RadioState.buffering.value) Radio.toggle(context)
            scope.launch {
                note = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpotifyApi.play(context, item) }
            }
        }
        fun open(item: SpotifyItem) {
            opened = item
            tracks = null
            tracksLoading = true
            scope.launch {
                val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpotifyApi.playlistTracks(context, item.uri) }
                if (opened == item) {
                    tracks = loaded
                    tracksLoading = false
                }
            }
        }
        fun load() {
            items = null
            scope.launch {
                val q = query.trim()
                if (tab == 2 && q.length >= 2) {
                    SpotifyApi.rememberQuery(context, q)
                    recentQueries = SpotifyApi.recentQueries(context)
                }
                items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    when (tab) {
                        0 -> SpotifyApi.playlists(context)
                        1 -> SpotifyApi.recent(context)
                        else -> if (q.length >= 2) SpotifyApi.search(context, q) else SpotifyApi.recent(context)
                    }
                }
            }
        }
        // v0.79.19: búsqueda en vivo (350 ms de pausa al escribir) y sugerencias de recientes al abrir Buscar
        // v0.81.21: también se pide de nuevo cuando vuelve la red (antes la lista quedaba vacía)
        LaunchedEffect(tab, query, NetWatch.back.intValue) {
            if (tab == 3) return@LaunchedEffect
            if (tab == 2 && query.trim().length >= 2) {
                kotlinx.coroutines.delay(350)
                load()
            } else if (tab != 2 || query.isBlank()) {
                load()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, label) in listOf("Listas", "Recientes", "Buscar", "Letra").withIndex()) {
                Box(
                    Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (tab == i) Color(0x554C8DFF) else Color(0x14FFFFFF)).clickable { tab = i; opened = null },
                    contentAlignment = Alignment.Center
                ) { Text(label, color = TextMain, fontSize = 13.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        if (tab == 3) {
            Spacer(Modifier.height(8.dp))
            LyricsPanel(info, Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        if (tab == 2) {
            Spacer(Modifier.height(6.dp))
            SearchField(query, "Tema, artista o lista (busca al escribir)", onChange = { query = it }, onDone = { load() })
            if (query.isBlank() && recentQueries.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rq in recentQueries.take(3)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x224C8DFF))
                                .clickable { query = rq }.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(rq, color = TextMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (query.trim().length < 2) {
                Spacer(Modifier.height(4.dp))
                Text("Escuchado hace poco en tu cuenta:", color = Muted, fontSize = 12.sp)
            }
        }
        note?.let { Text(it, color = Color(0xFFFFB86B), fontSize = 13.sp, maxLines = 2) }
        Spacer(Modifier.height(4.dp))
        val openList = opened
        if (openList != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x14FFFFFF))
                        .clickable { opened = null }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("‹ Listas", color = TextMain, fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
                Text(
                    openList.name, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Accent)
                        .clickable { playItem(openList) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("▶ Todo", color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(4.dp))
            val loaded = tracks
            when {
                tracksLoading -> Text("Cargando los temas…", color = Muted, fontSize = 14.sp)
                loaded == null -> Text("Spotify no deja ver los temas de esta lista. Con \"▶ Todo\" se reproduce igual.", color = Muted, fontSize = 13.sp, lineHeight = 16.sp)
                loaded.isEmpty() -> Text("La lista no tiene temas", color = Muted, fontSize = 14.sp)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(loaded.orEmpty()) { item -> SpotifyRow(item) { playItem(item) } }
            }
        } else {
        val list = items
        if (list == null) {
            Text("Cargando…", color = Muted, fontSize = 14.sp)
        } else if (list.isEmpty()) {
            Text(if (tab == 2) "Sin resultados para \"${query.trim()}\"" else if (WifiRescue.down) "Sin conexión a internet: las listas no se pueden cargar" else "No hay nada para mostrar", color = Muted, fontSize = 14.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(list.orEmpty()) { item ->
                // Una lista se abre para elegir el tema; lo demás se reproduce al tocarlo
                SpotifyRow(item) { if (SpotifyApi.playlistId(item.uri) != null) open(item) else playItem(item) }
            }
        }
        }
        // v0.81.18: "Desconectar Spotify" pasó a Ajustes → Sonido y avisos; su lugar lo usa la letra
        MiniLyricsBar(info, onClick = { tab = 3 }, Modifier.padding(top = 4.dp, bottom = 2.dp))
    }
}

/** Fila de la biblioteca de Spotify: portada, nombre y subtítulo. */
@Composable
private fun SpotifyRow(item: SpotifyItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotifyCover(item.imageUrl, 38)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Barra compacta de letra sincronizada en vivo (v0.81.5; más alta y con letra más grande desde v0.81.17, 20 sp desde v0.81.18): muestra al pie la estrofa actual
 * mientras se navegan listas, recientes o el buscador. Tocarla abre la pestaña Letra completa.
 */
@Composable
private fun MiniLyricsBar(info: MediaInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = info?.title
    val artist = info?.artist
    if (title.isNullOrBlank()) return
    var lines by remember(title, artist) { mutableStateOf<List<LyricLine>?>(null) }
    LaunchedEffect(title, artist, NetWatch.back.intValue) {
        lines = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Lyrics.fetch(title, artist)
        }
    }
    val current = lines
    if (current.isNullOrEmpty()) return
    val pos = rememberPlaybackPosition(info)
    val active = Lyrics.activeLineIndex(current, pos).coerceAtLeast(0)
    val nowLine = current.getOrNull(active)?.text.orEmpty()
    if (nowLine.isBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x2E4C8DFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("♪", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            nowLine,
            color = TextMain,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text("Ver", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Letra sincronizada en vivo del tema que está sonando (v0.79.19). */
@Composable
private fun LyricsPanel(info: MediaInfo?, modifier: Modifier) {
    val title = info?.title
    val artist = info?.artist
    var lines by remember(title, artist) { mutableStateOf<List<LyricLine>?>(null) }
    LaunchedEffect(title, artist, NetWatch.back.intValue) {
        if (title.isNullOrBlank()) {
            lines = emptyList()
        } else {
            lines = null
            lines = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Lyrics.fetch(title, artist)
            }
        }
    }
    val pos = rememberPlaybackPosition(info)
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x18FFFFFF)).padding(12.dp)) {
        val current = lines
        when {
            title.isNullOrBlank() -> Text("Reproducí un tema para ver su letra en vivo.", color = Muted, fontSize = 14.sp)
            current == null -> Text("Buscando la letra de \"$title\"…", color = Muted, fontSize = 14.sp)
            current.isEmpty() -> Text("No se encontró la letra de \"$title\".", color = Muted, fontSize = 14.sp)
            current.first().timeMs >= 0L -> {
                val active = Lyrics.activeLineIndex(current, pos).coerceAtLeast(0)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    val prev = current.getOrNull(active - 1)?.text.orEmpty()
                    val nowLine = current.getOrNull(active)?.text.orEmpty()
                    val next1 = current.getOrNull(active + 1)?.text.orEmpty()
                    val next2 = current.getOrNull(active + 2)?.text.orEmpty()
                    if (prev.isNotEmpty()) {
                        Text(prev, color = Muted.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(nowLine, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (next1.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(next1, color = Muted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (next2.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(next2, color = Muted.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(current) { l ->
                        Text(l.text, color = TextMain, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

/** Miniatura de portada de un ítem de Spotify; si no tiene o todavía carga, muestra el ícono musical. */
@Composable
private fun SpotifyCover(url: String?, size: Int) {
    var img by remember(url) { mutableStateOf(SpotifyCovers.cached(url)) }
    LaunchedEffect(url) {
        if (img == null && url != null && RenderFlags.animate) {
            img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RadioLogos.slots.acquire()
                try {
                    SpotifyCovers.fetch(url)
                } finally {
                    RadioLogos.slots.release()
                }
            }
        }
    }
    CoverArt(img, size)
}

/** Campo de texto oscuro de una línea, con acción "buscar/listo" del teclado. */
@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xF00B1120))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = Muted, fontSize = 15.sp)
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Radio: emisora actual con controles y la lista para elegir (favoritas primero). */
@Composable
private fun RadioTab() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Radio.loadMode(context)
        RadioFavorites.load(context)
        RadioCatalog.load(context)
    }
    val station = radioStation()
    Column(Modifier.fillMaxSize()) {
        if (radioActive()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationLogo(station, 56)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(station.name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiveDot()
                        Text(radioStatus(), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TransportRow(
                playing = RadioState.playing.value || RadioState.buffering.value,
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
            Spacer(Modifier.height(6.dp))
        }
        val favs = RadioFavorites.names.value
        val list = RadioCatalog.visible(favs).sortedWith(compareBy({ it.name !in favs }, { it.freq ?: 999f }))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(list, key = { it.name }) { s ->
                val current = s.name == RadioState.current.value
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (current) Color(0x334C8DFF) else Color.Transparent)
                        .clickable { Radio.play(context, s) }.padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StationLogo(s, 34)
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(s.freq?.let { String.format(Locale.US, "%.1f", it) } ?: "", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(28.dp).clickable { RadioFavorites.toggle(context, s.name) }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(18.dp)) { iconHeart(s.name in favs, if (s.name in favs) Color(0xFFFF5C6C) else Muted) }
                    }
                }
            }
        }
    }
}
