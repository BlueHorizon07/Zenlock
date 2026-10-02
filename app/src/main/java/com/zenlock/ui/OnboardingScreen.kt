package com.zenlock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First run. All three permissions live on Settings screens we cannot show inline, so the job
 * here is to say plainly what each one is for and hand the user straight to it — and to be
 * honest that the app does nothing at all until they are granted.
 */
@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    onGrantUsage: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Zenlock",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A pause between you and the app you reached for without thinking.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Zenlock needs three permissions before it can do anything. Android will " +
                "not let any app grant these itself — Allow opens the exact Settings switch, " +
                "and you come straight back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        SectionCard {
            PermissionRow(
                icon = Icons.Rounded.QueryStats,
                title = "Usage access",
                rationale = "Measures how long you have been in each app today.",
                granted = permissions.usageAccess,
                onGrant = onGrantUsage,
            )
            PermissionRow(
                icon = Icons.Rounded.Accessibility,
                title = "Accessibility",
                rationale = "Tells Zenlock the moment an app opens. It reads no screen " +
                    "content and nothing leaves your phone.",
                granted = permissions.accessibility,
                onGrant = onGrantAccessibility,
            )
            PermissionRow(
                icon = Icons.Rounded.Layers,
                title = "Draw over other apps",
                rationale = "Needed for the night filter and to show the pause screen " +
                    "reliably.",
                granted = permissions.overlay,
                onGrant = onGrantOverlay,
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            enabled = permissions.allGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (permissions.allGranted) "Start" else "Grant all three to continue",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No account, no servers, no analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}
