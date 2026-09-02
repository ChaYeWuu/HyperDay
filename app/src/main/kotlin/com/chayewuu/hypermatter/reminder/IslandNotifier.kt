package com.chayewuu.hypermatter.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import com.chayewuu.hypermatter.MainActivity
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 小米超级岛通知发送器（移植自 NexioSchedule 的 bypass 机制）。
 *
 * 超级岛应用白名单由 XMSF（com.xiaomi.xmsf 小米云服务）云端校验；发通知
 * 前通过 Shizuku 临时切断 XMSF 网络（OEM 防火墙链 + uid 规则），云端校验
 * 失败时系统走本地兜底渲染超级岛，发完立即恢复网络。
 *
 * 流程由 Mutex 串行化 + try/finally 保证 XMSF 网络必定恢复；Shizuku 不可
 * 用或切断失败时直接发送（降级为普通焦点通知）。
 */
object IslandNotifier {

    private const val TAG = "IslandNotifier"
    const val CHANNEL_ISLAND = "reminders_island"

    val scope = CoroutineScope(Dispatchers.IO)

    /** 串行化 bypass 流程，避免并发导致 XMSF 网络状态错乱。 */
    private val bypassMutex = Mutex()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ISLAND) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ISLAND,
                "超级岛提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "以小米超级岛样式展示的倒数日提醒"
                setShowBadge(true)
            }
        )
    }

    /** Shizuku 已运行且已授权（bypass 可用）。 */
    fun isBypassAvailable(context: Context): Boolean =
        ShizukuManager.isAuthorized(context)

    private suspend fun withShizukuBypass(
        context: Context,
        notificationId: Int,
        notification: Notification,
    ) {
        if (!isBypassAvailable(context)) {
            postDirect(context, notificationId, notification)
            return
        }
        bypassMutex.withLock {
            val disabled = try {
                ShizukuManager.setXmsfNetworkingEnabled(context, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable XMSF networking", e)
                false
            }
            if (!disabled) {
                Log.w(TAG, "Failed to disable XMSF networking, sending directly")
                postDirect(context, notificationId, notification)
                return@withLock
            }
            try {
                Log.d(TAG, "XMSF networking disabled, sending notification")
                postDirect(context, notificationId, notification)
                delay(100)
            } finally {
                try {
                    ShizukuManager.setXmsfNetworkingEnabled(context, true)
                    Log.d(TAG, "XMSF networking restored")
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: Failed to restore XMSF networking!", e)
                }
            }
        }
    }

    private fun postDirect(context: Context, notificationId: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — nothing to show.
        }
    }

    /**
     * 发送事件的超级岛/焦点通知（在 [scope] 协程内执行 bypass 流程）。
     */
    fun sendEventIsland(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        targetTimestamp: Long,
        hintContent: String,
        hintTitle: String,
    ) {
        ensureChannel(context)
        val notification = buildIslandNotification(
            context = context,
            eventId = eventId,
            title = title,
            content = content,
            targetTimestamp = targetTimestamp,
            hintContent = hintContent,
            hintTitle = hintTitle,
        )
        scope.launch {
            withShizukuBypass(context, eventId.hashCode(), notification)
        }
    }

    /**
     * 立即发送一条测试超级岛（60 秒倒计时），用于设置页手动验证。
     */
    fun sendTestIsland(context: Context) {
        ensureChannel(context)
        val target = System.currentTimeMillis() + 60_000L
        val notification = buildIslandNotification(
            context = context,
            eventId = "test_island",
            title = "超级岛测试",
            content = "60 秒后到来",
            targetTimestamp = target,
            hintContent = "还有 1 分钟",
            hintTitle = "",
        )
        scope.launch {
            withShizukuBypass(context, "test_island".hashCode(), notification)
        }
    }

    private fun buildIslandNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        targetTimestamp: Long,
        hintContent: String,
        hintTitle: String,
    ): Notification {
        val app = context.applicationContext

        val open = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId.takeIf { it != "test_island" })
        }
        val contentIntent = PendingIntent.getActivity(
            app, eventId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(app, CHANNEL_ISLAND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // Same-id re-posts (day-0 island re-pop) must not re-alert while
            // the notification is still active; the island re-expansion is
            // driven by the focus `reopen` param, not the alert.
            .setOnlyAlertOnce(true)

        // 超级岛/焦点通知 payload + 引用图标。
        val pics = Bundle().apply {
            val icon = Icon.createWithResource(app, R.mipmap.ic_launcher)
            putParcelable(FocusNotification.PIC_APP_ICON, icon)
            putParcelable(FocusNotification.PIC_APP_ICON_DARK, icon)
            putParcelable(FocusNotification.PIC_SMALL, icon)
            putParcelable(FocusNotification.PIC_SMALL_DARK, icon)
        }
        builder.addExtras(Bundle().apply {
            putBundle(FocusNotification.EXTRA_PICS, pics)
        })

        val params = FocusNotification.buildParams(
            context = app,
            title = title,
            content = content,
            targetTimestamp = targetTimestamp,
            hintContent = hintContent,
            hintTitle = hintTitle,
        )

        val notification = builder.build()
        notification.extras.putString(FocusNotification.EXTRA_PARAM, params)
        return notification
    }
}
