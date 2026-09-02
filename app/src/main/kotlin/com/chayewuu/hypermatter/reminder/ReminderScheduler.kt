package com.chayewuu.hypermatter.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.EventStore
import com.chayewuu.hypermatter.data.ReminderStore
import java.time.ZoneId

/**
 * Schedules one exact alarm per selected event via [AlarmManager].
 *
 * The reminder fires at 09:00 on (effective target date − advanceDays).
 * Recurring events always target their next occurrence, so after firing
 * the receiver re-invokes [reschedule] and the alarm rolls to the next
 * occurrence automatically.
 *
 * Alarm cancel bookkeeping: scheduled event ids are kept in prefs so a full
 * re-schedule can cleanly cancel stale PendingIntents even after reboot.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "reminders"

    /** Morning hour the reminder notification fires at. */
    private const val REMINDER_HOUR = 9

    private const val PREFS = "hypermatter_reminder_alarms"
    private const val KEY_SCHEDULED = "scheduled_event_ids"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "日程提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "倒数日到期的提醒通知"
            }
        )
    }

    /** Cancel every alarm recorded in the bookkeeping prefs; returns the old ids. */
    private fun cancelScheduled(context: Context): Set<String> {
        val app = context.applicationContext
        val ids = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SCHEDULED, emptySet()) ?: emptySet()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ids.forEach { id ->
            val intent = Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_EVENT_ID, id)
            val pending = PendingIntent.getBroadcast(
                app, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pending)
        }
        return ids
    }

    /**
     * Re-plan all reminder alarms from the current ReminderStore selection
     * and event list. Safe to call repeatedly (idempotent full rebuild).
     */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val oldIds = cancelScheduled(app)

        val store = ReminderStore(app)
        if (!store.enabled.value) {
            saveScheduled(app, emptySet())
            oldIds.forEach { LiveUpdateNotifier.cancel(app, it) }
            return
        }

        ensureChannel(app)
        val events = EventStore(app).events.value
        val advance = store.advanceDays.value
        val today = DateUtils.today()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()

        val scheduled = mutableSetOf<String>()
        events.filter { store.isSelected(it) }.forEach { event ->
            val target = DateUtils.effectiveDate(event)
            val reminderDay = target.minusDays(advance.toLong())
            if (reminderDay.isBefore(today)) return@forEach
            val trigger = reminderDay.atTime(REMINDER_HOUR, 0)
                .atZone(zone).toInstant().toEpochMilli()
            if (trigger <= now) return@forEach

            val intent = Intent(app, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            val pending = PendingIntent.getBroadcast(
                app, event.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
            scheduled.add(event.id)
        }
        saveScheduled(app, scheduled)
        // Drop stale 持续通知 (Live Updates) of events no longer scheduled.
        oldIds.filter { it !in scheduled }.forEach { LiveUpdateNotifier.cancel(app, it) }
    }

    private fun saveScheduled(context: Context, ids: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SCHEDULED, ids)
            .apply()
    }
}
