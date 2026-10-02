package com.zenlock.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.zenlock.data.history.DayAppUsage
import com.zenlock.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the system's usage event log.
 *
 * This is the only place that talks to [UsageStatsManager]. Today's numbers are served live
 * from here; anything older is rolled up once and handed to the history database, because
 * Android drops these events after about a week.
 */
class UsageTracker(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Which packages count as "screen time". The launcher and system UI are foreground for
     * large parts of the day and would swamp every chart, so totals are restricted to the
     * launchable app catalogue. Null until that catalogue has loaded.
     */
    @Volatile
    var countedPackages: Set<String>? = null

    data class Snapshot(
        val foregroundMs: Map<String, Long>,
        val opens: Map<String, Int>,
        /** 24 buckets, midnight-indexed, counting only [countedPackages]. */
        val hourlyMs: List<Long>,
        val takenAt: Long,
    ) {
        fun msFor(pkg: String): Long = foregroundMs[pkg] ?: 0L
        fun opensFor(pkg: String): Int = opens[pkg] ?: 0

        companion object {
            val EMPTY = Snapshot(emptyMap(), emptyMap(), List(HOURS) { 0L }, 0L)
        }
    }

    @Volatile
    private var cached: Snapshot = Snapshot.EMPTY

    /** Cached briefly: the event walk is cheap, but it runs on every window change. */
    fun snapshot(maxAgeMs: Long = 4_000L): Snapshot {
        val now = System.currentTimeMillis()
        val last = cached
        if (now - last.takenAt < maxAgeMs && last.takenAt >= Time.startOfDay(now)) return last
        return queryToday(now).also { cached = it }
    }

    fun invalidate() {
        cached = Snapshot.EMPTY
    }

    private fun counted(pkg: String): Boolean = countedPackages?.contains(pkg) ?: false

    @Suppress("DEPRECATION") // ACTIVITY_RESUMED/PAUSED are API 29+; these aliases hold the same values.
    private fun queryToday(now: Long): Snapshot {
        val manager = appContext.getSystemService(UsageStatsManager::class.java)
            ?: return Snapshot.EMPTY.copy(takenAt = now)

        val dayStart = Time.startOfDay(now)
        val totals = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        val hourly = LongArray(HOURS)
        val openSince = HashMap<String, Long>()
        var lastForeground: String? = null

        val events = try {
            manager.queryEvents(dayStart, now)
        } catch (e: SecurityException) {
            return Snapshot.EMPTY.copy(takenAt = now)
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    openSince[pkg] = event.timeStamp
                    if (lastForeground != pkg) {
                        opens[pkg] = (opens[pkg] ?: 0) + 1
                        lastForeground = pkg
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startedAt = openSince.remove(pkg) ?: continue
                    if (event.timeStamp > startedAt) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - startedAt)
                        if (counted(pkg)) sliceIntoHours(hourly, dayStart, startedAt, event.timeStamp)
                    }
                }
            }
        }

        // Whatever is still on screen has an open session; count it up to now.
        for ((pkg, startedAt) in openSince) {
            if (now <= startedAt) continue
            totals[pkg] = (totals[pkg] ?: 0L) + (now - startedAt)
            if (counted(pkg)) sliceIntoHours(hourly, dayStart, startedAt, now)
        }

        return Snapshot(totals, opens, hourly.toList(), now)
    }

    /**
     * Per-app, per-day totals across the system's retained window, ready to be written to the
     * history database. A session that crosses midnight is split across both days.
     */
    @Suppress("DEPRECATION")
    suspend fun dailyBreakdown(days: Int): List<DayAppUsage> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayStart = Time.startOfDay(now)
        val from = todayStart - (days - 1) * DAY_MS

        val manager = appContext.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext emptyList()

        val events = try {
            manager.queryEvents(from, now)
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }

        val millis = HashMap<Long, HashMap<String, Long>>()
        val opens = HashMap<Long, HashMap<String, Int>>()
        val openSince = HashMap<String, Long>()
        var lastForeground: String? = null

        fun dayOf(timestamp: Long): Long = from + ((timestamp - from) / DAY_MS) * DAY_MS

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    openSince[pkg] = event.timeStamp
                    if (lastForeground != pkg) {
                        if (counted(pkg)) {
                            val bucket = opens.getOrPut(dayOf(event.timeStamp)) { HashMap() }
                            bucket[pkg] = (bucket[pkg] ?: 0) + 1
                        }
                        lastForeground = pkg
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val startedAt = openSince.remove(pkg) ?: continue
                    if (counted(pkg)) {
                        spreadAcrossDays(millis, pkg, from, startedAt, event.timeStamp)
                    }
                }
            }
        }
        for ((pkg, startedAt) in openSince) {
            if (counted(pkg)) spreadAcrossDays(millis, pkg, from, startedAt, now)
        }

        val rows = ArrayList<DayAppUsage>()
        val allDays = millis.keys + opens.keys
        for (day in allDays) {
            val dayMillis = millis[day].orEmpty()
            val dayOpens = opens[day].orEmpty()
            for (pkg in dayMillis.keys + dayOpens.keys) {
                rows += DayAppUsage(
                    dayStamp = day,
                    packageName = pkg,
                    foregroundMs = dayMillis[pkg] ?: 0L,
                    opens = dayOpens[pkg] ?: 0,
                )
            }
        }
        rows
    }

    /** Splits one session across the hour buckets it spans, so a long session is not one spike. */
    private fun sliceIntoHours(buckets: LongArray, dayStart: Long, start: Long, end: Long) {
        var cursor = start.coerceAtLeast(dayStart)
        while (cursor < end) {
            val hour = ((cursor - dayStart) / HOUR_MS).toInt()
            if (hour !in 0 until HOURS) return
            val sliceEnd = minOf(end, dayStart + (hour + 1) * HOUR_MS)
            buckets[hour] += sliceEnd - cursor
            cursor = sliceEnd
        }
    }

    /** The same idea at day granularity, for sessions that run past midnight. */
    private fun spreadAcrossDays(
        into: HashMap<Long, HashMap<String, Long>>,
        pkg: String,
        from: Long,
        start: Long,
        end: Long,
    ) {
        var cursor = start.coerceAtLeast(from)
        while (cursor < end) {
            val dayStart = from + ((cursor - from) / DAY_MS) * DAY_MS
            val sliceEnd = minOf(end, dayStart + DAY_MS)
            val bucket = into.getOrPut(dayStart) { HashMap() }
            bucket[pkg] = (bucket[pkg] ?: 0L) + (sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    companion object {
        const val HOURS = 24
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val DAY_MS = 24L * HOUR_MS
    }
}
