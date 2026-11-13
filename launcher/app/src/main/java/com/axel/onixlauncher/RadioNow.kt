package com.axel.onixlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

/*
 * La radio de la app en la pantalla de inicio y en el tablero. Se lee del estado del servicio
 * de radio (RadioState), así se actualiza sola sin depender del acceso a notificaciones.
 */

/** true si hay una radio de la app sintonizada (sonando, conectando o en pausa). */
fun radioActive(): Boolean = RadioState.current.value != null

/**
 * Qué muestra la tarjeta de música: la radio si está sonando o conectando; si no, lo que suene
 * en otra app (Spotify, etc.); y si no suena nada, la radio en pausa. Así, al poner Spotify, la
 * tarjeta pasa a Spotify en lugar de quedar fija en la radio.
 */
fun showRadioCard(media: MediaResult, ownPackage: String): Boolean {
    if (!radioActive()) return false
    if (RadioState.playing.value || RadioState.buffering.value) return true
    val other = media.info?.takeIf { it.pkg != ownPackage }
    return other == null || !other.playing
}

/** La emisora sintonizada, con sus datos si se conocen. */
fun radioStation(): Station {
    val name = RadioState.current.value ?: ""
    return RadioState.station.value?.takeIf { it.name == name } ?: Station(name, "", "")
}

/** "RADIO · 95.5 FM" o "RADIO · WEB". */
fun radioLabel(s: Station): String =
    "RADIO · " + (s.freq?.let { String.format(Locale.US, "%.1f FM", it) } ?: "WEB")

/** Estado en palabras: en vivo, conectando, en pausa y por qué. */
fun radioStatus(): String = when {
    RadioState.playing.value -> if (RadioState.note.value != null) "En vivo · señal nacional" else "En vivo"
    RadioState.buffering.value -> "Conectando…"
    RadioState.pauseReason.value == "OTRA APP" -> "En pausa · otra app usa el audio"
    RadioState.pauseReason.value != null -> "En pausa · audio desconectado"
    else -> "En pausa"
}

/** Punto rojo "en vivo" (gris si no suena). */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(
        modifier.size(9.dp).clip(CircleShape)
            .background(if (RadioState.playing.value) Color(0xFFFF3B30) else Color(0xFF6B7280))
    )
}

