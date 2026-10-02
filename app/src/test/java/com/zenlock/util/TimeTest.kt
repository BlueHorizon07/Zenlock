package com.zenlock.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTest {

    @Test
    fun `a daytime window contains its own hours`() {
        assertTrue(Time.isWithinWindow(startMinute = 540, endMinute = 1020, nowMinute = 700))
        assertFalse(Time.isWithinWindow(startMinute = 540, endMinute = 1020, nowMinute = 1030))
    }

    @Test
    fun `the start of a window is inside it and the end is not`() {
        assertTrue(Time.isWithinWindow(540, 1020, 540))
        assertFalse(Time.isWithinWindow(540, 1020, 1020))
    }

    @Test
    fun `a window wrapping midnight covers both sides of it`() {
        // 22:00 to 07:00
        assertTrue(Time.isWithinWindow(1320, 420, 1350))
        assertTrue(Time.isWithinWindow(1320, 420, 120))
        assertFalse(Time.isWithinWindow(1320, 420, 600))
    }

    @Test
    fun `a window with equal ends is always on`() {
        assertTrue(Time.isWithinWindow(600, 600, 0))
        assertTrue(Time.isWithinWindow(600, 600, 1439))
    }

    @Test
    fun `durations read in hours and minutes`() {
        assertEquals("0m", Time.formatDuration(0))
        assertEquals("0m", Time.formatDuration(59_000))
        assertEquals("1m", Time.formatDuration(60_000))
        assertEquals("48m", Time.formatDuration(48 * 60_000L))
        assertEquals("1h 00m", Time.formatDuration(60 * 60_000L))
        assertEquals("2h 05m", Time.formatDuration(125 * 60_000L))
    }

    @Test
    fun `the clock uses twelve hour time with midnight and noon as twelve`() {
        assertEquals("12:00 AM", Time.formatClock(0))
        assertEquals("7:30 AM", Time.formatClock(7 * 60 + 30))
        assertEquals("12:00 PM", Time.formatClock(12 * 60))
        assertEquals("1:05 PM", Time.formatClock(13 * 60 + 5))
        assertEquals("11:59 PM", Time.formatClock(23 * 60 + 59))
    }

    @Test
    fun `minutes convert to milliseconds`() {
        assertEquals(0L, Time.minutesToMillis(0))
        assertEquals(1_800_000L, Time.minutesToMillis(30))
    }
}
