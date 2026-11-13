package com.axel.onixlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlink.core.connection.Connection
import com.carlink.core.connection.toLinkState
import com.carlink.core.core.Engine
import com.carlink.core.core.LinkProblem
import com.carlink.core.core.LinkStage
import com.carlink.core.core.LinkState

/*
 * Android Auto y CarPlay dentro de Onix (v0.56.0, ADR-0012) con el motor de CarLink (AGPL-3.0).
 * Al enchufar el celular por USB el motor arranca solo (Android Auto para Android, CarPlay para
 * iPhone) y abre su propia pantalla de proyección; al desenchufar vuelve a Onix (OnixHost).
 * El botón "Auto/CarPlay" del dock muestra esta pantalla con el estado y las indicaciones.
 */
object Projection {
    /** Pantalla Auto/CarPlay abierta (desde el dock). */
    val open = mutableStateOf(false)

    /**
     * Apps de proyección instaladas aparte que pueden pelear por el USB con el motor de Onix
     * (por ejemplo, CarLink instalada antes). Idea de LiteDash; pura, para los tests.
     */
    val PACKAGES = listOf(
        "com.carlink.app", "com.andrerinas.headunitrevived", "com.shihab.diplay",
        "com.zjinnova.zlink", "cn.manstep.phonemirrorBox", "net.easyconn.carman", "com.suding.speedplay"
    )
    private val WORDS = listOf("carplay", "android auto", "androidauto", "carlink", "zlink", "autokit", "easyconn", "phonelink", "carbit")

    /** La primera app de proyección entre [apps] (paquete → nombre), sin contar a Onix; o null. */
    fun find(apps: Map<String, String>): String? =
        PACKAGES.firstOrNull { it in apps } ?: apps.entries.firstOrNull { (pkg, label) ->
            val name = label.lowercase()
            val p = pkg.lowercase()
            pkg != "com.axel.onixlauncher" && WORDS.any { w -> w in name || w.replace(" ", "") in p }
        }?.key

    /** Apps con ícono instaladas (paquete → nombre). */
    fun installed(context: android.content.Context): Map<String, String> = try {
        val pm = context.packageManager
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
            .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    } catch (e: Exception) {
        emptyMap()
    }

    /** Si hay una sesión de Android Auto o CarPlay en curso (AUD-049). */
    fun sessionActive(context: android.content.Context): Boolean = try {
        Connection.get(context).state.value.toLinkState().isActive
    } catch (e: Exception) {
        false
    }

    /**
     * Actividad de la pantalla de [engine] (v0.72.0, como "returnToProjection" de CarLink): para
     * volver a Android Auto o CarPlay con la sesión andando. Pura, para los tests.
     */
    fun screenClass(engine: Engine?): String? = when (engine) {
        Engine.CARPLAY -> "com.shilapi.xcertplay.CarPlayHostActivity"
        Engine.ANDROID_AUTO -> "com.andrerinas.openheadunit.aap.AapProjectionActivity"
        null -> null
    }

    /** Vuelve a la pantalla de Android Auto o CarPlay si hay una sesión. */
    fun returnTo(context: android.content.Context, engine: Engine?) {
        val cls = screenClass(engine) ?: return
        val intent = android.content.Intent().setClassName(context, cls)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startSafe(context, intent)
    }

    /** Si el motor se frenó por algo que el conductor tiene que autorizar (se abre la pantalla sola). */
    fun needsDriver(s: LinkState): Boolean = s.stage == LinkStage.FAILED &&
        s.problem in setOf(LinkProblem.PERMISSIONS, LinkProblem.VPN_CONSENT, LinkProblem.OVERLAY)

    /** El estado del motor en palabras para el conductor (puro, para los tests). */
    fun status(s: LinkState): Pair<String, String> {
        val what = when (s.engine) {
            Engine.CARPLAY -> "CarPlay"
            null -> "Android Auto / CarPlay"
            else -> "Android Auto"
        }
        val phone = s.phoneName?.let { " con $it" } ?: ""
        return when (s.stage) {
            LinkStage.IDLE -> "Enchufá el celular por USB" to "iPhone → CarPlay · Android → Android Auto. Arranca solo."
            LinkStage.WAITING -> "Esperando al celular…" to "Desbloquealo y aceptá el aviso si aparece"
            LinkStage.CONNECTING -> "Conectando $what$phone…" to
                (if (s.stepCount > 0) "Paso ${s.step} de ${s.stepCount}" else "Un momento")
            LinkStage.CONNECTED -> "$what conectado$phone" to "Se abre la pantalla de $what"
            LinkStage.FAILED -> "No se pudo conectar $what" to problem(s.problem)
        }
    }

