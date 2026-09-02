package com.chayewuu.hypermatter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms all reminder alarms after a reboot, or after the system clock /
 * timezone changes (absolute RTC alarms become stale at that point).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderScheduler.reschedule(context)
        }
    }
}
