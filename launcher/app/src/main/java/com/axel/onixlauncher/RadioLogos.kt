package com.axel.onixlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.sin

/**
 * Logos de las radios. Fuentes, en orden:
 * 1. El logo del dial (World Radio Map) o, para las radios web, el ícono que publican en Radio Browser;
 *    después, el de Radio Browser como segunda opción.
 * 2. El ícono de su página web (apple-touch-icon, favicon), si no es una red social o un directorio.
 * 3. Una búsqueda por nombre en Radio Browser (solo la entrada de Tucumán, o la de la cadena nacional).
 * Se guardan achicados (128 px) en la caché de la app. El resultado de la búsqueda se recuerda
 * (si no hay logo, se vuelve a intentar a la semana). Mientras tanto se ven las iniciales.
 */
object RadioLogos {
    private const val SIDE = 128
    private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
    private val memory = LruCache<String, ImageBitmap>(80)
    private val failed = HashSet<String>()
    /** Como mucho 3 descargas a la vez, para no trabar la radio ni la red. */
    val slots = kotlinx.coroutines.sync.Semaphore(3)

    /** Sitios cuyo ícono no es el de la radio. */
    private val genericHosts = listOf(
        "facebook.com", "fb.com", "instagram.com", "twitter.com", "x.com", "youtube.com", "youtu.be",
        "tiktok.com", "linktr.ee", "wa.me", "whatsapp.com", "google.com", "blogspot.com", "wixsite.com",
        "zeno.fm", "tunein.com", "streema.com", "mytuner-radio.com", "onlineradiobox.com", "radio.garden",
        "radios.com.ar", "radiosargentina.com.ar", "radio.net", "radio-browser.info"
    )

    /** Enlaces a probar para el logo de una emisora (pura, para los tests). */
    fun candidates(s: Station): List<String> {
        val out = mutableListOf<String>()
        s.logo?.let { out += it }
        s.altLogo?.let { out += it }
        s.homepage?.let { home ->
            try {
                val u = java.net.URL(home)
                val host = u.host.lowercase().removePrefix("www.")
                if (host.isNotEmpty() && genericHosts.none { host == it || host.endsWith(".$it") }) {
                    val origin = "${u.protocol}://${u.host}"
                    out += "$origin/apple-touch-icon.png"
                    out += "$origin/favicon.ico"
                }
            } catch (_: Exception) {
                // página mal escrita: se ignora
            }
        }
        return out.distinct()
    }

    private fun file(context: Context, key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "logos_v2").apply { mkdirs() }, "$hash.png")
    }

    fun cached(name: String): ImageBitmap? = memory.get(name)

    /** Logo de la emisora o null. Llamar fuera del hilo principal. */
    fun resolve(context: Context, s: Station): ImageBitmap? {
        memory.get(s.name)?.let { return it }
        // Disco: guardado por nombre de emisora
        val f = file(context, s.name)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return keep(s.name, it) }

        for (u in candidates(s)) fetch(u)?.let { return save(context, s.name, it) }

        // Búsqueda por nombre, recordada para no repetirla en cada apertura
        val prefs = context.getSharedPreferences("logos", Context.MODE_PRIVATE)
        val last = prefs.getLong(s.name, 0L)
        if (System.currentTimeMillis() - last < RETRY_MS) return null
        val source = RadioDirectory.findLogoSource(s.name)
        val found = source?.let { src -> candidates(src).firstNotNullOfOrNull { fetch(it) } }
        if (found == null) {
            // v0.81.22: sin internet no se anota (quedaba sin logo 7 días)
            if (!WifiRescue.down) prefs.edit().putLong(s.name, System.currentTimeMillis()).apply()
            return null
        }
        return save(context, s.name, found)
    }

    private fun keep(name: String, b: Bitmap): ImageBitmap = b.asImageBitmap().also { memory.put(name, it) }

    private fun save(context: Context, name: String, b: Bitmap): ImageBitmap {
        try {
            file(context, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
            // sin caché en disco: igual se muestra
        }
        return keep(name, b)
    }

    private fun fetch(url: String): Bitmap? {
        if (synchronized(failed) { url in failed }) return null
        val bmp = Net.getBytes(url)?.let { decode(it) }
        if (bmp == null && !WifiRescue.down) synchronized(failed) { failed.add(url) }
        return bmp
    }

    /** Decodifica PNG, JPG, WebP, GIF y BMP, y también íconos .ico (muchos favicons lo son). */
    fun decode(bytes: ByteArray): Bitmap? {
        val data = icoToImage(bytes) ?: bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth < 16 || bounds.outHeight < 16) return null // demasiado chico para verse bien
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIDE && bounds.outHeight / (sample * 2) >= SIDE) sample *= 2
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = SIDE.toFloat() / maxOf(raw.width, raw.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true)
        } else raw
    }
}

private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
private fun u32(b: ByteArray, i: Int) = u16(b, i) or (u16(b, i + 2) shl 16)

