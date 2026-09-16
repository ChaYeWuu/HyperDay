package com.chayewuu.hypermatter.data

import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * One event read out of the system calendar, on its way into the app
 * (设置 → 数据 → 同步系统日历 → 从系统日历导入).
 */
data class SystemCalendarEvent(
    val id: Long,
    val calendarId: Long,
    val calendarName: String,
    val title: String,
    val date: LocalDate,
    val allDay: Boolean,
    /** 0 = one-off, else the CountdownEvent repeat type (1..4). */
    val repeatType: Int,
    /** "calendarId:eventId" — mirrors `CountdownEvent.sourceRef`. */
    val sourceRef: String,
)

/**
 * Reads the user's system calendars via CalendarContract for the one-way
 * import into HyperDay.
 *
 * The app's own 「HyperDay 倒数日」 calendar is skipped: its events were
 * written by [CalendarSyncManager] in the first place, so importing them
 * back would duplicate the very events they mirror.
 */
object SystemCalendarReader {

    /** Every non-deleted, non-cancelled event of every foreign calendar. */
    fun readEvents(context: Context): List<SystemCalendarEvent> {
        val resolver = context.contentResolver
        val ownCalendarIds = runCatching { CalendarSyncManager.ownCalendarIds(context) }
            .getOrDefault(emptySet())
        val calendarNames = runCatching { calendarNames(context) }.getOrDefault(emptyMap())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.STATUS,
        )

        val events = mutableListOf<SystemCalendarEvent>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DELETED}=0",
            null,
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val calendarId = cursor.getLong(1)
                if (calendarId in ownCalendarIds) continue
                if (cursor.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                val start = cursor.getLong(3)
                if (start <= 0L) continue
                val allDay = cursor.getInt(4) == 1
                val rrule = cursor.getString(5)
                events += SystemCalendarEvent(
                    id = id,
                    calendarId = calendarId,
                    calendarName = calendarNames[calendarId] ?: "系统日历",
                    title = cursor.getString(2)?.trim().orEmpty().ifBlank { "未命名日程" },
                    date = dateOf(start, allDay),
                    allDay = allDay,
                    repeatType = repeatTypeOf(rrule),
                    sourceRef = "$calendarId:$id",
                )
            }
        }
        return events
    }

    /**
     * All-day DTSTART is UTC midnight — reading it back in the local zone
     * would shift the date by a day west of UTC. Timed events, in contrast,
     * belong to the local wall clock.
     */
    private fun dateOf(dtstartMillis: Long, allDay: Boolean): LocalDate =
        if (allDay) {
            Instant.ofEpochMilli(dtstartMillis).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            Instant.ofEpochMilli(dtstartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    private fun calendarNames(context: Context): Map<Long, String> {
        val names = mutableMapOf<Long, String>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                names[cursor.getLong(0)] = cursor.getString(1)?.trim().orEmpty()
            }
        }
        return names
    }

    /** Maps an RRULE onto a CountdownEvent repeat type (unmappable = one-off). */
    private fun repeatTypeOf(rrule: String?): Int {
        if (rrule.isNullOrBlank()) return 0
        return when {
            rrule.contains("FREQ=DAILY") -> 1
            rrule.contains("FREQ=WEEKLY") -> 2
            rrule.contains("FREQ=MONTHLY") -> 3
            rrule.contains("FREQ=YEARLY") -> 4
            else -> 0
        }
    }

    /**
     * Turns picked system events into app events. Recurring system events
     * keep their rule (so the countdown rolls over like the system one);
     * everything else becomes a one-off countdown on its date.
     */
    fun toCountdownEvents(picked: List<SystemCalendarEvent>): List<CountdownEvent> =
        picked.map { event ->
            CountdownEvent(
                id = UUID.randomUUID().toString(),
                title = event.title,
                epochDay = event.date.toEpochDay(),
                note = "导入自「${event.calendarName}」",
                repeatType = event.repeatType.takeIf { it != 0 },
                repeatWeekday = if (event.repeatType == 2) event.date.dayOfWeek.value else null,
                repeatMonthDay = if (event.repeatType == 3 || event.repeatType == 4) {
                    event.date.dayOfMonth
                } else {
                    null
                },
                repeatYearMonth = if (event.repeatType == 4) event.date.monthValue else null,
                sourceRef = event.sourceRef,
            )
        }
}
