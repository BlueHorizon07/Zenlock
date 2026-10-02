package com.zenlock.block

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenlock.data.BlockReason
import com.zenlock.util.Time
import kotlinx.coroutines.delay
import kotlin.math.cos

/**
 * The Zen pause — the screen this whole app exists to show.
 *
 * It has one job: make the reach conscious. A ring that drains, a circle that breathes at a
 * pace slow enough to follow, and the day's own count sitting underneath where it cannot be
 * argued with. The way through stays closed until the breath is finished, and then it is
 * offered quietly rather than withheld.
 */
@Composable
internal fun PauseScreen(
    label: String,
    icon: ImageBitmap?,
    seconds: Int,
    passMinutes: Int,
    usedMs: Long,
    opens: Int,
    limitMinutes: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(seconds) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(seconds) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        // A small physical note that the wait is over, so the phone need not be watched.
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val done = remaining == 0

    // One slow cycle: in for four seconds, out for four. Driven by a cosine so the turn at the
    // top and bottom is soft rather than a bounce.
    val breath = rememberInfiniteTransition(label = "breath")
    val cycle by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )
    val swell = 0.5f - 0.5f * cos(2f * Math.PI.toFloat() * cycle)
    val scale = 0.70f + 0.30f * swell
    val inhaling = cycle < 0.5f

    val drain by animateFloatAsState(
        targetValue = if (seconds == 0) 0f else remaining.toFloat() / seconds,
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "drain",
    )

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val breathColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // The breathing body, behind everything.
            Box(
                modifier = Modifier
                    .fillMaxSize(0.76f)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(breathColor),
            )

            // The countdown ring, draining clockwise from the top.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (drain > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * drain,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = !done, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = remaining.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                AnimatedVisibility(visible = !done, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = if (inhaling) "Breathe in" else "Breathe out",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                AnimatedVisibility(visible = done, enter = fadeIn(tween(600))) {
                    Text(
                        text = "Still?",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        AppIcon(icon)
        Spacer(Modifier.height(14.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        OpenDots(opens = opens)
        Spacer(Modifier.height(8.dp))

        Text(
            text = buildSubline(opens, usedMs, limitMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Not now", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(2.dp))
        TextButton(
            onClick = onOpen,
            enabled = done,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = if (done) "Open for ${passMinutes}m" else "Open in ${remaining}s",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Today's opens as marks rather than a number — thirty dots land harder than "30". */
@Composable
private fun OpenDots(opens: Int) {
    if (opens <= 0) return
    val shown = opens.coerceAtMost(MAX_DOTS)
    val accent = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(shown) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.35f + 0.65f * index / shown.coerceAtLeast(1))),
            )
        }
        if (opens > MAX_DOTS) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "+${opens - MAX_DOTS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BlockScreen(
    label: String,
    icon: ImageBitmap?,
    reason: BlockReason?,
    untilMinute: Int,
    usedMs: Long,
    opens: Int,
    limitMinutes: Int,
    onDismiss: () -> Unit,
) {
    val headline = when (reason) {
        BlockReason.LIMIT_REACHED -> "That's your time on $label"
        BlockReason.SLEEP_MODE -> "$label is asleep"
        BlockReason.BLOCKED_TODAY -> "$label is off for today"
        null -> "$label is blocked"
    }

    val detail = when (reason) {
        BlockReason.LIMIT_REACHED ->
            "You set ${limitMinutes}m a day. It comes back tomorrow morning."

        BlockReason.SLEEP_MODE ->
            if (untilMinute >= 0) "Sleep mode is on until ${Time.formatClock(untilMinute)}."
            else "Sleep mode is on."

        else -> "You turned this one off for the rest of today."
    }

    var appear by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { appear = 1 }
    val settle by animateFloatAsState(
        targetValue = if (appear == 1) 1f else 0.9f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "settle",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .scale(settle)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OpenDots(opens = opens)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${Time.formatDuration(usedMs)} today · $opens opens",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon == null) return
    Image(
        bitmap = icon,
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape),
    )
}

private fun buildSubline(opens: Int, usedMs: Long, limitMinutes: Int): String =
    if (limitMinutes > 0) {
        val left = (limitMinutes - usedMs / 60_000L).coerceAtLeast(0)
        "$opens opens today · ${left}m of ${limitMinutes}m left"
    } else {
        "$opens opens today · ${Time.formatDuration(usedMs)} used"
    }

private const val MAX_DOTS = 24
