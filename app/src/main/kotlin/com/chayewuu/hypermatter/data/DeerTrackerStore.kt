package com.chayewuu.hypermatter.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * 🦌🦌记录器 persistence (小工具 → 🦌🦌记录器).
 *
 * One status per epochDay, stored as a JSON map in SharedPreferences
 * (EventStore/SettingsStore pattern):
 *
 *  * [STATUS_NONE] — no record for that day (also = absent key)
 *  * [STATUS_HIT]  — recorded (打了)
 *  * [STATUS_KEPT] — deliberately kept (没打)
 *
 * The calendar cycles a day through NONE → HIT → KEPT → NONE on tap;
 * future days cannot be tapped. Stats (streaks / totals) are pure
 * functions over the map so the UI can recompute after every write.
 */
object DeerTrackerStore {

    const val STATUS_NONE = 0
    const val STATUS_HIT = 1
    const val STATUS_KEPT = 2

    private const val PREFS = "deer_tracker"
    private const val KEY_RECORDS = "records"

    fun getRecords(context: Context): Map<Long, Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val day = key.toLongOrNull() ?: return@forEach
                    put(day, json.optInt(key, STATUS_NONE))
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** Writes one day's status (0 removes the record entirely). */
    fun setRecord(context: Context, epochDay: Long, status: Int) {
        val records = getRecords(context).toMutableMap()
        if (status == STATUS_NONE) records.remove(epochDay) else records[epochDay] = status
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDS, JSONObject().apply {
                records.forEach { (day, s) -> put(day.toString(), s) }
            }.toString())
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_RECORDS).apply()
    }

    // ------------------------------------------------------------------
    // Stats (pure functions so the UI recomputes from live state)
    // ------------------------------------------------------------------

    /**
     * Current streak of consecutive HIT days ending today (or yesterday —
     * today still unrecorded does not break a running streak, but a KEPT
     * day or a fully unrecorded gap does).
     */
    fun currentStreak(records: Map<Long, Int>, today: LocalDate): Int {
        var day = today
        if (records[day.toEpochDay()] != STATUS_HIT) day = day.minusDays(1)
        var streak = 0
        while (records[day.toEpochDay()] == STATUS_HIT) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Longest run of consecutive HIT days ever recorded. */
    fun bestStreak(records: Map<Long, Int>): Int {
        val hitDays = records.filterValues { it == STATUS_HIT }.keys.sorted()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (day in hitDays) {
            run = if (prev != null && day == prev!! + 1) run + 1 else 1
            prev = day
            if (run > best) best = run
        }
        return best
    }

    /** HIT days within [month]. */
    fun monthHits(records: Map<Long, Int>, month: YearMonth): Int {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        return records.count { (day, status) ->
            status == STATUS_HIT && day in first..last
        }
    }

    /** Total HIT days ever recorded. */
    fun totalHits(records: Map<Long, Int>): Int =
        records.count { it.value == STATUS_HIT }
}
