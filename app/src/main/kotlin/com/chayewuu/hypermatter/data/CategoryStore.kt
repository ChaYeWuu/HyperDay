package com.chayewuu.hypermatter.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A user-visible category for events (纪念日 / 生活 / 工作 / custom).
 * Built-in categories have fixed ids and cannot be deleted (rename allowed);
 * custom ones get a UUID id.
 */
@Serializable
data class EventCategory(
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
)

/**
 * Category registry. The persisted JSON list stores custom categories plus
 * renamed built-ins (a persisted entry with a built-in id overrides the
 * constant's display name). Built-in constants are always present.
 */
class CategoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hypermatter_categories", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _categories = MutableStateFlow(loadAll())
    val categories: StateFlow<List<EventCategory>> = _categories.asStateFlow()

    /** All persisted entries (custom + built-in rename overrides). */
    private fun persisted(): List<EventCategory> = runCatching {
        prefs.getString(KEY_PERSISTED, null)?.let {
            json.decodeFromString(ListSerializer(EventCategory.serializer()), it)
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun persist(list: List<EventCategory>) {
        val raw = json.encodeToString(ListSerializer(EventCategory.serializer()), list)
        prefs.edit().putString(KEY_PERSISTED, raw).apply()
    }

    private fun loadAll(): List<EventCategory> {
        val saved = persisted()
        return BUILT_INS.map { b -> saved.firstOrNull { it.id == b.id } ?: b } +
            saved.filterNot { s -> BUILT_INS.any { it.id == s.id } }
    }

    private fun publish(list: List<EventCategory>) {
        persist(list)
        _categories.value = loadAll()
    }

    /** Look up a category by id across built-in + custom entries. */
    fun byId(id: String?): EventCategory? =
        id?.let { target -> _categories.value.firstOrNull { it.id == target } }

    /** Add a custom category. Returns the created entry. */
    fun add(name: String): EventCategory {
        val entry = EventCategory(id = UUID.randomUUID().toString(), name = name.trim())
        publish(persisted() + entry)
        return entry
    }

    /** Rename any category (built-in or custom). */
    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val saved = persisted().toMutableList()
        val builtIn = id in BUILT_IDS
        val index = saved.indexOfFirst { it.id == id }
        val updated = EventCategory(id = id, name = trimmed, builtIn = builtIn)
        if (index >= 0) saved[index] = updated else saved.add(updated)
        publish(saved)
    }

    /** Delete a custom category (built-in ids are ignored). Events keep
     *  their category id — unknown ids simply render as uncategorized. */
    fun delete(id: String) {
        if (id in BUILT_IDS) return
        publish(persisted().filterNot { it.id == id })
    }

    companion object {
        private const val KEY_PERSISTED = "persisted"

        /** Fixed ids for the three built-in categories. */
        const val ID_ANNIVERSARY = "anniversary"
        const val ID_LIFE = "life"
        const val ID_WORK = "work"

        val BUILT_INS = listOf(
            EventCategory(ID_ANNIVERSARY, "纪念日", builtIn = true),
            EventCategory(ID_LIFE, "生活", builtIn = true),
            EventCategory(ID_WORK, "工作", builtIn = true),
        )

        private val BUILT_IDS = BUILT_INS.map { it.id }.toSet()
    }
}
