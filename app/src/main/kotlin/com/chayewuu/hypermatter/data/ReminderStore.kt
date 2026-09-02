package com.chayewuu.hypermatter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reminder settings, SharedPreferences-backed (mirrors SettingsStore).
 *
 * Selection model: an event is reminded when
 *   event.id ∈ eventIds  OR  event.category ∈ categoryIds
 * or, when BOTH sets are empty, every event is reminded (提醒全部).
 *
 * advanceDays: 0 = 提醒当天 09:00, N>0 = N days earlier at 09:00.
 */
class ReminderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hypermatter_reminders", Context.MODE_PRIVATE)

    val enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val advanceDays = MutableStateFlow(prefs.getInt(KEY_ADVANCE, 1))
    val categoryIds = MutableStateFlow(prefs.getStringSet(KEY_CATEGORIES, emptySet()) ?: emptySet())
    val eventIds = MutableStateFlow(prefs.getStringSet(KEY_EVENTS, emptySet()) ?: emptySet())

    /** 小米超级岛（= HyperOS 焦点通知）开关，默认开。 */
    val islandEnabled = MutableStateFlow(prefs.getBoolean(KEY_ISLAND, true))

    /** Android 16 Live Updates（持续通知）开关，默认开。 */
    val liveUpdatesEnabled = MutableStateFlow(prefs.getBoolean(KEY_LIVE, true))

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        enabled.value = value
    }

    fun setAdvanceDays(days: Int) {
        prefs.edit().putInt(KEY_ADVANCE, days).apply()
        advanceDays.value = days
    }

    fun setCategoryIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_CATEGORIES, ids).apply()
        categoryIds.value = ids
    }

    fun setEventIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_EVENTS, ids).apply()
        eventIds.value = ids
    }

    fun setIslandEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ISLAND, value).apply()
        islandEnabled.value = value
    }

    fun setLiveUpdatesEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE, value).apply()
        liveUpdatesEnabled.value = value
    }

    /**
     * Whether [event] falls inside the current reminder selection.
     * Empty selection (no categories, no events) means "remind all".
     */
    fun isSelected(event: CountdownEvent): Boolean {
        val cats = categoryIds.value
        val evs = eventIds.value
        if (cats.isEmpty() && evs.isEmpty()) return true
        return event.id in evs || (event.category != null && event.category in cats)
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ADVANCE = "advance_days"
        private const val KEY_CATEGORIES = "category_ids"
        private const val KEY_EVENTS = "event_ids"
        private const val KEY_ISLAND = "island_enabled"
        private const val KEY_LIVE = "live_updates_enabled"
    }
}
