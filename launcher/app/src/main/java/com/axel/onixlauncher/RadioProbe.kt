package com.axel.onixlauncher

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Prueba de radio desde Diagnóstico: revisa, uno por uno, todos los enlaces de una emisora
 * (página oficial, señales guardadas, búsqueda en el directorio) y anota qué devuelve cada uno.
 * Para La 100 además consulta a StreamTheWorld si existen las señales probables de Tucumán.
 * El resultado va al informe que se copia desde Diagnóstico, para mandárselo a Claude.
 */
object RadioProbe {
    val running = mutableStateOf(false)
    val lines = mutableStateOf<List<String>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Señales probables de La 100 Tucumán en StreamTheWorld (se consulta si existen). */
    val la100Mounts = listOf(
        "FM955_56", "FM955_56AAC", "FM955AAC", "FM955", "FM955_SC",
        "LA100_TUCUMAN", "LA100TUCUMAN", "LA100TUCUMANAAC", "LA100TUC", "LA100_TUC",
        "TUCUMANFM955", "FM955TUCUMAN"
    )

    /** URL de la consulta de StreamTheWorld para un mount (responde XML con su estado). */
    fun tritonInfoUrl(mount: String) =
        "https://playerservices.streamtheworld.com/api/livestream?version=1.9&mount=$mount&lang=es"

    fun start(station: Station) {
        if (running.value) return
        running.value = true
        lines.value = listOf("Probando ${station.name}…")
        scope.launch {
            val out = mutableListOf("Prueba de ${station.name} (${station.detail})")
            fun add(s: String) {
                out += s
                lines.value = out.toList()
            }
            try {
                for (c in radioCandidates(station.url, national = false)) {
                    val nac = c.startsWith("nac:")
                    val raw = c.removePrefix("nac:")
                    val tag = if (nac) "[nacional]" else "[local]"
                    when {
                        raw == "lookup:" -> {
                            val local = RadioDirectory.findLocalUrl(station.name)
                            add("$tag directorio (Tucumán): ${local ?: "no encontrado"}")
                            if (local != null) add("    → ${Net.inspect(local)}")
                            val any = RadioDirectory.findUrl(station.name, preferLocal = false)
                            if (any != null && any != local) add("[nacional] directorio: $any → ${Net.inspect(any)}")
                        }
                        raw.startsWith("page:") -> {
                            val page = raw.removePrefix("page:")
                            add("$tag página $page → ${Net.inspect(page)}")
                            val found = RadioDirectory.scrapeStream(page)
                            add("    lectura: ${RadioDirectory.lastScrape ?: "-"}")
                            if (found != null) add("    audio elegido → ${Net.inspect(found)}")
                        }
                        else -> {
                            val real = resolvePlaylist(raw)
                            add("$tag $raw" + (if (real != raw) " → $real" else "") + " → ${Net.inspect(real)}")
                        }
                    }
                }
                if (station.name == "La 100") {
                    add("StreamTheWorld, señales probables de Tucumán:")
                    for (m in la100Mounts) {
                        val xml = Net.get(tritonInfoUrl(m), timeoutMs = 6000)
                        val code = xml?.let { Regex("<status-code>(\\d+)</status-code>").find(it)?.groupValues?.get(1) }
                        add("    $m: " + when {
                            xml == null -> "sin respuesta"
                            code == "200" -> "EXISTE"
                            else -> "no (código ${code ?: "?"})"
                        })
                    }
                }
                add("Fin de la prueba.")
            } catch (e: Exception) {
                add("La prueba se cortó: ${e.javaClass.simpleName}")
            } finally {
                running.value = false
            }
        }
    }
}
