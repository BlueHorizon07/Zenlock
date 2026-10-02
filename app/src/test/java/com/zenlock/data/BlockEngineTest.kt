package com.zenlock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BlockEngine] is deliberately free of Android types, so its whole rule precedence can be
 * pinned down here without an emulator. These tests are the reason the engine is a pure
 * function rather than logic smeared through the accessibility service.
 */
class BlockEngineTest {

    private val pkg = "com.example.social"

    private fun minutes(n: Int) = n * 60_000L

    private fun state(
        enabled: Boolean = true,
        rule: AppRule? = null,
        sleep: SleepSchedule = SleepSchedule(),
    ) = ZenState(
        enabled = enabled,
        rules = rule?.let { mapOf(it.packageName to it) } ?: emptyMap(),
        sleep = sleep,
    )

    @Test
    fun `master switch off allows everything`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(enabled = false, rule = AppRule(pkg, blockedToday = true)),
            usedTodayMs = minutes(600),
            nowMinute = 12 * 60,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `an unmanaged app is allowed`() {
        val decision = BlockEngine.decide(pkg, state(), usedTodayMs = minutes(300), nowMinute = 600)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `sleep mode blocks apps that are not on the allowlist`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(sleep = SleepSchedule(enabled = true, startMinute = 1320, endMinute = 420)),
            usedTodayMs = 0,
            nowMinute = 23 * 60,
        )
        assertEquals(Decision.Block(BlockReason.SLEEP_MODE, 420), decision)
    }

    @Test
    fun `sleep mode lets an allowlisted app through`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(
                rule = AppRule(pkg, allowedInSleep = true),
                sleep = SleepSchedule(enabled = true, startMinute = 1320, endMinute = 420),
            ),
            usedTodayMs = 0,
            nowMinute = 2 * 60,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `sleep window that wraps midnight is inactive during the day`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(sleep = SleepSchedule(enabled = true, startMinute = 1320, endMinute = 420)),
            usedTodayMs = 0,
            nowMinute = 15 * 60,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `an app closed for the day is blocked`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, blockedToday = true)),
            usedTodayMs = 0,
            nowMinute = 600,
        )
        assertEquals(Decision.Block(BlockReason.BLOCKED_TODAY, null), decision)
    }

    @Test
    fun `usage under the limit is allowed`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, dailyLimitMinutes = 30)),
            usedTodayMs = minutes(29),
            nowMinute = 600,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `the limit blocks the moment it is exactly reached`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, dailyLimitMinutes = 30)),
            usedTodayMs = minutes(30),
            nowMinute = 600,
        )
        assertEquals(Decision.Block(BlockReason.LIMIT_REACHED, null), decision)
    }

    @Test
    fun `a configured pause is returned with its own duration`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, zenPauseSeconds = 15)),
            usedTodayMs = minutes(5),
            nowMinute = 600,
        )
        assertEquals(Decision.Pause(15), decision)
    }

    @Test
    fun `an active pass skips the pause`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, zenPauseSeconds = 15)),
            usedTodayMs = minutes(5),
            nowMinute = 600,
            passActive = true,
        )
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `a pass does not buy past a spent limit`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, dailyLimitMinutes = 30, zenPauseSeconds = 15)),
            usedTodayMs = minutes(31),
            nowMinute = 600,
            passActive = true,
        )
        assertEquals(Decision.Block(BlockReason.LIMIT_REACHED, null), decision)
    }

    @Test
    fun `sleep mode outranks a limit that has not been spent`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(
                rule = AppRule(pkg, dailyLimitMinutes = 120),
                sleep = SleepSchedule(enabled = true, startMinute = 1320, endMinute = 420),
            ),
            usedTodayMs = 0,
            nowMinute = 1380,
        )
        assertTrue(decision is Decision.Block && decision.reason == BlockReason.SLEEP_MODE)
    }

    @Test
    fun `a limit of zero means unlimited`() {
        val decision = BlockEngine.decide(
            pkg = pkg,
            state = state(rule = AppRule(pkg, dailyLimitMinutes = 0, zenPauseSeconds = 10)),
            usedTodayMs = minutes(900),
            nowMinute = 600,
        )
        assertEquals(Decision.Pause(10), decision)
    }
}
