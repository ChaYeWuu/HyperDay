package com.chayewuu.hypermatter.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * One-way sync of countdown events into the system calendar
 * (设置 → 数据 → 同步到系统日历).
 *
 * The manager owns a dedicated local calendar (ACCOUNT_TYPE_LOCAL, never
 * synced to any cloud account) and does a full rebuild on every sync:
 * delete all previously inserted events, then insert the current event
 * list. This keeps the calendar an exact mirror of the app data with no
 * per-event diffing state.
 *
 * Recurring events map to RRULEs:
 *  - daily  → FREQ=DAILY
 *  - weekly → FREQ=WEEKLY;BYDAY=…
 *  - monthly→ FREQ=MONTHLY;BYMONTHDAY=n
 *  - yearly → FREQ=YEARLY;BYMONTH=m;BYMONTHDAY=d
 *  - lunar-yearly cannot be expressed as an RRULE → inserted as a one-off
 *    at the next lunar occurrence ([DateUtils.effectiveDate]).
 */
object CalendarSyncManager {

    const val ACCOUNT_NAME = "HyperDay"
    private const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    private const val PREFS = "calendar_sync"
    private const val KEY_LAST_TIME = "last_sync_time"
    private const val KEY_LAST_COUNT = "last_sync_count"

    /** Selected event ids; key absent = selection never customized (all). */
    private const val KEY_SELECTED_IDS = "selected_ids"

    /** Calendar accent color (HyperDay blue). */
    private const val CALENDAR_COLOR = 0xFF5B8DEF.toInt()

    // ------------------------------------------------------------------
    // URI helpers
    // ------------------------------------------------------------------

    /**
     * Calendar provider inserts/deletes of calendars must be issued as a
     * "sync adapter" (CALLER_IS_SYNCADAPTER) with the account bound into
     * the URI, otherwise the provider rejects them with
     * "Calendars must be created with sync adapter".
     */
    private fun asSyncAdapter(
        uri: Uri,
        accountName: String = ACCOUNT_NAME,
        accountType: String = ACCOUNT_TYPE,
    ): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
        .build()

    // ------------------------------------------------------------------
    // Calendar bookkeeping
    // ------------------------------------------------------------------

