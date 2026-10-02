package com.zenlock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenlock.data.FilterSchedule
import com.zenlock.data.ZenState
import com.zenlock.ui.theme.ThemeMode
import com.zenlock.ui.theme.ZenPalette
import com.zenlock.util.Time

@Composable
fun DisplayScreen(
    state: ZenState,
    permissions: PermissionState,
    contentPadding: PaddingValues,
    onUpdateFilter: ((FilterSchedule) -> FilterSchedule) -> Unit,
    onSetPalette: (String) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onGrantOverlay: () -> Unit,
) {
    val filter = state.filter
    var exactWarmth by remember { mutableStateOf(false) }
    var exactDim by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                top = 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = 20.dp,
                end = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard(title = "Appearance") {
            PaletteChooser(
                selected = ZenPalette.from(state.palette),
                mode = ThemeMode.from(state.themeMode),
                onSelect = { onSetPalette(it.name) },
            )
            ChoiceRow(
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.from(state.themeMode).label,
                onSelect = { label ->
                    val mode = ThemeMode.entries.first { it.label == label }
                    onSetThemeMode(mode.name)
                },
            )
        }

        if (!permissions.overlay) {
            SectionCard(title = "Needs permission") {
                PermissionRow(
                    icon = Icons.Rounded.Layers,
                    title = "Draw over other apps",
                    rationale = "The filter is an overlay, so Android needs this before it can appear.",
                    granted = false,
                    onGrant = onGrantOverlay,
                )
            }
        }

        SectionCard(title = "Night filter") {
            SwitchRow(
                title = "On right now",
                subtitle = "Ignores the schedule until you switch it back off.",
                checked = filter.forcedOn,
                enabled = permissions.overlay,
                onCheckedChange = { on -> onUpdateFilter { it.copy(forcedOn = on) } },
            )
            SwitchRow(
                title = "Every night",
                subtitle = if (filter.enabled) {
                    "${Time.formatClock(filter.startMinute)} to ${Time.formatClock(filter.endMinute)}"
                } else {
                    "Fade the screen warm and dark on a timer."
                },
                checked = filter.enabled,
                enabled = permissions.overlay,
                onCheckedChange = { on -> onUpdateFilter { it.copy(enabled = on) } },
            )
            TimeRow(
                label = "Starts",
                minuteOfDay = filter.startMinute,
                enabled = filter.enabled && permissions.overlay,
                onPicked = { minute -> onUpdateFilter { it.copy(startMinute = minute) } },
            )
            TimeRow(
                label = "Ends",
                minuteOfDay = filter.endMinute,
                enabled = filter.enabled && permissions.overlay,
                onPicked = { minute -> onUpdateFilter { it.copy(endMinute = minute) } },
            )
        }

        SectionCard(title = "Strength") {
            SliderRow(
                label = "Warmth",
                value = filter.warmth.toFloat(),
                range = 0f..100f,
                enabled = permissions.overlay,
                format = { if (it.toInt() == 0) "Off" else "${it.toInt()}%" },
                onCustom = { exactWarmth = true },
                onCommit = { value -> onUpdateFilter { it.copy(warmth = value.toInt()) } },
            )
            SliderRow(
                label = "Extra dimming",
                value = filter.dim.toFloat(),
                range = 0f..80f,
                enabled = permissions.overlay,
                format = { if (it.toInt() == 0) "Off" else "${it.toInt()}%" },
                onCustom = { exactDim = true },
                onCommit = { value -> onUpdateFilter { it.copy(dim = value.toInt()) } },
            )
            Text(
                text = "Dimming is drawn on top of the screen rather than sent to the backlight, " +
                    "so it can go darker than your phone's own minimum brightness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
    }

    if (exactWarmth) {
        ExactValueDialog(
            title = "Warmth",
            unit = "%",
            initial = filter.warmth,
            range = 0..100,
            helper = "How much amber sits over the screen. 0 turns the warmth off.",
            onDismiss = { exactWarmth = false },
            onConfirm = { value ->
                onUpdateFilter { it.copy(warmth = value) }
                exactWarmth = false
            },
        )
    }

    if (exactDim) {
        ExactValueDialog(
            title = "Extra dimming",
            unit = "%",
            initial = filter.dim,
            range = 0..80,
            helper = "Darkening beyond your phone's minimum brightness. 0 turns dimming off.",
            onDismiss = { exactDim = false },
            onConfirm = { value ->
                onUpdateFilter { it.copy(dim = value) }
                exactDim = false
            },
        )
    }
}

@Composable
private fun PaletteChooser(
    selected: ZenPalette,
    mode: ThemeMode,
    onSelect: (ZenPalette) -> Unit,
) {
    // Swatches preview each palette in the mode it will actually be seen in.
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ZenPalette.entries.forEach { palette ->
            val (accent, container) = palette.swatch(dark)
            val isSelected = palette == selected

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelect(palette) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(container)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = palette.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
