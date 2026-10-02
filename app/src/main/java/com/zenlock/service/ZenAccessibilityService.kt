package com.zenlock.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.zenlock.block.BlockActivity
import com.zenlock.data.BlockEngine
import com.zenlock.data.BlockReason
import com.zenlock.data.Decision
import com.zenlock.data.ZenPasses
import com.zenlock.data.ZenState
import com.zenlock.data.ZenStore
import com.zenlock.usage.UsageTracker
import com.zenlock.util.Permissions
import com.zenlock.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The enforcement point. Watches which app comes to the foreground and, when the rules say so,
 * puts [BlockActivity] in front of it.
 *
 * It deliberately does not call [getRootInActiveWindow] or read window content — the config
 * declares `canRetrieveWindowContent="false"`, which keeps the Play Store accessibility
 * declaration honest and narrow.
 */
class ZenAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var tracker: UsageTracker
    private lateinit var store: ZenStore

    @Volatile
    private var state: ZenState? = null

    /** Packages we never touch: our own UI, the launcher, system UI, the dialer, Settings. */
    @Volatile
    private var exempt: Set<String> = emptySet()

    @Volatile
    private var foregroundPackage: String? = null

    private var lastInterceptPackage: String? = null
    private var lastInterceptAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = UsageTracker(this)
        store = ZenStore.get(this)
        exempt = buildExemptSet()

        scope.launch {
            store.state.collectLatest { state = it }
        }
        scope.launch {
            // Catches limits that run out while an app is already on screen.
            while (isActive) {
                delay(RECHECK_INTERVAL_MS)
                foregroundPackage?.let { evaluate(it, fromTick = true) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in exempt) {
            // Forget what was on screen. Leaving the last blocked package here meant the
            // re-check tick kept firing against it from the launcher, relaunching the block
            // screen every fifteen seconds after the user had already walked away.
            foregroundPackage = null
            return
        }
        foregroundPackage = pkg
        evaluate(pkg, fromTick = false)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun evaluate(pkg: String, fromTick: Boolean) {
        val current = state ?: return
        if (!current.enabled) return
        // Without usage access we cannot measure limits; enforcing half the rules would be worse
        // than enforcing none, so stay out of the way until the user grants it.
        if (!Permissions.hasUsageAccess(this)) return

        val usage = tracker.snapshot()
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = current,
            usedTodayMs = usage.msFor(pkg),
            nowMinute = Time.nowMinuteOfDay(),
            passActive = ZenPasses.isActive(pkg),
        )
        if (decision is Decision.Allow) return

        // The limit just ran out. If this rule was set to hold, close it now, so the lock does
        // not depend on the app being opened again later.
        if (decision is Decision.Block && decision.reason == BlockReason.LIMIT_REACHED) {
            val rule = current.ruleFor(pkg)
            if (rule != null && rule.lockWhenSpent && !rule.isLockedOn(Time.startOfDay())) {
                scope.launch { store.stampLock(pkg) }
            }
        }

        // A pause that is already satisfied should not reappear on every window change inside
        // the app; a hard block should not flicker if several windows open at once.
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastInterceptPackage && now - lastInterceptAt < INTERCEPT_DEBOUNCE_MS) return
        lastInterceptPackage = pkg
        lastInterceptAt = now

        if (fromTick && decision is Decision.Pause) return

        startActivity(
            BlockActivity.intentFor(
                context = this,
                packageName = pkg,
                decision = decision,
                usedTodayMs = usage.msFor(pkg),
                opensToday = usage.opensFor(pkg),
                limitMinutes = current.ruleFor(pkg)?.dailyLimitMinutes ?: 0,
                passMinutes = current.passMinutes,
            ),
        )
    }

    private fun buildExemptSet(): Set<String> {
        val pm = packageManager
        val exempt = mutableSetOf(packageName, "com.android.systemui", "android")

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, 0).forEach { exempt += it.activityInfo.packageName }

        val dial = Intent(Intent.ACTION_DIAL)
        pm.resolveActivity(dial, 0)?.activityInfo?.packageName?.let { exempt += it }

        // Never lock the user out of the screen where they can turn Zenlock off.
        ComponentName.unflattenFromString("com.android.settings/.Settings")
            ?.packageName?.let { exempt += it }

        return exempt
    }

    companion object {
        private const val RECHECK_INTERVAL_MS = 15_000L
        private const val INTERCEPT_DEBOUNCE_MS = 1_200L
    }
}