    /** Finds or creates our local calendar, returning its id. */
    private fun ensureCalendarId(resolver: ContentResolver): Long {
        // SYNC_EVENTS/VISIBLE are read too: MIUI's calendar app hides
        // local calendars with sync_events=0 (the wallet calendar beside
        // ours keeps it on), and early builds created ours with 0 — flip
        // pre-existing rows back to 1 so the calendar actually shows up.
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.VISIBLE,
            ),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                if (cursor.getInt(1) == 0 || cursor.getInt(2) == 0) {
                    resolver.update(
                        asSyncAdapter(CalendarContract.Calendars.CONTENT_URI),
                        ContentValues().apply {
                            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                            put(CalendarContract.Calendars.VISIBLE, 1)
                        },
                        "${CalendarContract.Calendars._ID}=?",
                        arrayOf(id.toString()),
                    )
                }
                return id
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "HyperDay 倒数日")
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            // Must be 1: MIUI's calendar app won't list/display a local
            // calendar whose sync flag is off. ACCOUNT_TYPE_LOCAL has no
            // sync adapter, so nothing actually syncs anywhere.
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
        }
        val uri = resolver.insert(
            asSyncAdapter(CalendarContract.Calendars.CONTENT_URI),
            values,
        ) ?: throw IllegalStateException("日历创建失败")
        return uri.lastPathSegment?.toLongOrNull()
            ?: throw IllegalStateException("日历创建失败")
    }

    /** Hard-deletes every event in our calendar (sync-adapter delete). */
    private fun clearEvents(resolver: ContentResolver, calendarId: Long) {
        resolver.delete(
            asSyncAdapter(CalendarContract.Events.CONTENT_URI),
            "${CalendarContract.Events.CALENDAR_ID}=?",
            arrayOf(calendarId.toString()),
        )
    }

    // ------------------------------------------------------------------
    // Event mapping
    // ------------------------------------------------------------------

    /** RRULE for the event's repeat rule, or null for one-off / lunar. */
    private fun rruleOf(event: CountdownEvent): String? {
        val anchor = LocalDate.ofEpochDay(event.epochDay)
        return when (DateUtils.effectiveRepeatType(event)) {
            1 -> "FREQ=DAILY"
            2 -> {
                val weekday = event.repeatWeekday ?: anchor.dayOfWeek.value
                val byDay = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
                    .getOrElse(weekday - 1) { "MO" }
                "FREQ=WEEKLY;BYDAY=$byDay"
            }
            3 -> {
                val day = event.repeatMonthDay ?: anchor.dayOfMonth
                "FREQ=MONTHLY;BYMONTHDAY=$day"
            }
            4 -> {
                val month = event.repeatYearMonth ?: anchor.monthValue
                val day = event.repeatMonthDay ?: anchor.dayOfMonth
                "FREQ=YEARLY;BYMONTH=$month;BYMONTHDAY=$day"
            }
            else -> null // one-off or lunar-yearly
        }
    }

    /**
     * The date the calendar event starts at:
     *  - one-off / past events: the stored date itself;
     *  - RRULE events: the LAST occurrence at/before today;
     *  - lunar-yearly (no RRULE, a one-off): the next occurrence.
     *
     *  The stored epochDay of a recurring event is only the ADD date (the
     *  add form hides the date picker once a repeat type is chosen), so
     *  the real recurrence lives in the repeat* fields. Seeding DTSTART
     *  at the next occurrence made this year's already-passed dates
     *  (e.g. a Jan 6 birthday checked in September) invisible in the
     *  calendar app; seeding it at the add date created a stray instance
     *  on the wrong day. The last occurrence ≤ today keeps the whole
     *  current period visible plus all future ones, without fabricating
     *  history the app never knew about.
     */
    private fun startDateOf(event: CountdownEvent): LocalDate {
        if (!DateUtils.isRecurring(event)) {
            return LocalDate.ofEpochDay(event.epochDay)
        }
        val next = DateUtils.effectiveDate(event)
        return when (DateUtils.effectiveRepeatType(event)) {
            1 -> next.minusDays(1)     // daily
            2 -> next.minusDays(7)     // weekly
            3 -> next.minusMonths(1)   // monthly (day-of-month clamps itself)
            4 -> next.minusYears(1)    // yearly (Feb 29 clamps to Feb 28)
            else -> next               // lunar-yearly one-off: stays upcoming
        }
    }

    private fun allDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun insertEvent(
        resolver: ContentResolver,
        calendarId: Long,
        event: CountdownEvent,
    ) {
        val start = allDayMillis(startDateOf(event))
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, "由 HyperDay 同步")
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, allDayMillis(startDateOf(event).plusDays(1)))
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            // Countdown entries shouldn't block the user's availability.
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            rruleOf(event)?.let { put(CalendarContract.Events.RRULE, it) }
        }
        resolver.insert(asSyncAdapter(CalendarContract.Events.CONTENT_URI), values)
    }

    // ------------------------------------------------------------------
    // Public API (call on a worker thread)
    // ------------------------------------------------------------------

    /**
     * Full-rebuild sync: clears our calendar, then inserts every event.
     * @return inserted event count on success.
     */
    fun syncAll(context: Context, events: List<CountdownEvent>): Result<Int> = runCatching {
        val resolver = context.contentResolver
        val calendarId = ensureCalendarId(resolver)
        clearEvents(resolver, calendarId)
        events.forEach { insertEvent(resolver, calendarId, it) }
        rememberSync(context, events.size)
        events.size
    }

    /** Removes every synced event and our calendar itself. */
    fun removeAll(context: Context): Result<Unit> = runCatching {
        val resolver = context.contentResolver
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                clearEvents(resolver, id)
                resolver.delete(
                    asSyncAdapter(CalendarContract.Calendars.CONTENT_URI),
                    "${CalendarContract.Calendars._ID}=?",
                    arrayOf(id.toString()),
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    /** (timestampMillis, eventCount) of the last successful sync, or null. */
    fun getLastSyncInfo(context: Context): Pair<Long, Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_LAST_TIME, 0L)
        if (time <= 0L) return null
        return time to prefs.getInt(KEY_LAST_COUNT, 0)
    }

    // ------------------------------------------------------------------
    // Free event selection (二级页勾选集)
    // ------------------------------------------------------------------

    /**
     * The user's event selection for syncing, or null when the selection
     * was never customized (null = "all events" default).
     */
    fun getSelectedIds(context: Context): Set<String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SELECTED_IDS)) return null
        return prefs.getStringSet(KEY_SELECTED_IDS, emptySet()) ?: emptySet()
    }

    fun setSelectedIds(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SELECTED_IDS, ids)
            .apply()
    }

    private fun rememberSync(context: Context, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .putInt(KEY_LAST_COUNT, count)
            .apply()
    }
}
