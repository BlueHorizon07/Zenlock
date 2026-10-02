package com.zenlock.data

import kotlinx.serialization.Serializable

/**
 * A single app's rules. Absence of an [AppRule] means the app is unmanaged and always allowed
 * (except while Sleep mode is on, which blocks by default).
 */
@Serializable
data class AppRule(
    val packageName: String,
    /** Foreground minutes allowed per day. 0 = unlimited. */
    val dailyLimitMinutes: Int = 0,
    /** Seconds of breathing/pause before the app can be opened. 0 = no pause. */
    val zenPauseSeconds: Int = 0,
    /** Stays open during Sleep mode. */
    val allowedInSleep: Boolean = false,
    /** Blocked for the rest of today regardless of usage. */
    val blockedToday: Boolean = false,
    /** Once today's limit is spent, refuse to loosen this rule until tomorrow. */
    val lockWhenSpent: Boolean = false,
    /** Start-of-day millis on which the lock closed. Zero means it is open. */
    val lockedOnDay: Long = 0L,
) {
    val isManaged: Boolean
        get() = dailyLimitMinutes > 0 || zenPauseSeconds > 0 || blockedToday

    fun isLockedOn(today: Long): Boolean = lockWhenSpent && lockedOnDay == today

    /**
     * Merges a requested edit into a locked rule, keeping only the parts that make it
     * stricter. The point of the lock is to survive the moment you want it gone, so the
     * refusal lives in the data layer rather than in a disabled button the UI might forget
     * to disable.
     */
    fun tightenedWith(requested: AppRule): AppRule = copy(
        dailyLimitMinutes = if (requested.dailyLimitMinutes in 1..dailyLimitMinutes) {
            requested.dailyLimitMinutes
        } else {
            dailyLimitMinutes
        },
        zenPauseSeconds = maxOf(zenPauseSeconds, requested.zenPauseSeconds),
        blockedToday = blockedToday || requested.blockedToday,
        allowedInSleep = allowedInSleep && requested.allowedInSleep,
        lockWhenSpent = true,
    )
}

@Serializable
data class SleepSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
)

@Serializable
data class FilterSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 21 * 60,
    val endMinute: Int = 7 * 60,
    /** Amber strength, 0..100. */
    val warmth: Int = 45,
    /** Extra darkening beyond the system minimum, 0..80. */
    val dim: Int = 25,
    /** Apply the filter right now, ignoring the schedule. */
    val forcedOn: Boolean = false,
)

@Serializable
data class ZenState(
    /** Master switch. Off = Zenlock enforces nothing. */
    val enabled: Boolean = true,
    val rules: Map<String, AppRule> = emptyMap(),
    val sleep: SleepSchedule = SleepSchedule(),
    val filter: FilterSchedule = FilterSchedule(),
    /** How long an app stays open after the user sits through a Zen pause. */
    val passMinutes: Int = 5,
    /** Default pause length offered when the user first adds an app. */
    val defaultPauseSeconds: Int = 10,
    val onboardingDone: Boolean = false,
    /** Name of a ZenPalette entry; resolved leniently so an unknown value falls back. */
    val palette: String = "Sage",
    /** Name of a ThemeMode entry: System, Light or Dark. */
    val themeMode: String = "System",
    /** Start-of-day millis this state was last normalised for; drives the daily reset. */
    val dayStamp: Long = 0L,
) {
    fun ruleFor(pkg: String): AppRule? = rules[pkg]

    /** True while any rule is holding itself shut for the rest of the day. */
    fun hasLockHeld(today: Long): Boolean = rules.values.any { it.isLockedOn(today) }

    fun lockedCount(today: Long): Int = rules.values.count { it.isLockedOn(today) }

    val managedPackages: List<String>
        get() = rules.values.filter { it.isManaged }.map { it.packageName }
}