    /** El motivo exacto que dio el motor al fallar (v0.79.1), para la Prueba completa. Pura. */
    fun detailOf(s: LinkState): String? =
        if (s.stage == LinkStage.FAILED) s.detail?.takeIf { it.isNotBlank() }?.let { "motivo: $it" } else null

    private fun problem(p: LinkProblem?): String = when (p) {
        LinkProblem.CARPLAY_AUTH_MISSING -> "A CarPlay le falta su identidad: cargala en GitHub (docs/CARPLAY.md)"
        LinkProblem.USB -> "Revisá el cable USB y volvé a enchufar"
        LinkProblem.PHONE_DID_NOT_ANSWER -> "El celular no respondió: desbloquealo y volvé a enchufar"
        LinkProblem.PERMISSIONS -> "Faltan permisos (micrófono y ubicación): tocá Dar permisos"
        LinkProblem.VPN_CONSENT -> "CarPlay por cable necesita una autorización de Android: tocá Autorizar"
        LinkProblem.OVERLAY -> "Falta el permiso para mostrarse sobre otras apps"
        null -> "Desenchufá y volvé a enchufar"
        else -> "Desenchufá y volvé a enchufar (${p.name})"
    }
}

/**
 * Últimos pasos del motor de Android Auto y CarPlay (v0.79.1), para la Prueba completa: cada paso
 * de CarPlay (con el nombre que le da el motor), los cambios de estado con el motivo exacto al
 * fallar y los avisos del motor. Así se ve dónde se corta sin conectar la computadora.
 */
object EngineTrace {
    const val MAX = 50
    private val lines = ArrayDeque<String>()
    private var lastText: String? = null

    /** Anota [text] con la hora; no repite la misma línea seguida. */
    fun add(text: String, nowMs: Long = System.currentTimeMillis()) = synchronized(lines) {
        if (text == lastText) return@synchronized
        lastText = text
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(nowMs))
        lines.addLast("$t $text")
        while (lines.size > MAX) lines.removeFirst()
    }

    fun recent(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear(); lastText = null }

    /** Escucha los pasos de CarPlay (proceso principal, donde corre el motor de CarPlay). */
    fun install() {
        com.shilapi.xcertplay.orchestration.CarPlayStatusBus.addListener { s -> if (s != null) add("CarPlay: " + describe(s)) }
    }

    private val WORDS = mapOf(
        "DiscoveringMfi" to "buscando la identidad de CarPlay",
        "WaitingForMfi" to "esperando la identidad de CarPlay",
        "RequestingMfiPermission" to "pidiendo permiso para la identidad",
        "MfiReady" to "identidad de CarPlay lista",
        "DiscoveringIphone" to "buscando el iPhone en el USB",
        "WaitingForIphone" to "esperando al iPhone en el USB",
        "IphoneNotListed" to "el iPhone no aparece en el USB",
        "RequestingIphonePermission" to "pidiendo permiso de USB para el iPhone",
        "SelectingConfiguration" to "pasando el iPhone a modo CarPlay",
        "WaitingForReenumeration" to "esperando que el iPhone vuelva en modo CarPlay",
        "OpeningDataPaths" to "abriendo los canales de datos",
        "Pairing" to "el iPhone confía en la radio",
        "ConnectingControl" to "conectando el control (iAP2)",
        "AttachingNetwork" to "armando la red por USB",
        "RunningControl" to "CarPlay andando",
    )

    /** Un paso de CarPlay en palabras, con el nombre del motor entre paréntesis. Pura. */
    fun describe(s: com.shilapi.xcertplay.orchestration.CarPlayStatus): String {
        if (s is com.shilapi.xcertplay.orchestration.CarPlayStatus.Failed) {
            return "falló: ${s.message}" + (s.knownCause?.let { " ($it)" } ?: "")
        }
        // toString de un data object o data class: el nombre queda aunque R8 acorte las clases
        val name = s.toString().substringBefore('(')
        return WORDS[name]?.let { "$it ($name)" } ?: name
    }

    /** El estado del motor en una línea, con el motivo exacto si falló. Pura. */
    fun session(s: LinkState): String {
        val (title, detail) = Projection.status(s)
        return listOfNotNull(title, detail, Projection.detailOf(s)).joinToString(" · ")
    }

    /**
     * Las últimas [n] líneas del registro propio del motor de CarPlay (`files/logs/diplay.log`,
     * y antes la sesión anterior si la actual es corta), sin direcciones ni claves. Pura.
     */
    fun logTail(current: List<String>, previous: List<String>, n: Int = 80): List<String> {
        val lines = if (current.size >= 15 || previous.isEmpty()) current
        else previous.takeLast(n - current.size) + listOf("(sesión actual)") + current
        return lines.takeLast(n).map { AppLog.redact(com.carlink.core.diagnostics.LogMasking.mask(it)).take(300) }
    }

    /** Un equipo USB conectado a la radio (Apple es 05ac). Pura. */
    fun usbLine(vendor: Int, product: Int, name: String?, configs: Int): String =
        String.format(java.util.Locale.US, "%04x:%04x", vendor, product) + " · " +
            (name?.takeIf { it.isNotBlank() } ?: "sin nombre") + (if (vendor == 0x05ac) " (Apple)" else "") +
            " · $configs " + if (configs == 1) "configuración" else "configuraciones"
}

