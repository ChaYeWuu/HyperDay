package com.chayewuu.hypermatter.reminder

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chayewuu.hypermatter.MainActivity
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.EventStore
import com.chayewuu.hypermatter.data.ReminderStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Fires when a scheduled reminder alarm goes off: posts the event's
 * notification — 小米超级岛 (= HyperOS 焦点通知, with Shizuku XMSF bypass)
 * and/or an Android 16 Live Updates countdown, each gated by its own
 * ReminderStore switch — then re-arms the next occurrence for recurring
 * events.
 *
 * goAsync + coroutine: the Shizuku bypass (bindUserService → notify →
 * restore) can take a few seconds, beyond the synchronous onReceive window.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val app = context.applicationContext
        val event = EventStore(app).events.value.firstOrNull { it.id == eventId } ?: run {
            // Event deleted after scheduling — drop the alarm bookkeeping.
            ReminderScheduler.reschedule(app)
            return
        }

        val pending = goAsync()
        IslandNotifier.scope.launch {
            try {
                postNotifications(app, event)
            } finally {
                runCatching { ReminderScheduler.reschedule(app) }
                pending.finish()
            }
        }
    }

    private fun postNotifications(app: Context, event: com.chayewuu.hypermatter.data.CountdownEvent) {
        ReminderScheduler.ensureChannel(app)

        val title = event.title
        val text = DateUtils.describe(event)
        val zone = ZoneId.systemDefault()

        // Countdown target: start of the target day; if that moment already
        // passed (same-day reminder), count down to the end of the day.
        val target = DateUtils.effectiveDate(event)
        var targetMillis = target.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (targetMillis <= now) {
            targetMillis = target.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1000
        }
        val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
        val hintContent = if (days > 0) "还有 $days 天" else "就是今天"
        val hintTitle = if (days > 0) "" else "已到期"

        val store = ReminderStore(app)

        if (store.islandEnabled.value && FocusNotification.focusProtocolVersion(app) >= 1) {
            // 小米超级岛（= HyperOS 焦点通知，Shizuku bypass 内部自动降级）。
            IslandNotifier.sendEventIsland(
                context = app,
                eventId = event.id,
                title = title,
                content = text,
                targetTimestamp = targetMillis,
                hintContent = hintContent,
                hintTitle = hintTitle,
            )
        } else {
            // Plain reminder notification (island off or no focus-notification support).
            postPlain(app, event, title, text)
        }

        // Android 16 Live Updates：实时倒数到目标时刻，到期自动移除。
        if (store.liveUpdatesEnabled.value) {
            LiveUpdateNotifier.showCountdown(
                context = app,
                eventId = event.id,
                title = "$title · 倒计时",
                content = text,
                shortCriticalText = hintContent,
                targetTimestamp = targetMillis,
            )
        }
    }

    private fun postPlain(
        app: Context,
        event: com.chayewuu.hypermatter.data.CountdownEvent,
        title: String,
        text: String,
    ) {
        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_EVENT_ID, event.id)
        }
        val contentIntent = PendingIntent.getActivity(
            app, event.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(app, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        try {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            manager.notify(event.id.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — nothing to show.
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "hyperday.extra.REMINDER_EVENT_ID"
    }
}
