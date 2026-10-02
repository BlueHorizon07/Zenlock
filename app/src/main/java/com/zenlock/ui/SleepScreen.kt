package com.zenlock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zenlock.data.SleepSchedule
import com.zenlock.data.ZenState
import com.zenlock.util.AppEntry
import com.zenlock.util.Time

@Composable
fun SleepScreen(
    state: ZenState,
    apps: List<AppEntry>,
    contentPadding: PaddingValues,
    onUpdateSleep: ((SleepSchedule) -> SleepSchedule) -> Unit,
    onSetAllowedInSleep: (String, Boolean) -> Unit,
) {
    val sleep = state.sleep
    var query by remember { mutableStateOf("") }

    // Allowed apps float to the top, but only as the screen is entered. Re-sorting on every
    // toggle would slide the row out from under the finger that just tapped it.
    val initiallyAllowed = remember(apps) {
        state.rules.values.filter { it.allowedInSleep }.map { it.packageName }.toSet()
    }
    val sorted = remember(apps, initiallyAllowed) {
        apps.sortedWith(
            compareByDescending<AppEntry> { it.packageName in initiallyAllowed }
                .thenBy { it.label.lowercase() },
        )
    }
    val ordered = remember(sorted, query) {
        if (query.isBlank()) sorted
        else sorted.filter { it.label.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SectionCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SwitchRow(
                    title = "Sleep mode",
                    subtitle = if (sleep.enabled) {
                        "${Time.formatClock(sleep.startMinute)} to ${Time.formatClock(sleep.endMinute)}"
                    } else {
                        "Everything closes except the apps you allow."
                    },
                    checked = sleep.enabled,
                    onCheckedChange = { enabled -> onUpdateSleep { it.copy(enabled = enabled) } },
                )
                TimeRow(
                    label = "Starts",
                    minuteOfDay = sleep.startMinute,
                    enabled = sleep.enabled,
                    onPicked = { minute -> onUpdateSleep { it.copy(startMinute = minute) } },
                )
                TimeRow(
                    label = "Ends",
                    minuteOfDay = sleep.endMinute,
                    enabled = sleep.enabled,
                    onPicked = { minute -> onUpdateSleep { it.copy(endMinute = minute) } },
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text(
                text = "ALLOWED DURING SLEEP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
            )
            Text(
                text = "Your phone app, clock and Settings always stay open. " +
                    "Anything you add here does too.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
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
        }

        items(ordered, key = { it.packageName }) { entry ->
            val allowed = state.ruleFor(entry.packageName)?.allowedInSleep == true
            AppRow(
                label = entry.label,
                icon = entry.icon,
                detail = null,
                onClick = { onSetAllowedInSleep(entry.packageName, !allowed) },
                trailing = {
                    Switch(
                        checked = allowed,
                        onCheckedChange = { onSetAllowedInSleep(entry.packageName, it) },
                    )
                },
            )
        }
    }
}
