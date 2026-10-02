package com.zenlock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zenlock.data.AppRule
import com.zenlock.data.FilterSchedule
import com.zenlock.data.SleepSchedule
import com.zenlock.data.ZenState
import com.zenlock.data.ZenStore
import com.zenlock.data.history.DaySummary
import com.zenlock.data.history.HistoryRepository
import com.zenlock.service.ScreenFilterService
import com.zenlock.usage.UsageTracker
import com.zenlock.util.AppCatalog
import com.zenlock.util.AppEntry
import com.zenlock.util.Permissions
import com.zenlock.util.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
) {
    val allGranted: Boolean get() = usageAccess && accessibility && overlay
    val missingCount: Int get() = listOf(usageAccess, accessibility, overlay).count { !it }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ZenStore.get(application)
    private val tracker = UsageTracker(application)
    private val history = HistoryRepository(application)

    val state: StateFlow<ZenState> = store.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZenState())

    /** False until DataStore has answered once, so first-run UI is not shown to returning users. */
    val loaded: StateFlow<Boolean> = store.state
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _usage = MutableStateFlow(UsageTracker.Snapshot.EMPTY)
    val usage: StateFlow<UsageTracker.Snapshot> = _usage.asStateFlow()

    private val _rangeDays = MutableStateFlow<List<DaySummary>>(emptyList())
    val rangeDays: StateFlow<List<DaySummary>> = _rangeDays.asStateFlow()

    private val _range = MutableStateFlow(WEEK)
    val range: StateFlow<Int> = _range.asStateFlow()

    /** How far the stored record actually reaches, so the month view can admit when it is thin. */
    private val _daysRecorded = MutableStateFlow(0)
    val daysRecorded: StateFlow<Int> = _daysRecorded.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    init {
        viewModelScope.launch {
            // Names first so the screen has real numbers immediately, icons straight after.
            val labels = AppCatalog.loadLabels(getApplication())
            _apps.value = labels
            // The charts count launchable apps only; the launcher and system UI are foreground
            // for much of the day and would swamp every bar.
            tracker.countedPackages = labels.map { it.packageName }.toSet() +
                getApplication<Application>().packageName
            tracker.invalidate()
            _usage.value = tracker.snapshot()
            reloadHistory()
            _apps.value = AppCatalog.load(getApplication())
        }
        refresh()
    }

    /** Called whenever the UI comes back to the foreground. */
    fun refresh() {
        val context = getApplication<Application>()
        _permissions.value = PermissionState(
            usageAccess = Permissions.hasUsageAccess(context),
            accessibility = Permissions.hasAccessibility(context),
            overlay = Permissions.hasOverlay(context),
        )
        tracker.invalidate()
        _usage.value = tracker.snapshot()
        viewModelScope.launch {
            closeSpentLocks()
            reloadHistory()
        }
    }

    /**
     * Arms any lock whose limit has run out. The accessibility service does the same when it
     * blocks, so a lock closes whether or not the app was opened afterwards.
     */
    private suspend fun closeSpentLocks() {
        val today = Time.startOfDay()
        val snapshot = _usage.value
        store.current().rules.values.forEach { rule ->
            val spent = rule.dailyLimitMinutes > 0 &&
                snapshot.msFor(rule.packageName) >= Time.minutesToMillis(rule.dailyLimitMinutes)
            if (rule.lockWhenSpent && spent && !rule.isLockedOn(today)) {
                store.stampLock(rule.packageName)
            }
        }
    }

    fun setRange(days: Int) {
        if (_range.value == days) return
        _range.value = days
        viewModelScope.launch { _rangeDays.value = history.dailyTotals(days) }
    }

    /**
     * Rolls the system's retained window into the database, then reads the chart back out of
     * it. Everything longer than a week exists only because this ran.
     */
    private suspend fun reloadHistory() {
        if (tracker.countedPackages == null) return
        history.sync(tracker)
        _rangeDays.value = history.dailyTotals(_range.value)
        _daysRecorded.value = history.daysRecorded()
    }

    fun setEnabled(enabled: Boolean) {
        edit { it.copy(enabled = enabled) }
    }

    fun setDailyLimit(pkg: String, minutes: Int) {
        editRule(pkg) { it.copy(dailyLimitMinutes = minutes.coerceIn(0, 12 * 60)) }
    }

    fun setPauseSeconds(pkg: String, seconds: Int) {
        editRule(pkg) { it.copy(zenPauseSeconds = seconds.coerceIn(0, 60)) }
    }

    fun setAllowedInSleep(pkg: String, allowed: Boolean) {
        editRule(pkg) { it.copy(allowedInSleep = allowed) }
    }

    fun setBlockedToday(pkg: String, blocked: Boolean) {
        editRule(pkg) { it.copy(blockedToday = blocked) }
    }

    fun setLockWhenSpent(pkg: String, locked: Boolean) {
        // Sequential on purpose: launching the write and the check separately let the check
        // read the rule before the switch had landed, so the lock stayed open until the next
        // resume. Arming it has to be part of the same write.
        viewModelScope.launch {
            store.updateRule(pkg) { it.copy(lockWhenSpent = locked) }
            closeSpentLocks()
        }
    }

    fun clearRule(pkg: String) {
        viewModelScope.launch { store.removeRule(pkg) }
    }

    fun updateSleep(transform: (SleepSchedule) -> SleepSchedule) {
        edit { it.copy(sleep = transform(it.sleep)) }
    }

    fun updateFilter(transform: (FilterSchedule) -> FilterSchedule) {
        viewModelScope.launch {
            store.update { it.copy(filter = transform(it.filter)) }
            // The overlay service only exists while it has something to do.
            ScreenFilterService.sync(getApplication(), store.current().filter)
        }
    }

    fun setPassMinutes(minutes: Int) {
        edit { it.copy(passMinutes = minutes.coerceIn(1, 60)) }
    }

    fun setPalette(name: String) {
        edit { it.copy(palette = name) }
    }

    fun setThemeMode(name: String) {
        edit { it.copy(themeMode = name) }
    }

    fun completeOnboarding() {
        edit { it.copy(onboardingDone = true) }
    }

    fun ruleFor(pkg: String): AppRule? = state.value.ruleFor(pkg)

    private fun edit(transform: (ZenState) -> ZenState) {
        viewModelScope.launch { store.update(transform) }
    }

    private fun editRule(pkg: String, transform: (AppRule) -> AppRule) {
        viewModelScope.launch { store.updateRule(pkg, transform) }
    }

    companion object {
        const val WEEK = 7
        const val MONTH = 30
    }
}
