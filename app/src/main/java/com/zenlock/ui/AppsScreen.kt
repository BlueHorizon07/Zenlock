package com.zenlock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zenlock.data.AppRule
import com.zenlock.data.ZenState
import com.zenlock.usage.UsageTracker
import com.zenlock.util.AppEntry
import com.zenlock.util.Time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: ZenState,
    usage: UsageTracker.Snapshot,
    apps: List<AppEntry>,
    contentPadding: PaddingValues,
    onSetLimit: (String, Int) -> Unit,
    onSetPause: (String, Int) -> Unit,
    onSetBlockedToday: (String, Boolean) -> Unit,
    onSetLockWhenSpent: (String, Boolean) -> Unit,
    onClearRule: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Keyed on the boolean, not on the snapshot: the order settles once when usage first
    // lands and then stays put, so a refresh mid-scroll cannot reshuffle rows underfoot.
    val hasUsage = usage.foregroundMs.isNotEmpty()
    val ordered = remember(apps, hasUsage) {
        apps.sortedWith(
            compareByDescending<AppEntry> { usage.msFor(it.packageName) }
                .thenBy { it.label.lowercase() },
        )
    }
    val filtered = remember(ordered, query) {
        if (query.isBlank()) ordered
        else ordered.filter { it.label.contains(query, ignoreCase = true) }
    }

    // That one re-sort would otherwise leave keyed items pinned mid-alphabet.
    LaunchedEffect(hasUsage) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (filtered.isEmpty()) {
                item { EmptyHint(if (apps.isEmpty()) "Loading apps..." else "No app matches that.") }
            }

            items(filtered, key = { it.packageName }) { entry ->
                val rule = state.ruleFor(entry.packageName)
                AppRow(
                    label = entry.label,
                    icon = entry.icon,
                    detail = Time.formatDuration(usage.msFor(entry.packageName)) +
                        " · " + usage.opensFor(entry.packageName) + " opens",
                    onClick = { editing = entry.packageName },
                    trailing = { RuleChip(rule) },
                )
            }
        }
    }

    val editingPackage = editing
    if (editingPackage != null) {
        val entry = apps.firstOrNull { it.packageName == editingPackage }
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            AppRuleSheet(
                label = entry?.label ?: editingPackage,
                rule = state.ruleFor(editingPackage),
                usedMs = usage.msFor(editingPackage),
                opens = usage.opensFor(editingPackage),
                onSetLimit = { onSetLimit(editingPackage, it) },
                onSetPause = { onSetPause(editingPackage, it) },
                onSetBlockedToday = { onSetBlockedToday(editingPackage, it) },
                onSetLockWhenSpent = { onSetLockWhenSpent(editingPackage, it) },
                onClear = {
                    onClearRule(editingPackage)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun RuleChip(rule: AppRule?) {
    val text = when {
        rule == null || !rule.isManaged -> return
        rule.blockedToday -> "Off today"
        rule.dailyLimitMinutes > 0 && rule.zenPauseSeconds > 0 ->
            "${rule.dailyLimitMinutes}m · ${rule.zenPauseSeconds}s"

        rule.dailyLimitMinutes > 0 -> "${rule.dailyLimitMinutes}m"
        else -> "${rule.zenPauseSeconds}s pause"
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun AppRuleSheet(
    label: String,
    rule: AppRule?,
    usedMs: Long,
    opens: Int,
    onSetLimit: (Int) -> Unit,
    onSetPause: (Int) -> Unit,
    onSetBlockedToday: (Boolean) -> Unit,
    onSetLockWhenSpent: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val limit = rule?.dailyLimitMinutes ?: 0
    val pause = rule?.zenPauseSeconds ?: 0
    val locked = rule?.isLockedOn(Time.startOfDay()) == true
    var exactLimit by remember { mutableStateOf(false) }
    var exactPause by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "${Time.formatDuration(usedMs)} and $opens opens today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (locked) {
            Spacer(Modifier.height(16.dp))
            SectionCard {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        text = "Locked until tomorrow",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Today's limit is spent. You asked not to be able to talk " +
                            "yourself out of it, so the only changes allowed now are stricter " +
                            "ones. It opens again at midnight.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionCard {
            SliderRow(
                label = "Daily limit",
                enabled = !locked,
                value = limit.toFloat(),
                range = 0f..240f,
                format = { if (it.toInt() == 0) "Off" else "${it.toInt()}m" },
                onCustom = if (locked) null else ({ exactLimit = true }),
                onCommit = { onSetLimit(it.toInt()) },
            )
            Text(
                text = "Once the limit is spent the app stays shut until tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            SliderRow(
                label = "Zen pause",
                value = pause.toFloat(),
                range = 0f..30f,
                format = { if (it.toInt() == 0) "Off" else "${it.toInt()}s" },
                onCustom = { exactPause = true },
                onCommit = { onSetPause(it.toInt()) },
            )
            Text(
                text = "A breath and a look at today's count before the app opens. " +
                    "This is the setting that actually moves the number.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            SwitchRow(
                title = "Lock it in",
                subtitle = if (locked) {
                    "Holding until midnight."
                } else {
                    "Once today's limit is spent, this app cannot be raised, switched off, " +
                        "or removed until tomorrow \u2014 and Zenlock itself cannot be " +
                        "turned off while it holds."
                },
                checked = rule?.lockWhenSpent == true,
                enabled = !locked,
                onCheckedChange = onSetLockWhenSpent,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            SwitchRow(
                title = "Close for the rest of today",
                subtitle = "Resets on its own tomorrow morning.",
                checked = rule?.blockedToday == true,
                enabled = !locked,
                onCheckedChange = onSetBlockedToday,
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = onClear,
            enabled = !locked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (locked) "Locked until midnight" else "Remove all rules for this app",
                textAlign = TextAlign.Center,
                color = if (locked) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.error,
            )
        }
    }

    if (exactLimit) {
        ExactValueDialog(
            title = "Daily limit",
            unit = "min",
            initial = limit,
            range = 0..720,
            helper = "Total foreground time allowed per day. Leave blank for no limit.",
            onDismiss = { exactLimit = false },
            onConfirm = {
                onSetLimit(it)
                exactLimit = false
            },
        )
    }

    if (exactPause) {
        ExactValueDialog(
            title = "Zen pause",
            unit = "sec",
            initial = pause,
            range = 0..60,
            helper = "How long you sit with the breath before the app can open. " +
                "Ten seconds is enough to break the reflex; sixty is a decision.",
            onDismiss = { exactPause = false },
            onConfirm = {
                onSetPause(it)
                exactPause = false
            },
        )
    }
}
