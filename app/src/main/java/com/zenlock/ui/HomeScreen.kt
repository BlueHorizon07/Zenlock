package com.zenlock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlock.data.ZenState
import com.zenlock.data.history.DaySummary
import com.zenlock.usage.UsageTracker
import com.zenlock.util.AppEntry
import com.zenlock.util.Time
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: ZenState,
    usage: UsageTracker.Snapshot,
    rangeDays: List<DaySummary>,
    range: Int,
    daysRecorded: Int,
    onRangeChange: (Int) -> Unit,
    apps: List<AppEntry>,
    permissions: PermissionState,
    contentPadding: PaddingValues,
    onToggleEnabled: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
) {
    // Zenlock is excluded from the rules list (you cannot sensibly limit the limiter),
    // but its own screen time is still screen time, so it counts toward the daily total.
    val ownPackage = LocalContext.current.packageName
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val ready = apps.isNotEmpty()

    val totalToday = remember(apps, usage, ownPackage) {
        apps.sumOf { usage.msFor(it.packageName) } + usage.msFor(ownPackage)
    }
    val activeApps = remember(apps, usage, ownPackage) {
        apps.count { usage.msFor(it.packageName) > 0 } +
            if (usage.msFor(ownPackage) > 0) 1 else 0
    }
    val managed = remember(state.rules, usage) {
        state.rules.values
            .filter { it.isManaged }
            .sortedByDescending { usage.msFor(it.packageName) }
    }
    val topApps = remember(apps, usage) {
        apps.filter { usage.msFor(it.packageName) > 0 }
            .sortedByDescending { usage.msFor(it.packageName) }
            .take(5)
    }
    val today = Time.startOfDay()
    val lockedApps = state.lockedCount(today)
    val busiestHour = remember(usage) {
        usage.hourlyMs.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(2.dp))
                if (ready) {
                    AnimatedDuration(millis = totalToday)
                } else {
                    // A dash, not 0m: a wrong number that corrects itself reads as a bug.
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (ready) "across $activeApps apps" else "adding it up",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    HourlyChart(hourly = usage.hourlyMs)
                    if (busiestHour != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Busiest around ${Time.formatClock(busiestHour.index * 60)}" +
                                " · ${Time.formatDuration(busiestHour.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                SwitchRow(
                    title = if (state.enabled) "Zenlock is on" else "Zenlock is off",
                    subtitle = when {
                        lockedApps > 0 -> "Held by $lockedApps locked " +
                            (if (lockedApps == 1) "app" else "apps") + " until midnight."

                        state.enabled -> "Limits, sleep and pauses are being enforced."
                        else -> "Nothing is being enforced."
                    },
                    checked = state.enabled,
                    enabled = lockedApps == 0,
                    onCheckedChange = onToggleEnabled,
                )
            }
        }

        if (!permissions.allGranted) {
            item {
                SectionCard(title = "${permissions.missingCount} left to set up") {
                    PermissionRow(
                        icon = Icons.Rounded.QueryStats,
                        title = "Usage access",
                        rationale = "So Zenlock can measure how long you have been in each app.",
                        granted = permissions.usageAccess,
                        onGrant = onGrantUsage,
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Accessibility,
                        title = "Accessibility",
                        rationale = "So Zenlock notices the moment an app opens. It never reads screen content.",
                        granted = permissions.accessibility,
                        onGrant = onGrantAccessibility,
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Layers,
                        title = "Draw over other apps",
                        rationale = "For the night filter and for showing the pause screen reliably.",
                        granted = permissions.overlay,
                        onGrant = onGrantOverlay,
                    )
                }
            }
        }

        if (rangeDays.isNotEmpty()) {
            item {
                SectionCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (range <= 7) "This week" else "Last 30 days",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            RangeToggle(range = range, onRangeChange = onRangeChange)
                        }
                        Spacer(Modifier.height(16.dp))
                        DayRangeChart(days = rangeDays)
                        Spacer(Modifier.height(14.dp))
                        RangeSummary(days = rangeDays)
                        if (range > 7 && daysRecorded in 1 until range) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Recording for $daysRecorded days so far · " +
                                    "the rest fills in as they pass.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }

        if (topApps.isNotEmpty()) {
            item {
                SectionCard(title = "Most used today") {
                    val peak = usage.msFor(topApps.first().packageName)
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        topApps.forEach { entry ->
                            val used = usage.msFor(entry.packageName)
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = Time.formatDuration(used),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                UsageBar(fraction = if (peak > 0) used.toFloat() / peak else 0f)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "MANAGED APPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (managed.isEmpty()) {
            item {
                SectionCard {
                    EmptyHint("Nothing set up yet. Open the Apps tab and pick one.")
                }
            }
        } else {
            items(managed, key = { it.packageName }) { rule ->
                val entry = byPackage[rule.packageName]
                val used = usage.msFor(rule.packageName)
                val limitMs = Time.minutesToMillis(rule.dailyLimitMinutes)
                val fraction = if (limitMs > 0) (used.toFloat() / limitMs).coerceIn(0f, 1f) else 0f

                SectionCard {
                    AppRow(
                        label = entry?.label ?: rule.packageName,
                        icon = entry?.icon,
                        detail = ruleSummary(rule.dailyLimitMinutes, rule.zenPauseSeconds, used),
                    )
                    if (rule.dailyLimitMinutes > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UsageBar(
                                fraction = fraction,
                                color = if (fraction >= 1f) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeToggle(range: Int, onRangeChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.background),
    ) {
        listOf(MainViewModel.WEEK to "7d", MainViewModel.MONTH to "30d").forEach { (days, label) ->
            val selected = range == days
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.background,
                    )
                    .clickable { onRangeChange(days) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * Today against the days before it. An average is the only honest comparison a short record
 * can make — "down 20% on yesterday" would just be noise.
 */
@Composable
private fun RangeSummary(days: List<DaySummary>) {
    val previous = days.dropLast(1).filter { it.totalMs > 0 }
    val today = days.last().totalMs
    val average = if (previous.isEmpty()) 0L else previous.sumOf { it.totalMs } / previous.size

    val delta = if (average > 0) ((today - average) * 100f / average).toInt() else 0
    val down = delta < 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Daily average ${Time.formatDuration(average)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (average > 0 && abs(delta) >= 5) {
            Text(
                text = (if (down) "↓ " else "↑ ") + "${abs(delta)}% today",
                style = MaterialTheme.typography.labelMedium,
                color = if (down) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (down) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.outlineVariant,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private fun ruleSummary(limitMinutes: Int, pauseSeconds: Int, usedMs: Long): String {
    val parts = mutableListOf<String>()
    if (limitMinutes > 0) {
        parts += "${Time.formatDuration(usedMs)} of ${limitMinutes}m"
    } else {
        parts += "${Time.formatDuration(usedMs)} today"
    }
    if (pauseSeconds > 0) parts += "${pauseSeconds}s pause"
    return parts.joinToString(" · ")
}
