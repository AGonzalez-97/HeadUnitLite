package com.axel.onixlauncher.logic

import java.util.Locale

/**
 * Lector ELM327 simulado para los tests (v0.50.0): contesta como un ELM327 real configurado con
 * ATE0 ATL0 ATS0 ATH0 (sin eco, sin espacios, sin encabezados) sobre CAN, que es el protocolo del
 * Onix. Simula un auto con o sin sensor MAF, luz del motor, códigos guardados y pendientes, y el
 * borrado con el modo 04. Los mensajes CAN largos salen en varias líneas numeradas, como el real.
 */
class FakeElm327(
    var coolantC: Int? = 90,
    var rpm: Int? = 2000,
    var speedKmh: Int? = 60,
    var fuelPct: Int? = 50,
    var volts: Float = 14.1f,
    /** g/s; null = el auto no tiene MAF (como el Onix Joy 1.0). */
    var mafGs: Float? = null,
    var mapKpa: Int? = 50,
    var intakeC: Int? = 30,
    var stored: MutableList<String> = mutableListOf(),
    var pending: MutableList<String> = mutableListOf(),
    var acceptClear: Boolean = true
) {
    /** Comandos recibidos, en orden. */
    val sent = mutableListOf<String>()

    fun send(cmd: String): String {
        sent += cmd
        return when (cmd) {
            "ATRV" -> String.format(Locale.US, "%.1fV", volts)
            "0101" -> {
                val mil = stored.isNotEmpty()
                hex(0x41, 0x01, (if (mil) 0x80 else 0) or stored.size, 0x07, 0x65, 0x00)
            }
            "0105" -> coolantC?.let { hex(0x41, 0x05, it + 40) } ?: NO_DATA
            "010C" -> rpm?.let { val v = it * 4; hex(0x41, 0x0C, v shr 8, v and 0xFF) } ?: NO_DATA
            "010D" -> speedKmh?.let { hex(0x41, 0x0D, it) } ?: NO_DATA
            "012F" -> fuelPct?.let { hex(0x41, 0x2F, Math.round(it * 255f / 100f)) } ?: NO_DATA
            "0110" -> mafGs?.let { val v = Math.round(it * 100); hex(0x41, 0x10, v shr 8, v and 0xFF) } ?: NO_DATA
            "010B" -> mapKpa?.let { hex(0x41, 0x0B, it) } ?: NO_DATA
            "010F" -> intakeC?.let { hex(0x41, 0x0F, it + 40) } ?: NO_DATA
            "03" -> codes(0x43, stored)
            "07" -> codes(0x47, pending)
            "04" -> if (acceptClear) { stored.clear(); pending.clear(); "44" } else NO_DATA
            else -> "?"
        }
    }

    companion object {
        const val NO_DATA = "NO DATA"

        fun hex(vararg b: Int): String = b.joinToString("") { String.format(Locale.US, "%02X", it) }

        /** "P0133" → [0x01, 0x33] (lo inverso de Dtc.decode). */
        fun encode(code: String): List<Int> {
            val letter = "PCBU".indexOf(code[0])
            val a = (letter shl 6) or (code[1].digitToInt() shl 4) or code[2].digitToInt(16)
            return listOf(a, code.substring(3).toInt(16))
        }

        /**
         * Respuesta CAN de modo 03/07: 43, cantidad y pares. Hasta 7 bytes en una línea; más,
         * como el ELM327: línea con el largo y líneas "0:" (6 bytes), "1:", "2:"… (7 bytes).
         */
        fun codes(tag: Int, list: List<String>): String {
            val bytes = listOf(tag, list.size) + list.flatMap { encode(it) }
            if (bytes.size <= 7) return hex(*bytes.toIntArray())
            val lines = mutableListOf(String.format(Locale.US, "%03X", bytes.size))
            lines += "0:" + hex(*bytes.take(6).toIntArray())
            bytes.drop(6).chunked(7).forEachIndexed { i, chunk -> lines += "${(i + 1) % 16}:" + hex(*chunk.toIntArray()) }
            return lines.joinToString("\r")
        }
    }
}
