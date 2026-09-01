package com.chayewuu.hypermatter.data

import android.content.Context
import android.content.SharedPreferences
import com.chayewuu.hypermatter.widget.updateAllWidgets
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Lightweight persistence layer for [CountdownEvent] backed by SharedPreferences.
 *
 * Events are stored as a single JSON array under key "events".
 * The class also exposes a [StateFlow] so Compose can observe changes.
 * Every write also refreshes the home-screen widgets.
 */
class EventStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("hypermatter_events", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _events = MutableStateFlow(loadAll())
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    private fun loadAll(): List<CountdownEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return defaultSeed()
        return runCatching {
            json.decodeFromString(ListSerializer(CountdownEvent.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<CountdownEvent>) {
        val raw = json.encodeToString(ListSerializer(CountdownEvent.serializer()), list)
        prefs.edit().putString(KEY_EVENTS, raw).apply()
        // Keep home-screen widgets in sync with every data change.
        runCatching { updateAllWidgets(appContext) }
    }

    fun add(event: CountdownEvent) {
        val updated = _events.value + event
        persist(updated)
        _events.value = updated
    }

    fun delete(id: String) {
        val updated = _events.value.filterNot { it.id == id }
        persist(updated)
        _events.value = updated
    }

    fun update(event: CountdownEvent) {
        val updated = _events.value.map { if (it.id == event.id) event else it }
        persist(updated)
        _events.value = updated
    }

    fun clearAll() {
        persist(emptyList())
        _events.value = emptyList()
    }

    /**
     * Merge imported events into the store. Events whose id already exists
     * are skipped. Returns the number of newly added events.
     */
    fun importEvents(events: List<CountdownEvent>): Int {
        val existing = _events.value.map { it.id }.toHashSet()
        val fresh = events.filterNot { existing.contains(it.id) }
        if (fresh.isEmpty()) return 0
        val updated = _events.value + fresh
        persist(updated)
        _events.value = updated
        return fresh.size
    }

    /** Seed a couple of sample events the first time the app is opened. */
    private fun defaultSeed(): List<CountdownEvent> {
        val today = DateUtils.today()
        val newYear = LocalDate.of(today.year + 1, 1, 1)
        val birthday = LocalDate.of(today.year, today.monthValue, today.dayOfMonth).let { d ->
            if (d.isBefore(today)) d.plusYears(1) else d
        }
        val seeded = listOf(
            CountdownEvent(
                id = "seed_newyear",
                title = "元旦",
                epochDay = newYear.toEpochDay(),
                note = "新的一年开始了",
            ),
            CountdownEvent(
                id = "seed_birthday",
                title = "生日",
                epochDay = birthday.toEpochDay(),
                note = "祝自己生日快乐",
            ),
        )
        persist(seeded)
        return seeded
    }

    companion object {
        private const val KEY_EVENTS = "events"
    }
}
