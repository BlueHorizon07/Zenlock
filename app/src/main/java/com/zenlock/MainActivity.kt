package com.zenlock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zenlock.ui.AppsScreen
import com.zenlock.ui.DisplayScreen
import com.zenlock.ui.HomeScreen
import com.zenlock.ui.MainViewModel
import com.zenlock.ui.OnboardingScreen
import com.zenlock.ui.SleepScreen
import com.zenlock.ui.theme.ThemeMode
import com.zenlock.ui.theme.ZenPalette
import com.zenlock.ui.theme.ZenlockTheme
import com.zenlock.util.Permissions
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Today", Icons.Rounded.SelfImprovement),
    Apps("Apps", Icons.Rounded.Apps),
    Sleep("Sleep", Icons.Rounded.Bedtime),
    Display("Screen", Icons.Rounded.Contrast),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZenlockRoot() }
    }
}

@Composable
private fun ZenlockRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val rangeDays by viewModel.rangeDays.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val daysRecorded by viewModel.daysRecorded.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    // Permissions are granted on Settings screens we do not control, so re-read them every time
    // the user comes back, along with fresh usage numbers.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The filter still works without it; the notification just will not show. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ZenlockTheme(
        palette = ZenPalette.from(state.palette),
        themeMode = ThemeMode.from(state.themeMode),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                // Hold the empty background for the one frame it takes DataStore to answer,
                // rather than flashing onboarding at someone who finished it months ago.
                !loaded -> Unit

                !state.onboardingDone -> OnboardingScreen(
                    permissions = permissions,
                    onGrantUsage = { Permissions.openUsageAccess(context) },
                    onGrantAccessibility = { Permissions.openAccessibility(context) },
                    onGrantOverlay = { Permissions.openOverlay(context) },
                    onContinue = viewModel::completeOnboarding,
                )

                else -> MainTabs(
                    viewModel = viewModel,
                    state = state,
                    usage = usage,
                    rangeDays = rangeDays,
                    range = range,
                    daysRecorded = daysRecorded,
                    apps = apps,
                    permissions = permissions,
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    viewModel: MainViewModel,
    state: com.zenlock.data.ZenState,
    usage: com.zenlock.usage.UsageTracker.Snapshot,
    rangeDays: List<com.zenlock.data.history.DaySummary>,
    range: Int,
    daysRecorded: Int,
    apps: List<com.zenlock.util.AppEntry>,
    permissions: com.zenlock.ui.PermissionState,
) {
    val context = LocalContext.current
    val tabs = Tab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = {
                            Text(entry.label, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 1,
        ) { page ->
            when (tabs[page]) {
                Tab.Home -> HomeScreen(
                    state = state,
                    usage = usage,
                    rangeDays = rangeDays,
                    range = range,
                    daysRecorded = daysRecorded,
                    onRangeChange = viewModel::setRange,
                    apps = apps,
                    permissions = permissions,
                    contentPadding = innerPadding,
                    onToggleEnabled = viewModel::setEnabled,
                    onGrantUsage = { Permissions.openUsageAccess(context) },
                    onGrantAccessibility = { Permissions.openAccessibility(context) },
                    onGrantOverlay = { Permissions.openOverlay(context) },
                )

                Tab.Apps -> AppsScreen(
                    state = state,
                    usage = usage,
                    apps = apps,
                    contentPadding = innerPadding,
                    onSetLimit = viewModel::setDailyLimit,
                    onSetPause = viewModel::setPauseSeconds,
                    onSetBlockedToday = viewModel::setBlockedToday,
                    onSetLockWhenSpent = viewModel::setLockWhenSpent,
                    onClearRule = viewModel::clearRule,
                )

                Tab.Sleep -> SleepScreen(
                    state = state,
                    apps = apps,
                    contentPadding = innerPadding,
                    onUpdateSleep = viewModel::updateSleep,
                    onSetAllowedInSleep = viewModel::setAllowedInSleep,
                )

                Tab.Display -> DisplayScreen(
                    state = state,
                    permissions = permissions,
                    contentPadding = innerPadding,
                    onUpdateFilter = viewModel::updateFilter,
                    onSetPalette = viewModel::setPalette,
                    onSetThemeMode = viewModel::setThemeMode,
                    onGrantOverlay = { Permissions.openOverlay(context) },
                )
            }
        }
    }
}