/** Pantalla Auto/CarPlay: estado del motor e indicaciones. */
@Composable
internal fun ProjectionOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { Connection.get(context) }
    val session by manager.state.collectAsState()
    val link = session.toLinkState()
    val (title, detail) = Projection.status(link)
    // AUD-036 / AUD-037 (v0.59.0): permisos del motor y autorización de VPN para CarPlay por cable
    val askPermissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { manager.retry() }
    val askVpn = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { manager.retry() }
    // Otra app de proyección instalada (por ejemplo CarLink) puede quedarse con el USB
    val other = remember { Projection.find(Projection.installed(context)) }
    Box(
        Modifier.size(1024.dp, 600.dp).background(Color(0xE6040612))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(640.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111634))
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(72.dp)) { iconProjection(Accent) }
            Spacer(Modifier.height(16.dp))
            Text(title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = if (link.stage == LinkStage.FAILED) Color(0xFFFF8A1F) else Muted, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            if (other != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hay otra app de Android Auto / CarPlay instalada ($other): desinstalala para que no se quede con el USB",
                    color = Color(0xFFFF8A1F), fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (link.problem == LinkProblem.PERMISSIONS) PillButton("Dar permisos", Accent, OnAccent, Modifier.width(170.dp)) {
                    askPermissions.launch(com.carlink.core.connection.DevicePreflight.requiredPermissions().toTypedArray())
                }
                if (link.problem == LinkProblem.VPN_CONSENT) PillButton("Autorizar", Accent, OnAccent, Modifier.width(170.dp)) {
                    val intent = runCatching { android.net.VpnService.prepare(context) }.getOrNull()
                    if (intent != null) askVpn.launch(intent) else manager.retry()
                }
                // v0.72.0: volver a la pantalla con la sesión andando; "Desconectar" corta la sesión
                if (link.isActive && link.engine != null) PillButton(
                    if (link.engine == Engine.CARPLAY) "Volver a CarPlay" else "Volver a Android Auto",
                    Accent, OnAccent, Modifier.width(230.dp)
                ) { onClose(); Projection.returnTo(context, link.engine) }
                if (link.isActive) PillButton("Desconectar", Track, TextMain, Modifier.width(170.dp)) { manager.disconnect() }
                PillButton("Cerrar", if (link.isActive) Track else Accent, if (link.isActive) TextMain else OnAccent, Modifier.width(150.dp), onClose)
            }
        }
    }
}

/** Ícono de Auto / CarPlay: una pantalla con el triángulo de "reproducir" y un pie. */
internal fun DrawScope.iconProjection(color: Color = Color.White) {
    val s = size.width
    val stroke = Stroke(width = s * 0.09f)
    drawRoundRect(color, Offset(s * 0.12f, s * 0.18f), Size(s * 0.76f, s * 0.52f), CornerRadius(s * 0.08f), style = stroke)
    val tri = Path().apply {
        moveTo(s * 0.43f, s * 0.32f)
        lineTo(s * 0.62f, s * 0.44f)
        lineTo(s * 0.43f, s * 0.56f)
        close()
    }
    drawPath(tri, color)
    drawLine(color, Offset(s * 0.5f, s * 0.70f), Offset(s * 0.5f, s * 0.86f), strokeWidth = s * 0.09f)
    drawLine(color, Offset(s * 0.34f, s * 0.86f), Offset(s * 0.66f, s * 0.86f), strokeWidth = s * 0.09f)
}
