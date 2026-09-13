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
 *  * [STATUS_NONE] — no record for that day (also = absent key) = 没打
 *  * [STATUS_HIT]  — recorded (打了)
 *  * [STATUS_KEPT] — legacy "deliberately kept" state; treated exactly
 *    like NONE by stats and rendered as plain 没打 by the UI
 *
 * The calendar toggles a day between HIT and unrecorded on long-press
 * (no tap cycling); future days cannot be marked. Stats (quit days /
 * totals) are pure functions over the map so the UI can recompute
 * after every write.
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
     * Days since the most recent relapse (HIT): 0 means the relapse was
     * today, null means no relapse ever recorded (nothing to count from).
     */
    fun daysQuit(records: Map<Long, Int>, today: LocalDate): Int? {
        val todayDay = today.toEpochDay()
        val lastHit = records.filterValues { it == STATUS_HIT }
            .keys.filter { it <= todayDay }.maxOrNull() ?: return null
        return (todayDay - lastHit).toInt()
    }

    /**
     * Longest abstain run in days: the biggest gap between consecutive
     * relapses, with the ongoing run (last relapse → today) counted too.
     */
    fun bestQuit(records: Map<Long, Int>, today: LocalDate): Int {
        val days = records.filterValues { it == STATUS_HIT }.keys.sorted()
        if (days.isEmpty()) return 0
        var best = 0
        for (i in 1 until days.size) {
            best = maxOf(best, (days[i] - days[i - 1]).toInt())
        }
        best = maxOf(best, (today.toEpochDay() - days.last()).toInt())
        return best
    }

    /** Relapses (HIT days) within [month]. */
    fun monthHits(records: Map<Long, Int>, month: YearMonth): Int {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        return records.count { (day, status) ->
            status == STATUS_HIT && day in first..last
        }
    }

    /** Total relapses (HIT days) ever recorded. */
    fun totalHits(records: Map<Long, Int>): Int =
        records.count { it.value == STATUS_HIT }
}
