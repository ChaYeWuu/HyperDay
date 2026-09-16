package com.chayewuu.hypermatter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chayewuu.hypermatter.MainActivity
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.data.DeerTrackerStore
import java.time.LocalDate
import java.time.YearMonth

/**
 * 🦌🦌记录器 home-screen widgets (RemoteViews, same hand-drawn HyperOS styling
 * as the countdown widgets — Miuix has no AppWidget component):
 *
 *  - [DeerStatusWidget]   2x1 — 已戒 N 天 + 今日状态 + 最长纪录
 *  - [DeerCalendarWidget] 2x2 — 本月记录一览（破戒日高亮 / 今天描边）
 *                                + 已戒天数 + 本月破戒次数
 *
 * Both open the 🦌🦌记录器 page (deep link into MainActivity). Unlike the
 * countdown widgets the data does NOT flow through [com.chayewuu.hypermatter.data.EventStore],
 * so [pushDeerWidgets] is called by the recorder page itself after every
 * write.
 */

private val CELL_IDS = intArrayOf(
    R.id.deer_cell_0,
    R.id.deer_cell_1,
    R.id.deer_cell_2,
    R.id.deer_cell_3,
    R.id.deer_cell_4,
    R.id.deer_cell_5,
    R.id.deer_cell_6,
)

/** Open the 🦌🦌记录器 page (separate request codes keep the PIs distinct). */
private fun openDeerTracker(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_DEER_TRACKER, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

// ---------------------------------------------------------------------------
// Status widget (2x1)
// ---------------------------------------------------------------------------

class DeerStatusWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateDeerStatusWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
        } else {
            // DATE_CHANGED / TIMEZONE_CHANGED: the day rolled over.
            push(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        updateDeerStatusWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DeerStatusWidget::class.java))
            ids.forEach { updateDeerStatusWidget(context, manager, it) }
        }
    }
}

private fun updateDeerStatusWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
) {
    val today = LocalDate.now()
    val records = DeerTrackerStore.getRecords(context)
    val quit = DeerTrackerStore.daysQuit(records, today)
    val views = RemoteViews(context.packageName, R.layout.widget_deer_status)

    views.setTextViewText(
        R.id.deer_today,
        if (records[today.toEpochDay()] == DeerTrackerStore.STATUS_HIT) "今日已记录" else "今日未记录",
    )
    if (quit == null) {
        // Nothing recorded yet: no run to count.
        views.setTextViewText(R.id.deer_days, "--")
        views.setTextViewText(R.id.deer_days_unit, "")
        views.setTextViewText(R.id.deer_best, "还没有记录")
    } else {
        views.setTextViewText(R.id.deer_days, quit.toString())
        views.setTextViewText(R.id.deer_days_unit, "天")
        views.setTextViewText(
            R.id.deer_best,
            "最长 ${DeerTrackerStore.bestQuit(records, today)} 天",
        )
    }
    views.setOnClickPendingIntent(R.id.widget_root, openDeerTracker(context, 4000 + appWidgetId))
    manager.updateAppWidget(appWidgetId, views)
}

// ---------------------------------------------------------------------------
// Month widget (2x2)
// ---------------------------------------------------------------------------

class DeerCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateDeerCalendarWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
        } else {
            push(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        updateDeerCalendarWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DeerCalendarWidget::class.java),
            )
            ids.forEach { updateDeerCalendarWidget(context, manager, it) }
        }
    }
}

private fun updateDeerCalendarWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
) {
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val records = DeerTrackerStore.getRecords(context)
    val quit = DeerTrackerStore.daysQuit(records, today)

    val views = RemoteViews(context.packageName, R.layout.widget_deer_calendar)
    views.setTextViewText(
        R.id.deer_stat,
        buildString {
            append(if (quit == null) "还没有记录" else "已戒 $quit 天")
            append(" · 本月破戒 ${DeerTrackerStore.monthHits(records, month)} 次")
        },
    )

    // Monday-first grid: blanks before the 1st, one row per week (max 6).
    val leading = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val weeks = ((leading + daysInMonth + 6) / 7).coerceAtMost(6)
    val todayEpochDay = today.toEpochDay()

    views.removeAllViews(R.id.deer_grid)
    for (week in 0 until weeks) {
        val row = RemoteViews(context.packageName, R.layout.widget_deer_week)
        for (slot in 0 until 7) {
            val dayOfMonth = week * 7 + slot - leading + 1
            val dayEpochDay = if (dayOfMonth in 1..daysInMonth) {
                month.atDay(dayOfMonth).toEpochDay()
            } else {
                Long.MIN_VALUE
            }
            val cell = when {
                dayEpochDay == Long.MIN_VALUE -> R.drawable.widget_deer_day_none
                records[dayEpochDay] == DeerTrackerStore.STATUS_HIT ->
                    R.drawable.widget_deer_day_hit
                dayEpochDay == todayEpochDay -> R.drawable.widget_deer_day_today
                else -> R.drawable.widget_deer_day_none
            }
            row.setImageViewResource(CELL_IDS[slot], cell)
        }
        views.addView(R.id.deer_grid, row)
    }

    views.setOnClickPendingIntent(R.id.widget_root, openDeerTracker(context, 5000 + appWidgetId))
    manager.updateAppWidget(appWidgetId, views)
}

/** Refresh the 🦌🦌记录器 widgets (called after every recorder write). */
fun pushDeerWidgets(context: Context) {
    DeerStatusWidget.push(context)
    DeerCalendarWidget.push(context)
}
