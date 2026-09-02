package com.chayewuu.hypermatter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.EventStore
import com.chayewuu.hypermatter.data.ReminderStore
import java.time.temporal.ChronoUnit

/**
 * Daily refresh (09:00:30, armed by [ReminderScheduler]) keeping countdown
 * notifications in sync as days roll over — the "real-time" half of the
 * reminder chain (chronometer / island timerInfo tick by themselves; this
 * receiver refreshes the day-count texts and the island state):
 *
 * - days 1..advance-1 (reminder already fired, target upcoming): silently
 *   re-post the Live Updates notification so「还有 N 天」stays accurate.
 * - day 0 (arrival morning): re-post the island with「就是今天」— the
 *   `reopen` focus param lets the same notification id pop the island again
 *   for the state transition (NexioSchedule pattern) — and refresh the Live
 *   Updates notification.
 * - reminder-day events (days == advance) are skipped: their own 09:00
 *   alarm handles them, avoiding double posts.
 *
 * Ends with a full [ReminderScheduler.reschedule], which re-arms this
 * receiver for tomorrow and late-fires any missed 09:00 reminder.
 */
class LiveUpdateRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = ReminderStore(app)
        if (!store.enabled.value) return

        val advance = store.advanceDays.value
        val today = DateUtils.today()
        val events = EventStore(app).events.value

        events.filter { store.isSelected(it) }.forEach { event ->
            val target = DateUtils.effectiveDate(event)
            if (target.isBefore(today)) return@forEach
            val days = ChronoUnit.DAYS.between(today, target)
            if (days >= advance) return@forEach // reminder-day or later: own alarm handles it

            if (days == 0L) {
                // Arrival morning: re-pop island + refresh live notification.
                // plainFallback=false — never invent an extra plain reminder.
                ReminderReceiver.postEventNotifications(
                    app, event, markFiredDay = false, plainFallback = false,
                )
            } else if (store.liveUpdatesEnabled.value) {
                // Silent same-id re-post: refresh「还有 N 天」texts only.
                val timing = ReminderReceiver.timingFor(event)
                LiveUpdateNotifier.showCountdown(
                    context = app,
                    eventId = event.id,
                    title = "${event.title} · 倒计时",
                    content = DateUtils.describe(event),
                    shortCriticalText = timing.hintContent,
                    targetTimestamp = timing.targetMillis,
                )
            }
        }

        runCatching { ReminderScheduler.reschedule(app) }
    }
}
