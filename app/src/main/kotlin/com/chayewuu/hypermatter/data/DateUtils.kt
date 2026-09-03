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
     * Whether the event should be treated as "past" (days since).
     * Recurring events never count as past — they always target the next
     * occurrence.
     */
    fun isPastEvent(event: CountdownEvent): Boolean {
        if (isRecurring(event)) return false
        return event.isPast ?: (event.epochDay < todayEpochDay())
    }

    /** Whether this event repeats (daily/weekly/monthly/yearly/lunar-yearly). */
    fun isRecurring(event: CountdownEvent): Boolean = effectiveRepeatType(event) != 0

    /**
     * Effective repeat type: an explicit setting (1..5) always wins; events in
     * the built-in 纪念日 category default to yearly (4) — anniversaries roll
     * to the next year after the day passes instead of falling into "past".
     */
    private fun effectiveRepeatType(event: CountdownEvent): Int {
        val explicit = event.repeatType ?: 0
        if (explicit != 0) return explicit
        return if (event.category == CategoryStore.ID_ANNIVERSARY) 4 else 0
    }

    /**
     * The effective target date: for one-off events the stored date; for
     * recurring events the next occurrence at/after today.
     */
    fun effectiveDate(event: CountdownEvent): LocalDate {
        if (!isRecurring(event)) return LocalDate.ofEpochDay(event.epochDay)
        val today = today()
        val anchor = LocalDate.ofEpochDay(event.epochDay)
        return when (effectiveRepeatType(event)) {
            1 -> today // daily
            2 -> { // weekly: chosen weekday (or the anchor's, legacy)
                val weekday = event.repeatWeekday ?: anchor.dayOfWeek.value
                val delta = (weekday - today.dayOfWeek.value + 7) % 7
                today.plusDays(delta.toLong())
            }
            3 -> { // monthly: chosen day-of-month (clamped)
                val targetDay = event.repeatMonthDay ?: anchor.dayOfMonth
                val candidate = today.withDayOfMonth(
                    targetDay.coerceIn(1, today.lengthOfMonth()),
                )
                if (candidate.isBefore(today)) {
                    val nextMonth = today.plusMonths(1)
                    nextMonth.withDayOfMonth(
                        targetDay.coerceIn(1, nextMonth.lengthOfMonth()),
                    )
                } else {
                    candidate
                }
            }
            4 -> { // yearly: chosen month/day (Feb 29 -> Feb 28 fallback)
                val targetMonth = event.repeatYearMonth ?: anchor.monthValue
                val targetDay = event.repeatMonthDay ?: anchor.dayOfMonth
                val candidate = safeDateOf(today.year, targetMonth, targetDay)
                if (candidate.isBefore(today)) {
                    safeDateOf(today.year + 1, targetMonth, targetDay)
                } else {
                    candidate
                }
            }
            5 -> { // yearly lunar anniversary
                val lm = event.lunarMonth ?: anchor.let { LunarCalendar.solarToLunar(it).month }
                val ld = event.lunarDay ?: anchor.let { LunarCalendar.solarToLunar(it).day }
                LunarCalendar.nextLunarAnniversary(lm, ld, today)
            }
            else -> anchor
        }
    }

    /** Feb 29 tolerant date construction. */
    private fun safeDateOf(year: Int, month: Int, day: Int): LocalDate {
        val validDay = day.coerceIn(1, LocalDate.of(year, month, 1).lengthOfMonth())
        return LocalDate.of(year, month, validDay)
    }

    /**
     * Human-readable repeat label, e.g. "每周六" / "每年 农历八月十五".
     */
    fun repeatLabel(event: CountdownEvent): String {
        return when (effectiveRepeatType(event)) {
            1 -> {
                val h = event.timeHour
                val m = event.timeMinute
                if (h != null && m != null)
                    "每天 %02d:%02d".format(h, m)
                else
                    "每天"
            }
            2 -> "每周${weekdayName(event.repeatWeekday ?: LocalDate.ofEpochDay(event.epochDay).dayOfWeek.value)}"
            3 -> "每月${event.repeatMonthDay ?: LocalDate.ofEpochDay(event.epochDay).dayOfMonth}日"
            4 -> {
                val anchor = LocalDate.ofEpochDay(event.epochDay)
                val m = event.repeatYearMonth ?: anchor.monthValue
                val d = event.repeatMonthDay ?: anchor.dayOfMonth
                "每年${m}月${d}日"
            }
            5 -> {
                val anchor = LocalDate.ofEpochDay(event.epochDay)
                val lm = event.lunarMonth ?: LunarCalendar.solarToLunar(anchor).month
                val ld = event.lunarDay ?: LunarCalendar.solarToLunar(anchor).day
                val lmStr = if (lm == 12) "腊月" else if (lm == 11) "冬月" else "${lunarMonthName(lm)}月"
                "每年农历$lmStr${lunarDayName(ld)}"
            }
            else -> ""
        }
    }

    /** Weekday name for a java.time DayOfWeek value (1=周一..7=周日). */
    fun weekdayName(weekday: Int): String {
        val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return names[(weekday - 1).coerceIn(0, 6)]
    }

    fun lunarMonthName(m: Int): String =
        arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")[m - 1]

    fun lunarDayName(d: Int): String {
        val prefixes = arrayOf("初", "十", "廿", "三")
        val digits = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        return when (d) {
            10 -> "初十"
            20 -> "二十"
            30 -> "三十"
            else -> {
                val prefix = prefixes[d / 10]
                val digit = digits[(d % 10) - 1]
                "$prefix$digit"
            }
        }
    }

    /**
     * Day difference for display: recurring events always report the
     * distance to their next occurrence.
     */
    fun dayDifference(event: CountdownEvent): Long {
        if (isRecurring(event)) {
            return effectiveDate(event).toEpochDay() - todayEpochDay()
        }
        return event.epochDay - todayEpochDay()
    }

    /**
     * Effective epoch day for sorting/display (recurring -> next occurrence).
     */
    fun effectiveEpochDay(event: CountdownEvent): Long {
        if (isRecurring(event)) return effectiveDate(event).toEpochDay()
        return event.epochDay
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
     * Year/month/day span between today and the event's effective date,
     * e.g. "3年2月15天" (leading zero parts omitted, "0天" collapses to
     * the non-empty parts only). Used by the tap-to-convert day number.
     */
    fun periodSpan(event: CountdownEvent): String {
        val today = today()
        val target = LocalDate.ofEpochDay(effectiveEpochDay(event))
        val start = if (target.isBefore(today)) target else today
        val end = if (target.isBefore(today)) today else target
        val period = java.time.Period.between(start, end)
        val parts = mutableListOf<String>()
        if (period.years > 0) parts.add("${period.years}年")
        if (period.months > 0) parts.add("${period.months}月")
        if (period.days > 0 || parts.isEmpty()) parts.add("${period.days}天")
        return parts.joinToString("")
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