/**
 * Si [b] es un archivo .ico, devuelve la imagen más grande que contiene como PNG o BMP
 * (Android no lee .ico directamente). Null si no es un .ico.
 */
fun icoToImage(b: ByteArray): ByteArray? {
    if (b.size < 22 || u16(b, 0) != 0 || u16(b, 2) != 1) return null
    val count = u16(b, 4)
    var best = -1
    var bestOff = 0
    var bestLen = 0
    for (i in 0 until count) {
        val e = 6 + i * 16
        if (e + 16 > b.size) break
        val w = (b[e].toInt() and 0xFF).let { if (it == 0) 256 else it }
        val len = u32(b, e + 8)
        val off = u32(b, e + 12)
        if (len > 0 && off >= 0 && off + len <= b.size && w > best) {
            best = w; bestOff = off; bestLen = len
        }
    }
    if (best < 0) return null
    val img = b.copyOfRange(bestOff, bestOff + bestLen)
    // PNG dentro del .ico: se usa tal cual
    if (img.size > 8 && (img[0].toInt() and 0xFF) == 0x89 && img[1].toInt() == 'P'.code) return img
    // BMP sin cabecera de archivo: se le agrega, con la altura a la mitad (el .ico incluye la máscara)
    if (img.size < 40) return null
    val dibSize = u32(img, 0)
    val bpp = u16(img, 14)
    val colors = u32(img, 32).let { if (it == 0 && bpp <= 8) 1 shl bpp else it }
    val pixelOffset = 14 + dibSize + colors * 4
    val dib = img.copyOf()
    val h = u32(dib, 8) / 2
    for (k in 0 until 4) dib[8 + k] = ((h shr (8 * k)) and 0xFF).toByte()
    val total = 14 + dib.size
    val header = ByteArray(14)
    header[0] = 'B'.code.toByte(); header[1] = 'M'.code.toByte()
    for (k in 0 until 4) header[2 + k] = ((total shr (8 * k)) and 0xFF).toByte()
    for (k in 0 until 4) header[10 + k] = ((pixelOffset shr (8 * k)) and 0xFF).toByte()
    return header + dib
}

/** Iniciales para la insignia: "La 100" → "100", "Radio Mitre Tucumán" → "MIT", "BBN Radio" → "BBN". */
fun stationInitials(name: String): String {
    val stop = setOf("radio", "fm", "la", "el", "los", "las", "de", "del", "tucuman", "tucumán", "san", "miguel")
    // La frecuencia ("88.1") no sirve como inicial
    val words = name.replace(Regex("""\d{2,3}[.,]\d"""), " ")
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val tokens = words.filter { it.lowercase() !in stop }.ifEmpty { words }
    if (tokens.isEmpty()) return "FM"
    tokens.firstOrNull { it.all(Char::isDigit) }?.let { return it.take(4) }
    return if (tokens.size == 1) tokens[0].take(3).uppercase()
    else tokens.take(3).joinToString("") { it.take(1) }.uppercase()
}

private val BadgeColors = listOf(
    Color(0xFF8B5CF6), Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFF10B981),
    Color(0xFFEF4444), Color(0xFFEAB308), Color(0xFF6366F1), Color(0xFFEC4899)
)

/** Logo de la emisora, o una insignia de color con sus iniciales mientras no haya logo. */
@Composable
fun StationLogo(station: Station, size: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var img by remember(station.name) { mutableStateOf(RadioLogos.cached(station.name)) }
    LaunchedEffect(station.name, station.logo, station.homepage) {
        if (img == null && RenderFlags.animate) {
            img = withContext(Dispatchers.IO) {
                RadioLogos.slots.acquire()
                try {
                    RadioLogos.resolve(context.applicationContext, station)
                } finally {
                    RadioLogos.slots.release()
                }
            }
        }
    }
    val shape = RoundedCornerShape((size / 5).dp)
    val logo = img
    if (logo != null) {
        Box(modifier.size(size.dp).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(logo, station.name, Modifier.fillMaxSize().padding((size / 14).dp), contentScale = ContentScale.Fit)
        }
    } else {
        val color = BadgeColors[Math.floorMod(station.name.hashCode(), BadgeColors.size)]
        val text = stationInitials(station.name)
        Box(modifier.size(size.dp).clip(shape).background(color), contentAlignment = Alignment.Center) {
            Text(
                text, color = Color.White, fontWeight = FontWeight.Black,
                fontSize = (size * (if (text.length <= 2) 0.42f else 0.30f)).sp, maxLines = 1
            )
        }
    }
}

/** Estrella de favoritos (rellena o solo contorno). */
fun DrawScope.iconStar(color: Color, filled: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f + size.height * 0.04f
    val r1 = size.minDimension * 0.48f
    val r2 = r1 * 0.45f
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) r1 else r2
        val a = Math.toRadians(-90.0 + i * 36.0)
        val pt = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(width = size.minDimension * 0.08f))
}
