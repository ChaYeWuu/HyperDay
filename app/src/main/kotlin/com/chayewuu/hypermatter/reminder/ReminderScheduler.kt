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
import java.time.temporal.ChronoUnit

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
    private const val PREFS_FIRED = "hypermatter_reminder_fired"
    private const val KEY_FIRED = "fired_reminders"

    /** Distinct request code of the daily live-refresh alarm. */
    private const val REQUEST_REFRESH = 0x11FE_0129

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
            // Countdown-style switch alarm for the same event.
            val switchIntent = Intent(app, CountdownSwitchReceiver::class.java)
                .putExtra(CountdownSwitchReceiver.EXTRA_EVENT_ID, id)
            val switchPending = PendingIntent.getBroadcast(
                app, LiveUpdateNotifier.liveId(id), switchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(switchPending)
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
            EventStore(app).events.value.forEach { LiveUpdateNotifier.cancel(app, it.id) }
            armDayRefresh(app, canExact = false, needed = false)
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
        val fired = firedToday(app, today.toEpochDay())
        events.filter { store.isSelected(it) }.forEach { event ->
            val target = DateUtils.effectiveDate(event)
            val reminderDay = target.minusDays(advance.toLong())
            if (reminderDay.isBefore(today)) return@forEach
            var trigger = reminderDay.atTime(REMINDER_HOUR, 0)
                .atZone(zone).toInstant().toEpochMilli()
            if (trigger <= now) {
                // The 09:00 reminder moment already passed (e.g. reminder was
                // enabled after 09:00 today): late-fire right away, but only
                // once per day — later re-schedules skip via the marker.
                if (reminderDay > today) return@forEach
                if (event.id in fired) return@forEach
                trigger = now + 3_000
            }

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

        // Events that should currently hold a 实时动态 notification:
        // selected AND inside the reminder window. Fired reminder-day events
        // deliberately stay in this set — they leave the alarm bookkeeping
        // (`scheduled`) but their ongoing notification must survive. (The old
        // `oldIds.filter { it !in scheduled }` cancel killed the live
        // notification one second after the reminder receiver posted it,
        // because firing removes the event from the bookkeeping.)
        val liveKeep = if (store.liveUpdatesEnabled.value) {
            events.filter { store.isSelected(it) }.mapNotNull { ev ->
                val days = ChronoUnit.DAYS.between(today, DateUtils.effectiveDate(ev))
                if (days in 0..advance.toLong()) ev.id else null
            }.toSet()
        } else {
            emptySet()
        }
        // Cancel stale live notifications: deselected / out-of-window /
        // deleted events (oldIds persists deleted ids) / live toggled off.
        (oldIds + events.map { it.id }).filter { it !in liveKeep }
            .forEach { LiveUpdateNotifier.cancel(app, it) }

        // Daily live-refresh chain: keep countdown texts / island state in
        // sync as days roll over. Armed while any selected event sits in the
        // reminder window [0, advance] (reminder-day events arm it for their
        // following days).
        val live = store.liveUpdatesEnabled.value
        val anyInWindow = events.filter { store.isSelected(it) }.any { ev ->
            val days = ChronoUnit.DAYS.between(today, DateUtils.effectiveDate(ev))
            days in 0..advance.toLong()
        }
        armDayRefresh(app, canExact, needed = anyInWindow && (store.islandEnabled.value || live))

        if (live) {
            // Keep every selected event inside the reminder window in sync:
            // silent same-id re-post refreshes the ongoing notification —
            // style auto-picked from the remaining time (plain「还有 N 天」
            // until the final 12 h window, then the 秒表倒数).
            // Filtered by selection & window (NOT `scheduled` — fired events
            // leave the alarm bookkeeping but must keep refreshing). Also
            // posts on days == advance: a reminder deferred by MIUI standby
            // past the switch moment (target − 12 h) can no longer arm the
            // style-switch alarm, and a live-toggled-on-after-09:00 reminder
            // would otherwise stay without its notification until day 0 —
            // same-id re-posts are seamless (onlyAlertOnce).
            events.filter { store.isSelected(it) }.forEach { ev ->
                val days = ChronoUnit.DAYS.between(today, DateUtils.effectiveDate(ev))
                if (days !in 0..advance.toLong()) return@forEach
                val timing = ReminderReceiver.timingFor(ev)
                LiveUpdateNotifier.showCountdown(
                    context = app,
                    eventId = ev.id,
                    title = "${ev.title} · 倒计时",
                    content = DateUtils.describe(ev),
                    shortCriticalText = timing.hintContent,
                    targetTimestamp = timing.targetMillis,
                )
                val switchAt = timing.targetMillis - LiveUpdateNotifier.COUNTDOWN_LEAD_MS
                if (switchAt > now) {
                    val switchIntent = Intent(app, CountdownSwitchReceiver::class.java)
                        .putExtra(CountdownSwitchReceiver.EXTRA_EVENT_ID, ev.id)
                    val switchPending = PendingIntent.getBroadcast(
                        app, LiveUpdateNotifier.liveId(ev.id), switchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    if (canExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, switchAt, switchPending,
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, switchAt, switchPending,
                        )
                    }
                }
            }
        }
        // (Live Updates toggled off needs no extra branch: liveKeep is empty
        // above, so the unified cancel already removed every notification.)
    }

    /**
     * Arm the daily 09:00:30 [LiveUpdateRefreshReceiver] alarm (or cancel it
     * when not needed).
     */
    private fun armDayRefresh(context: Context, canExact: Boolean, needed: Boolean) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, LiveUpdateRefreshReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            app, REQUEST_REFRESH, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!needed) {
            alarmManager.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        var trigger = DateUtils.today().atTime(REMINDER_HOUR, 0, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (trigger <= now) trigger = DateUtils.today().plusDays(1)
            .atTime(REMINDER_HOUR, 0, 30).atZone(zone).toInstant().toEpochMilli()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    /**
     * Record that [eventId]'s notification fired today ("eventId:epochDay"
     * markers). Used to late-fire a reminder whose 09:00 moment already
     * passed exactly once, without re-firing on every re-schedule.
     */
    fun markFired(context: Context, eventId: String) {
        val app = context.applicationContext
        val today = DateUtils.today().toEpochDay()
        val prefs = app.getSharedPreferences(PREFS_FIRED, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FIRED, emptySet()) ?: emptySet()
        // Keep only today's markers — older days are irrelevant.
        val next = current.filter { it.endsWith(":$today") }.toSet() + "$eventId:$today"
        prefs.edit().putStringSet(KEY_FIRED, next).apply()
    }

    /** Event ids whose notification already fired today. */
    private fun firedToday(context: Context, todayEpoch: Long): Set<String> {
        val all = context.applicationContext
            .getSharedPreferences(PREFS_FIRED, Context.MODE_PRIVATE)
            .getStringSet(KEY_FIRED, emptySet()) ?: emptySet()
        return all.filterTo(mutableSetOf()) { it.endsWith(":$todayEpoch") }
            .mapTo(mutableSetOf()) { it.substringBeforeLast(":") }
    }

    private fun saveScheduled(context: Context, ids: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SCHEDULED, ids)
            .apply()
    }
}
