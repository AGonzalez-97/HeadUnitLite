package com.onixgarage.phone.ui.trip

import java.util.Calendar

enum class TripPeriod {
    Day, Week, Month, All;

    fun fromMs(now: Long): Long {
        if (this == All) return 0L
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return when (this) {
            Day -> cal.timeInMillis
            Week -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.timeInMillis
            }
            Month -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            All -> 0L
        }
    }
}
