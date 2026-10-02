package com.zenlock.data

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived "you may open this now" grants, issued when the user sits through a Zen pause.
 *
 * Deliberately in-memory only: the accessibility service and the block screen share a process,
 * and if that process dies the safe default is to ask again rather than stay unlocked.
 * Uses [SystemClock.elapsedRealtime] so a clock change cannot extend a pass.
 */
object ZenPasses {

    private val expiries = ConcurrentHashMap<String, Long>()

    fun grant(packageName: String, minutes: Int) {
        expiries[packageName] = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    fun isActive(packageName: String): Boolean {
        val expiry = expiries[packageName] ?: return false
        if (SystemClock.elapsedRealtime() >= expiry) {
            expiries.remove(packageName)
            return false
        }
        return true
    }

    fun revoke(packageName: String) {
        expiries.remove(packageName)
    }

    fun clear() = expiries.clear()
}
