package com.axel.onixlauncher

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Autoría, versión y atribuciones obligatorias (AUD-022, AUD-024). Se abre desde Diagnóstico. */
internal object AboutInfo {
    const val AUTHOR = "Axel Hernán González"
    const val COPYRIGHT = "© 2026 Axel Hernán González. Licencia AGPL-3.0 (desde v0.56.0)."

    /** Créditos de terceros: servicios, datos, marcas e imágenes. */
    val credits = listOf(
        "Clima" to "Datos de Open-Meteo.com, licencia CC BY 4.0",
        "Directorio de radios" to "Radio Browser (radio-browser.info)",
        "Mapas" to "© colaboradores de OpenStreetMap (ODbL); mapa 3D OpenFreeMap © OpenMapTiles con MapLibre (BSD); osmdroid (Apache 2.0)",
        "Búsqueda y rutas" to "© TomTom (TomTom Search y Routing API)",
        "Frecuencias y logos" to "World Radio Map (worldradiomap.com); los logos son de cada emisora",
        "Transmisiones" to "Pertenecen a cada emisora",
        "Marca" to "Chevrolet y Onix son marcas de General Motors. Uso personal, sin relación con GM",
        "Fotos" to "Auto y paisajes aportados por el usuario, de sus autores. Uso personal",
        "Librerías" to "Jetpack Compose, AndroidX, Media3, osmdroid y Conscrypt (Apache 2.0)",
        "Voces grabadas" to "Voz es_AR-daniela-high de Piper (larcanio), corpus de castellano argentino OpenSLR 61, CC BY-SA 4.0",
        "Voces del inicio con IA" to "ElevenLabs (elevenlabs.io), plan gratuito, uso personal",
        "Voces del inicio" to "Piper: en_US-ryan-high (RyanSpeech, CC BY-NC-SA 4.0), en_US-joe-medium (CC0), en_GB-alan-medium (mimic3), en_US-norman-medium (LibriVox, dominio público)",
        "Android Auto y CarPlay" to "Motor de CarLink (F-e-n-y-x, AGPL-3.0), con DiAuto/open-headunit (AGPL-3.0, Michael Reid y André Knieriem) y DiPlay/xcertplay (GPL-3.0). CarPlay y Android Auto son marcas de Apple y Google; sin relación ni aval"
    )
}

@Composable
internal fun AboutOverlay(onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(700.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111634)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painterResource(R.drawable.chevrolet_emblem), null,
                    Modifier.size(96.dp, 32.dp), contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f)) {
                    Text("Onix Launcher", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Versión ${appVersionLabel()}", color = Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Desarrollado por ${AboutInfo.AUTHOR}", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(AboutInfo.COPYRIGHT, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "CRÉDITOS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            for ((k, v) in AboutInfo.credits) {
                Row(Modifier.fillMaxWidth()) {
                    Text(k, color = Muted, fontSize = 13.sp, modifier = Modifier.width(170.dp))
                    Text(v, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Tocá afuera para cerrar.", color = Muted, fontSize = 12.sp)
        }
    }
}
