package com.onixgarage.phone.ui.common

import java.util.Calendar
import java.util.Locale

fun formatArs(value: Double): String =
    "$" + String.format(Locale.US, "%,.0f", value).replace(",", ".")

fun formatTimestamp(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d/%02d/%d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
    )
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
