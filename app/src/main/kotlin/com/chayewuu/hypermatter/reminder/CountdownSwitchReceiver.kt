package com.chayewuu.hypermatter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.EventStore
import com.chayewuu.hypermatter.data.ReminderStore

/**
 * CountdownSwitchReceiver fires at (target − [LiveUpdateNotifier.COUNTDOWN_LEAD_MS])
 * for each event in the live-notification window: at that moment the ongoing
 * Live Updates notification re-posts in its final 秒表倒数 form — the switch
 * from the plain「还有 N 天」style to the real-time countdown happens here.
 *
 * Gated by the full selection (enabled / isSelected / liveUpdatesEnabled) at
 * delivery time, mirroring ReminderReceiver's stale-alarm guard.
 */
class CountdownSwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val app = context.applicationContext

        val store = ReminderStore(app)
        if (!store.enabled.value || !store.liveUpdatesEnabled.value) return

        val event = EventStore(app).events.value.firstOrNull { it.id == eventId } ?: return
        if (!store.isSelected(event)) return

        val timing = ReminderReceiver.timingFor(event)
        if (timing.targetMillis - System.currentTimeMillis() > LiveUpdateNotifier.COUNTDOWN_LEAD_MS) {
            // Alarm delivered early (clock drift / inexact fallback): keep the
            // plain style, a later switch alarm re-fires inside the window.
            return
        }

        LiveUpdateNotifier.showCountdown(
            context = app,
            eventId = event.id,
            title = "${event.title} · 倒计时",
            content = DateUtils.describe(event),
            shortCriticalText = timing.hintContent,
            targetTimestamp = timing.targetMillis,
        )
    }

    companion object {
        const val EXTRA_EVENT_ID = "hyperday.extra.REMINDER_EVENT_ID"
    }
}
