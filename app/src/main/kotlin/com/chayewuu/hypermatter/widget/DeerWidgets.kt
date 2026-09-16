package com.chayewuu.hypermatter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
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
 *
 * The 2x2 month grid uses SIX statically declared week rows, each
 * `layout_height=0dp` + `layout_weight=1` (see widget_deer_calendar.xml):
 * the visible rows always split the full remaining height, so a 5-week month
 * gets taller cells instead of leaving a gap at the bottom. Static rows are
 * deliberate — children created by RemoteViews.addView do not reliably keep
 * their layout_weight, statically declared ones always do.
 */

/** The six static week rows of the month grid, top to bottom. */
private val WEEK_ROW_IDS = intArrayOf(
    R.id.deer_week_0,
    R.id.deer_week_1,
    R.id.deer_week_2,
    R.id.deer_week_3,
    R.id.deer_week_4,
    R.id.deer_week_5,
)

/** The 7 day cells of each static week row, in the same order as above. */
private val WEEK_CELL_IDS = arrayOf(
    intArrayOf(
        R.id.deer_w0_c0, R.id.deer_w0_c1, R.id.deer_w0_c2, R.id.deer_w0_c3,
        R.id.deer_w0_c4, R.id.deer_w0_c5, R.id.deer_w0_c6,
    ),
    intArrayOf(
        R.id.deer_w1_c0, R.id.deer_w1_c1, R.id.deer_w1_c2, R.id.deer_w1_c3,
        R.id.deer_w1_c4, R.id.deer_w1_c5, R.id.deer_w1_c6,
    ),
    intArrayOf(
        R.id.deer_w2_c0, R.id.deer_w2_c1, R.id.deer_w2_c2, R.id.deer_w2_c3,
        R.id.deer_w2_c4, R.id.deer_w2_c5, R.id.deer_w2_c6,
    ),
    intArrayOf(
        R.id.deer_w3_c0, R.id.deer_w3_c1, R.id.deer_w3_c2, R.id.deer_w3_c3,
        R.id.deer_w3_c4, R.id.deer_w3_c5, R.id.deer_w3_c6,
    ),
    intArrayOf(
        R.id.deer_w4_c0, R.id.deer_w4_c1, R.id.deer_w4_c2, R.id.deer_w4_c3,
        R.id.deer_w4_c4, R.id.deer_w4_c5, R.id.deer_w4_c6,
    ),
    intArrayOf(
        R.id.deer_w5_c0, R.id.deer_w5_c1, R.id.deer_w5_c2, R.id.deer_w5_c3,
        R.id.deer_w5_c4, R.id.deer_w5_c5, R.id.deer_w5_c6,
    ),
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

    // Monday-first grid: blanks before the 1st, one weighted row per week
    // (max 6). Unused rows are GONE so the visible ones share the full height.
    val leading = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val weeks = ((leading + daysInMonth + 6) / 7).coerceAtMost(6)
    val todayEpochDay = today.toEpochDay()
    val onAccent = context.getColor(R.color.widget_on_accent)
    val accent = context.getColor(R.color.widget_accent)
    val secondary = context.getColor(R.color.widget_text_secondary)

    for (week in 0 until WEEK_ROW_IDS.size) {
        val rowId = WEEK_ROW_IDS[week]
        if (week >= weeks) {
            views.setViewVisibility(rowId, View.GONE)
            continue
        }
        views.setViewVisibility(rowId, View.VISIBLE)
        for (slot in 0 until 7) {
            val dayOfMonth = week * 7 + slot - leading + 1
            val cellId = WEEK_CELL_IDS[week][slot]
            if (dayOfMonth !in 1..daysInMonth) {
                // Outside this month (lead-in / trail): fully transparent,
                // no date.
                views.setInt(cellId, "setBackgroundResource", R.drawable.widget_deer_day_blank)
                views.setTextViewText(cellId, "")
                continue
            }
            val dayEpochDay = month.atDay(dayOfMonth).toEpochDay()
            val hit = records[dayEpochDay] == DeerTrackerStore.STATUS_HIT
            val isToday = dayEpochDay == todayEpochDay
            views.setInt(
                cellId,
                "setBackgroundResource",
                when {
                    hit -> R.drawable.widget_deer_day_hit
                    isToday -> R.drawable.widget_deer_day_today
                    else -> R.drawable.widget_deer_day_none
                },
            )
            // Every cell carries its small day number; a 破戒 day adds the 🦌
            // above the date so the blue box still says which day it is.
            views.setTextViewText(cellId, if (hit) "🦌\n$dayOfMonth" else dayOfMonth.toString())
            views.setTextColor(
                cellId,
                when {
                    hit -> onAccent
                    isToday -> accent
                    else -> secondary
                },
            )
            views.setTextViewTextSize(
                cellId,
                TypedValue.COMPLEX_UNIT_SP,
                if (hit) 7f else 8f,
            )
        }
    }

    views.setOnClickPendingIntent(R.id.widget_root, openDeerTracker(context, 5000 + appWidgetId))
    manager.updateAppWidget(appWidgetId, views)
}

/** Refresh the 🦌🦌记录器 widgets (called after every recorder write). */
fun pushDeerWidgets(context: Context) {
    DeerStatusWidget.push(context)
    DeerCalendarWidget.push(context)
}
