package com.onixgarage.core.obd

internal object PidParser {

    // ── Private helpers ──────────────────────────────────────────────────────

    private val NO_DATA_RESPONSES = setOf(
        "NODATA", "UNABLETOCONNECT", "ERROR", "STOPPED", "CANERROR"
    )

    private fun cleanResponse(raw: String): String =
        raw.replace(" ", "")
            .replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
            .uppercase()

    private fun checkNoData(clean: String): Result<Nothing>? =
        if (NO_DATA_RESPONSES.any { clean.contains(it) })
            Result.failure(IllegalStateException("No data: '$clean'"))
        else null

    private fun oneByte(clean: String, prefix: String): Result<Int> {
        if (!clean.startsWith(prefix))
            return Result.failure(IllegalArgumentException("Bad prefix, expected $prefix: '$clean'"))
        if (clean.length < prefix.length + 2)
            return Result.failure(IllegalArgumentException("Too short: '$clean'"))
        return try {
            Result.success(clean.substring(prefix.length, prefix.length + 2).toInt(16))
        } catch (e: NumberFormatException) {
            Result.failure(e)
        }
    }

    private fun twoBytes(clean: String, prefix: String): Result<Pair<Int, Int>> {
        if (!clean.startsWith(prefix))
            return Result.failure(IllegalArgumentException("Bad prefix, expected $prefix: '$clean'"))
        if (clean.length < prefix.length + 4)
            return Result.failure(IllegalArgumentException("Too short: '$clean'"))
        return try {
            val a = clean.substring(prefix.length, prefix.length + 2).toInt(16)
            val b = clean.substring(prefix.length + 2, prefix.length + 4).toInt(16)
            Result.success(a to b)
        } catch (e: NumberFormatException) {
            Result.failure(e)
        }
    }

    // ── Live PID parsers ─────────────────────────────────────────────────────

    fun parseCoolantTempC(response: String): Result<Int> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return oneByte(clean, "4105").mapCatching { raw ->
            val c = raw - 40
            require(c in -40..215) { "Out of range [$c]: '$response'" }
            c
        }
    }

    fun parseRpm(response: String): Result<Float> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return twoBytes(clean, "410C").map { (a, b) -> ((a * 256) + b) / 4f }
    }

    fun parseSpeedKmh(response: String): Result<Int> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return oneByte(clean, "410D")
    }

    fun parseFuelLevelPct(response: String): Result<Float> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return oneByte(clean, "412F").map { a -> a * 100f / 255f }
    }

    fun parseMafGs(response: String): Result<Double> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return twoBytes(clean, "4110").map { (a, b) -> ((a * 256) + b) / 100.0 }
    }

    fun parseThrottlePct(response: String): Result<Float> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return oneByte(clean, "4111").map { a -> a * 100f / 255f }
    }

    fun parseEngineLoadPct(response: String): Result<Float> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return oneByte(clean, "4104").map { a -> a * 100f / 255f }
    }

    fun parseDistanceSinceDtcClearKm(response: String): Result<Int> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        return twoBytes(clean, "4131").map { (a, b) -> (a * 256) + b }
    }

    // ── DTC parser ───────────────────────────────────────────────────────────

    fun parseDtcCodes(response: String): Result<List<String>> {
        val clean = cleanResponse(response)
        checkNoData(clean)?.let { return it }
        if (!clean.startsWith("43"))
            return Result.failure(IllegalArgumentException("Not a DTC response: '$response'"))
        val data = clean.removePrefix("43")
        if (data.isEmpty() || data == "00") return Result.success(emptyList())
        val codes = mutableListOf<String>()
        var i = 0
        while (i + 3 < data.length) {
            val byteA = data.substring(i, i + 2).toIntOrNull(16) ?: break
            val byteB = data.substring(i + 2, i + 4).toIntOrNull(16) ?: break
            i += 4
            if (byteA == 0 && byteB == 0) continue
            val category = when ((byteA shr 6) and 0x03) {
                0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U"
            }
            val codeNum = String.format("%02X%02X", byteA and 0x3F, byteB)
            codes.add("$category$codeNum")
        }
        return Result.success(codes)
    }
}
