package com.zenlock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zenlock.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.zenDataStore: DataStore<Preferences> by preferencesDataStore(name = "zenlock")

/**
 * Single source of truth for everything the user configured. The whole state is small
 * (tens of rules), so it is stored as one JSON blob rather than a database.
 */
class ZenStore private constructor(private val appContext: Context) {

    val state: Flow<ZenState> = appContext.zenDataStore.data.map { prefs ->
        decode(prefs[KEY]).rolledToToday()
    }

    suspend fun current(): ZenState = state.first()

    suspend fun update(transform: (ZenState) -> ZenState) {
        appContext.zenDataStore.edit { prefs ->
            val before = decode(prefs[KEY]).rolledToToday()
            prefs[KEY] = JSON.encodeToString(before.guarded(transform(before)))
        }
    }

    /** Edits one app's rule, creating it if the app was not managed yet. */
    suspend fun updateRule(pkg: String, transform: (AppRule) -> AppRule) = update { state ->
        val today = Time.startOfDay()
        val current = state.rules[pkg] ?: AppRule(pkg)
        val requested = transform(current)

        var next = if (current.isLockedOn(today)) current.tightenedWith(requested) else requested
        // Closing an app for the day is also "finished for today", so it arms the lock too.
        if (next.lockWhenSpent && next.blockedToday && next.lockedOnDay != today) {
            next = next.copy(lockedOnDay = today)
        }

        val rules = state.rules.toMutableMap()
        if (next.isManaged || next.allowedInSleep) rules[pkg] = next else rules.remove(pkg)
        state.copy(rules = rules)
    }

    suspend fun removeRule(pkg: String) = update { state ->
        val today = Time.startOfDay()
        if (state.rules[pkg]?.isLockedOn(today) == true) state
        else state.copy(rules = state.rules - pkg)
    }

    /**
     * Closes the lock on a rule whose limit has just been spent. Separate from [updateRule]
     * because this is the one write that is allowed to make a rule stricter on its own.
     */
    suspend fun stampLock(pkg: String) = update { state ->
        val today = Time.startOfDay()
        val rule = state.rules[pkg] ?: return@update state
        if (!rule.lockWhenSpent || rule.lockedOnDay == today) return@update state
        state.copy(rules = state.rules + (pkg to rule.copy(lockedOnDay = today)))
    }

    private fun decode(raw: String?): ZenState {
        if (raw.isNullOrBlank()) return ZenState()
        return runCatching { JSON.decodeFromString<ZenState>(raw) }.getOrDefault(ZenState())
    }

    /**
     * Clears per-day flags once the calendar day turns over. Applied on every read so a
     * stale stored value can never keep an app blocked into the next morning.
     */
    /**
     * The master switch is the obvious way around a per-app lock, so while any lock is held it
     * cannot be turned off. Revoking accessibility or uninstalling still works — this is a
     * commitment device, not a cage.
     */
    private fun ZenState.guarded(next: ZenState): ZenState {
        val today = Time.startOfDay()
        return if (!next.enabled && hasLockHeld(today)) next.copy(enabled = true) else next
    }

    private fun ZenState.rolledToToday(): ZenState {
        val today = Time.startOfDay()
        if (dayStamp == today) return this
        return copy(
            dayStamp = today,
            rules = rules.mapValues { (_, rule) -> rule.copy(blockedToday = false) },
        )
    }

    companion object {
        private val KEY = stringPreferencesKey("state_json")
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Volatile
        private var instance: ZenStore? = null

        fun get(context: Context): ZenStore =
            instance ?: synchronized(this) {
                instance ?: ZenStore(context.applicationContext).also { instance = it }
            }
    }
}
