package com.zenlock.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import android.content.Context

/**
 * One row per app per day.
 *
 * Android keeps raw usage events for roughly a week and then drops them, so anything longer
 * than that has to be rolled up and kept by us. Today's rows are rewritten on every sync
 * (the system's own numbers stay authoritative while a day is still in progress); older rows
 * are frozen once their day has passed.
 */
@Entity(tableName = "day_app_usage", primaryKeys = ["dayStamp", "packageName"])
data class DayAppUsage(
    val dayStamp: Long,
    val packageName: String,
    val foregroundMs: Long,
    val opens: Int,
)

/** A whole day, summed across apps. */
data class DaySummary(
    val dayStart: Long,
    val totalMs: Long,
    val opens: Int,
    val isToday: Boolean,
)

/** One app's share of a range. */
data class AppTotal(
    val packageName: String,
    val totalMs: Long,
    val opens: Int,
)

@Dao
interface HistoryDao {

    @Upsert
    suspend fun upsertAll(rows: List<DayAppUsage>)

    @Query(
        """
        SELECT dayStamp, SUM(foregroundMs) AS totalMs, SUM(opens) AS opens
        FROM day_app_usage
        WHERE dayStamp >= :from
        GROUP BY dayStamp
        ORDER BY dayStamp ASC
        """,
    )
    suspend fun dailyTotals(from: Long): List<DayTotalRow>

    @Query(
        """
        SELECT packageName, SUM(foregroundMs) AS totalMs, SUM(opens) AS opens
        FROM day_app_usage
        WHERE dayStamp >= :from
        GROUP BY packageName
        ORDER BY totalMs DESC
        LIMIT :limit
        """,
    )
    suspend fun topApps(from: Long, limit: Int): List<AppTotal>

    @Query("SELECT MIN(dayStamp) FROM day_app_usage")
    suspend fun earliestDay(): Long?

    @Query("DELETE FROM day_app_usage WHERE dayStamp < :before")
    suspend fun prune(before: Long)
}

data class DayTotalRow(
    val dayStamp: Long,
    val totalMs: Long,
    val opens: Int,
)

@Database(entities = [DayAppUsage::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun history(): HistoryDao

    companion object {
        @Volatile
        private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "zenlock-history.db",
                ).build().also { instance = it }
            }
    }
}
