package com.chayewuu.hypermatter.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure date arithmetic for [CountdownEvent].
 * All calculations use [LocalDate] and whole-day precision (no time component).
 */
object DateUtils {

    fun today(): LocalDate = LocalDate.now()

    fun todayEpochDay(): Long = today().toEpochDay()

    /**
     * Returns the absolute day difference between [event] and today.
     * Positive means the event is in the future (days until);
     * negative means it is in the past (days since).
     */
    fun dayDifference(event: CountdownEvent): Long {
        return event.epochDay - todayEpochDay()
    }

    /**
     * Whether the event should be treated as "past" (days since).
     * If [CountdownEvent.isPast] is explicitly set, use that;
     * otherwise the date itself decides.
     */
    fun isPastEvent(event: CountdownEvent): Boolean {
        return event.isPast ?: (event.epochDay < todayEpochDay())
    }

    /**
     * Human-readable summary such as "还有 42 天" or "已过去 30 天".
     */
    fun describe(event: CountdownEvent): String {
        val diff = dayDifference(event)
        val past = isPastEvent(event)
        return when {
            diff == 0L -> "就是今天"
            past -> "已过去 ${-diff} 天"
            else -> "还有 $diff 天"
        }
    }

    /**
     * Short label used on the home card — just the number.
     */
    fun dayNumber(event: CountdownEvent): Long {
        return kotlin.math.abs(dayDifference(event))
    }

    /**
     * Number of years between the event and today (for anniversary display).
     */
    fun yearSpan(event: CountdownEvent): Long {
        return ChronoUnit.YEARS.between(
            LocalDate.ofEpochDay(event.epochDay),
            today(),
        )
    }

    /**
     * Format the event date as "2025年6月15日".
     */
    fun formatDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }

    /**
     * Format the event date as "2025-06-15".
     */
 fun formatDateShort(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.year}-${date.monthValue.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
    }

    /**
     * Weekday label such as "周六".
     */
    fun weekdayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return names[date.dayOfWeek.value - 1]
    }
}
