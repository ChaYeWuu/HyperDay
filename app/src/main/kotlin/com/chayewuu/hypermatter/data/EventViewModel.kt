package com.chayewuu.hypermatter.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class EventViewModel(
    private val store: EventStore,
) : ViewModel() {

    val events: StateFlow<List<CountdownEvent>> = store.events

    /** Events whose target date is today or in the future. */
    val upcoming: List<CountdownEvent>
        get() = events.value
            .filter { !DateUtils.isPastEvent(it) }
            .sortedBy { it.epochDay }

    /** Events whose target date is in the past. */
    val past: List<CountdownEvent>
        get() = events.value
            .filter { DateUtils.isPastEvent(it) }
            .sortedByDescending { it.epochDay }

    fun addEvent(title: String, epochDay: Long, note: String?) {
        store.add(
            CountdownEvent(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                epochDay = epochDay,
                note = note?.trim()?.ifBlank { null },
            )
        )
    }

    fun deleteEvent(id: String) {
        store.delete(id)
    }

    fun updateEvent(event: CountdownEvent) {
        store.update(event)
    }

    fun clearAll() {
        store.clearAll()
    }

    fun importEvents(events: List<CountdownEvent>): Int = store.importEvents(events)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                throw IllegalStateException("Use EventViewModel(store) constructor directly")
            }
        }
    }
}
