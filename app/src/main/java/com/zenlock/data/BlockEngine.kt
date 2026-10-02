package com.zenlock.data

import com.zenlock.util.Time

enum class BlockReason { LIMIT_REACHED, SLEEP_MODE, BLOCKED_TODAY }

sealed interface Decision {
    /** Let the app through untouched. */
    data object Allow : Decision

    /** Hard stop. [untilMinute] is set only when the block lifts at a known clock time. */
    data class Block(val reason: BlockReason, val untilMinute: Int? = null) : Decision

    /** Soft stop: make the user sit through [seconds] before the app opens. */
    data class Pause(val seconds: Int) : Decision
}

/**
 * Pure decision logic — no Android types, so it is trivially testable and the service stays thin.
 */
object BlockEngine {

    fun decide(
        pkg: String,
        state: ZenState,
        usedTodayMs: Long,
        nowMinute: Int = Time.nowMinuteOfDay(),
        passActive: Boolean = false,
    ): Decision {
        if (!state.enabled) return Decision.Allow

        val rule = state.ruleFor(pkg)

        // Sleep mode is an allowlist: anything not explicitly permitted is closed.
        val sleepActive = state.sleep.enabled &&
            Time.isWithinWindow(state.sleep.startMinute, state.sleep.endMinute, nowMinute)
        if (sleepActive && rule?.allowedInSleep != true) {
            return Decision.Block(BlockReason.SLEEP_MODE, state.sleep.endMinute)
        }

        if (rule == null) return Decision.Allow
        if (rule.blockedToday) return Decision.Block(BlockReason.BLOCKED_TODAY)

        if (rule.dailyLimitMinutes > 0 &&
            usedTodayMs >= Time.minutesToMillis(rule.dailyLimitMinutes)
        ) {
            return Decision.Block(BlockReason.LIMIT_REACHED)
        }

        // A pass earned by sitting through a pause only bypasses the pause, never a hard block.
        if (passActive) return Decision.Allow
        if (rule.zenPauseSeconds > 0) return Decision.Pause(rule.zenPauseSeconds)

        return Decision.Allow
    }
}
