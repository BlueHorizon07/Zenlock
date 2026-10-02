package com.zenlock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenlock.data.history.DaySummary
import com.zenlock.usage.UsageTracker
import com.zenlock.util.Time
import java.util.Calendar

/**
 * Counts up from zero the first time it appears. A total that lands softly reads as considered;
 * one that snaps into place reads as a readout.
 */
@Composable
fun AnimatedDuration(
    millis: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(millis) { target = millis.toFloat() }
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "duration",
    )
    Text(
        text = Time.formatDuration(value.toLong()),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Today, hour by hour. The shape of a day is the thing worth seeing — a wall at 1am says more
 * than any total does.
 */
@Composable
fun HourlyChart(
    hourly: List<Long>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outlineVariant
    val peak = (hourly.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val currentHour = remember(hourly) {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    var reveal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hourly) { reveal = 1f }
    val grow by animateFloatAsState(
        targetValue = reveal,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "bars",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
        ) {
            val gap = 3.dp.toPx()
            val barWidth = (size.width - gap * (UsageTracker.HOURS - 1)) / UsageTracker.HOURS
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            val floor = 3.dp.toPx()

            hourly.forEachIndexed { hour, value ->
                val ratio = value.toFloat() / peak
                val full = (size.height - floor) * ratio + floor
                val barHeight = (full * grow).coerceAtLeast(floor)
                val x = hour * (barWidth + gap)

                drawRoundRect(
                    color = when {
                        value == 0L -> quiet
                        hour == currentHour -> accent
                        else -> accent.copy(alpha = 0.42f)
                    },
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("12am", "6am", "12pm", "6pm", "11pm").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = when (label) {
                        "12am" -> TextAlign.Start
                        "11pm" -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A run of days. Up to ten of them get their own label and value; beyond that the labels stop
 * fitting, so a month renders as a bare run of bars with the ends dated.
 */
@Composable
fun DayRangeChart(
    days: List<DaySummary>,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    if (days.size <= 10) LabelledDays(days, modifier) else CompactDays(days, modifier)
}

@Composable
private fun LabelledDays(days: List<DaySummary>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outlineVariant
    val peak = (days.maxOfOrNull { it.totalMs } ?: 0L).coerceAtLeast(1L)
    val grow = growth(days)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Time.formatDuration(day.totalMs).substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(6.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                ) {
                    val ratio = day.totalMs.toFloat() / peak
                    val floor = 4.dp.toPx()
                    val height = ((size.height - floor) * ratio + floor) * grow
                    // Narrower than the column, and the radius is clamped by the shorter side
                    // so a low bar stays a bar instead of collapsing into a circle.
                    val barWidth = size.width * 0.66f
                    val radius = minOf(barWidth, height) / 2
                    drawRoundRect(
                        color = if (day.isToday) accent else if (day.totalMs == 0L) quiet
                        else accent.copy(alpha = 0.38f),
                        topLeft = Offset((size.width - barWidth) / 2, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Time.dayInitial(day.dayStart),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun CompactDays(days: List<DaySummary>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outlineVariant
    val peak = (days.maxOfOrNull { it.totalMs } ?: 0L).coerceAtLeast(1L)
    val grow = growth(days)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            val gap = 2.5.dp.toPx()
            val barWidth = (size.width - gap * (days.size - 1)) / days.size
            val floor = 3.dp.toPx()

            days.forEachIndexed { index, day ->
                val ratio = day.totalMs.toFloat() / peak
                val height = (((size.height - floor) * ratio + floor) * grow)
                    .coerceAtLeast(floor)
                val radius = CornerRadius(
                    minOf(barWidth, height) / 2,
                    minOf(barWidth, height) / 2,
                )
                drawRoundRect(
                    color = when {
                        day.isToday -> accent
                        day.totalMs == 0L -> quiet
                        else -> accent.copy(alpha = 0.38f)
                    },
                    topLeft = Offset(index * (barWidth + gap), size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = Time.shortDate(days.first().dayStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Bars grow in once when the data they describe changes. */
@Composable
private fun growth(key: Any): Float {
    var reveal by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(key) { reveal = 1f }
    val grow by animateFloatAsState(
        targetValue = reveal,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "grow",
    )
    return grow
}

/** A thin progress bar for one app's share of the day, or its share of a limit. */
@Composable
fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val width by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "usageBar",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(vertical = 0.dp),
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, size = size, cornerRadius = radius)
        if (width > 0f) {
            drawRoundRect(
                color = color,
                size = Size((size.width * width).coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}
