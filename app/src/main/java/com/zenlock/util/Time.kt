package com.zenlock.util

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Everything in this app reasons about "minute of day" (0..1439) and "today". */
object Time {

    fun nowMinuteOfDay(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun startOfDay(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * True when [nowMinute] falls inside the window, handling windows that wrap past
     * midnight (22:00 -> 07:00). A window whose ends are equal is treated as "always".
     */
    fun isWithinWindow(startMinute: Int, endMinute: Int, nowMinute: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> nowMinute >= startMinute && nowMinute < endMinute
        else -> nowMinute >= startMinute || nowMinute < endMinute
    }

    fun formatClock(minuteOfDay: Int): String {
        val h24 = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        val h12 = when (h24 % 12) {
            0 -> 12
            else -> h24 % 12
        }
        val suffix = if (h24 < 12) "AM" else "PM"
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix)
    }

    /** "0m", "48m", "2h 05m" — compact enough for a stat row. */
    fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format(Locale.US, "%dh %02dm", hours, minutes)
        else String.format(Locale.US, "%dm", minutes)
    }

    fun minutesToMillis(minutes: Int): Long = minutes * 60_000L

    /** "2 Sep" — the month chart has no room for more. */
    fun shortDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
    }

    /** Single letter for the week chart's axis. */
    fun dayInitial(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            Calendar.SATURDAY -> "S"
            else -> "S"
        }
    }
}
