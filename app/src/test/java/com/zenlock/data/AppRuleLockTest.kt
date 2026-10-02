package com.zenlock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock's whole value is that it holds at the exact moment you want it gone, so the rule
 * that decides what an edit is allowed to do is a pure function and is pinned down here.
 */
class AppRuleLockTest {

    private val today = 1_700_000_000_000L
    private val yesterday = today - 24 * 60 * 60 * 1000L

    private val locked = AppRule(
        packageName = "com.example.social",
        dailyLimitMinutes = 30,
        zenPauseSeconds = 10,
        lockWhenSpent = true,
        lockedOnDay = today,
    )

    @Test
    fun `a lock stamped today is held`() {
        assertTrue(locked.isLockedOn(today))
    }

    @Test
    fun `a lock stamped yesterday has opened`() {
        assertFalse(locked.isLockedOn(yesterday + 2 * 24 * 60 * 60 * 1000L))
        assertFalse(locked.copy(lockedOnDay = yesterday).isLockedOn(today))
    }

    @Test
    fun `a rule that never opted in is never locked`() {
        assertFalse(locked.copy(lockWhenSpent = false).isLockedOn(today))
    }

    @Test
    fun `the limit can be lowered while locked`() {
        val result = locked.tightenedWith(locked.copy(dailyLimitMinutes = 10))
        assertEquals(10, result.dailyLimitMinutes)
    }

    @Test
    fun `the limit cannot be raised while locked`() {
        val result = locked.tightenedWith(locked.copy(dailyLimitMinutes = 120))
        assertEquals(30, result.dailyLimitMinutes)
    }

    @Test
    fun `the limit cannot be switched off while locked`() {
        val result = locked.tightenedWith(locked.copy(dailyLimitMinutes = 0))
        assertEquals(30, result.dailyLimitMinutes)
    }

    @Test
    fun `the pause can be lengthened but not shortened`() {
        assertEquals(25, locked.tightenedWith(locked.copy(zenPauseSeconds = 25)).zenPauseSeconds)
        assertEquals(10, locked.tightenedWith(locked.copy(zenPauseSeconds = 0)).zenPauseSeconds)
    }

    @Test
    fun `closing for the day sticks and cannot be undone`() {
        val closed = locked.copy(blockedToday = true)
        assertTrue(closed.tightenedWith(closed.copy(blockedToday = false)).blockedToday)
        assertTrue(locked.tightenedWith(locked.copy(blockedToday = true)).blockedToday)
    }

    @Test
    fun `sleep permission can be withdrawn but not granted while locked`() {
        val allowed = locked.copy(allowedInSleep = true)
        assertFalse(allowed.tightenedWith(allowed.copy(allowedInSleep = false)).allowedInSleep)
        assertFalse(locked.tightenedWith(locked.copy(allowedInSleep = true)).allowedInSleep)
    }

    @Test
    fun `the lock cannot switch itself off`() {
        val result = locked.tightenedWith(locked.copy(lockWhenSpent = false))
        assertTrue(result.lockWhenSpent)
    }

    @Test
    fun `a locked rule stays managed so it cannot be dropped`() {
        val result = locked.tightenedWith(
            AppRule(packageName = locked.packageName),
        )
        assertTrue(result.isManaged)
    }

    @Test
    fun `state reports how many rules are holding`() {
        val state = ZenState(
            rules = mapOf(
                locked.packageName to locked,
                "b" to AppRule("b", dailyLimitMinutes = 10),
                "c" to AppRule("c", lockWhenSpent = true, lockedOnDay = today, dailyLimitMinutes = 5),
            ),
        )
        assertEquals(2, state.lockedCount(today))
        assertTrue(state.hasLockHeld(today))
        assertFalse(state.hasLockHeld(yesterday))
    }
}
