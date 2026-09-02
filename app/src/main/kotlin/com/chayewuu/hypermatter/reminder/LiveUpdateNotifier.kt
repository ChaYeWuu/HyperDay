package com.chayewuu.hypermatter.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chayewuu.hypermatter.MainActivity
import com.chayewuu.hypermatter.R

/**
 * Android 16 持续通知（Live Updates / promoted ongoing）：
 * 倒计时秒表风格的通知，chronometer 实时倒数到目标时刻，系统在到达时
 * 自动移除（setTimeoutAfter）。API 36+ 申请 promoted（关键持续通知），
 * 旧系统降级为普通 ongoing 通知。
 */
object LiveUpdateNotifier {

    const val CHANNEL_LIVE = "reminders_live"

    /** 与超级岛通知 id（event.id.hashCode()）错开的偏移。 */
    private const val ID_OFFSET = 1_000_000

    fun liveId(eventId: String): Int = eventId.hashCode() + ID_OFFSET

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_LIVE) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIVE,
                "持续倒数提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "倒数日实时倒计时持续通知"
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** API 36+ 且系统允许本应用发送 promoted（关键持续）通知。 */
    fun canPostPromoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.canPostPromotedNotifications()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 发送/刷新某事件的持续倒计时通知（同一 id 重复 notify 无痕更新）。
     *
     * @param targetTimestamp 倒计时归零时刻（毫秒），须为未来时间。
     */
    fun showCountdown(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        shortCriticalText: String,
        targetTimestamp: Long,
    ) {
        val app = context.applicationContext
        ensureChannel(app)

        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        }
        val contentIntent = PendingIntent.getActivity(
            app, eventId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(app, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setWhen(targetTimestamp)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_REMINDER)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(shortCriticalText)
            builder.setRequestPromotedOngoing(true)
        }

        val timeout = targetTimestamp - System.currentTimeMillis()
        if (timeout > 0) builder.setTimeoutAfter(timeout)

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE

        try {
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(liveId(eventId), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — nothing to show.
        }
    }

    /** 取消某事件的持续倒计时通知。 */
    fun cancel(context: Context, eventId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(liveId(eventId))
    }
}
