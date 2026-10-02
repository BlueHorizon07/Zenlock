package com.zenlock.data.history

import android.content.Context
import com.zenlock.usage.UsageTracker
import com.zenlock.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the long-term record.
 *
 * The flow is one-way: [UsageTracker] reads the system's event log (about a week deep), this
 * writes the rolled-up result into Room, and every chart longer than today reads from Room.
 * That means the first week of history is inherited from Android for free, and everything
 * after it accumulates because the app kept it.
 */
class HistoryRepository(context: Context) {

    private val dao = HistoryDatabase.get(context).history()

    /**
     * Pulls the system's whole retained window and upserts it. Cheap enough to run on every
     * foreground, and running it often is what stops a few unopened days falling off the end
     * of Android's retention before they were ever recorded.
     */
    suspend fun sync(tracker: UsageTracker) = withContext(Dispatchers.IO) {
        val rows = tracker.dailyBreakdown(SYSTEM_WINDOW_DAYS)
        if (rows.isNotEmpty()) dao.upsertAll(rows)
        dao.prune(Time.startOfDay() - RETAIN_DAYS * DAY_MS)
    }

    /** Daily totals for the last [days] days, oldest first, with empty days filled in. */
    suspend fun dailyTotals(days: Int): List<DaySummary> = withContext(Dispatchers.IO) {
        val today = Time.startOfDay()
        val from = today - (days - 1) * DAY_MS
        val byDay = dao.dailyTotals(from).associateBy { it.dayStamp }

        (0 until days).map { offset ->
            val dayStart = from + offset * DAY_MS
            val row = byDay[dayStart]
            DaySummary(
                dayStart = dayStart,
                totalMs = row?.totalMs ?: 0L,
                opens = row?.opens ?: 0,
                isToday = dayStart == today,
            )
        }
    }

    suspend fun topApps(days: Int, limit: Int = 5): List<AppTotal> = withContext(Dispatchers.IO) {
        dao.topApps(Time.startOfDay() - (days - 1) * DAY_MS, limit)
    }

    /** How far back the record actually goes, so the UI can avoid implying data it lacks. */
    suspend fun daysRecorded(): Int = withContext(Dispatchers.IO) {
        val earliest = dao.earliestDay() ?: return@withContext 0
        (((Time.startOfDay() - earliest) / DAY_MS) + 1).toInt()
    }

    companion object {
        /** Android's own retention is about a week; asking for more just returns nothing. */
        const val SYSTEM_WINDOW_DAYS = 7
        private const val RETAIN_DAYS = 400L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
