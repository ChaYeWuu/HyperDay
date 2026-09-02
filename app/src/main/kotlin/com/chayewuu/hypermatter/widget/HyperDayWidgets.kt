package com.chayewuu.hypermatter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.chayewuu.hypermatter.MainActivity
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.EventStore
import kotlin.math.min

/**
 * HyperDay home-screen widgets.
 *
 * Miuix has no AppWidget component (widgets render via RemoteViews/XML, not
 * Compose), so these layouts are hand-drawn in the Miuix/HyperOS design
 * language: rounded white/#242424 cards, Miuix light/dark palette via
 * values / values-night color resources, primary-blue accents.
 *
 * Three styles:
 *  - [CardWidget]   2x2  — one event (user-picked via WidgetPrefs, else auto
 *                          nearest upcoming/past), 距离/过去 tag top-left
 *  - [ListWidget]   4x2  — nearest 4 events (upcoming first, then past),
 *                          one row each with its own 距离/过去 tag
 *  - [MinimalWidget] 2x1 — auto nearest event (upcoming, else past) with
 *                          距离/过去 tag + day number + title on one line
 *
 * Data refresh triggers:
 *  - EventStore writes (see [updateAllWidgets])
 *  - System periodic update (30 min) and DATE_CHANGED / TIMEZONE_CHANGED
 *  - Manual onUpdate from the launcher
 */

/** Upcoming events (recurring always count), soonest first. */
private fun upcomingEvents(context: Context): List<CountdownEvent> =
    EventStore(context).events.value
        .filter { !DateUtils.isPastEvent(it) }
        .sortedBy { DateUtils.effectiveEpochDay(it) }

/**
 * Nearest events regardless of direction: upcoming soonest first, then past
 * most-recent first. Used by the list widget (rows carry 距离/过去 tags) and
 * the minimal widget (auto: nearest upcoming, else nearest past).
 */
private fun feedEvents(context: Context): List<CountdownEvent> {
    val all = EventStore(context).events.value
    val upcoming = all
        .filter { !DateUtils.isPastEvent(it) }
        .sortedBy { DateUtils.effectiveEpochDay(it) }
    val past = all
        .filter { DateUtils.isPastEvent(it) }
        .sortedByDescending { DateUtils.effectiveEpochDay(it) }
    return upcoming + past
}

/**
 * Per-app widget configuration: which event the single-event widget is
 * bound to. Kept separate from EventStore/SettingsStore because the widget
 * provider (a plain BroadcastReceiver) reads it synchronously.
 */
object WidgetPrefs {
    private const val PREFS = "hypermatter_widgets"
    private const val KEY_SINGLE_EVENT = "single_event_id"

    fun getSingleEventId(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SINGLE_EVENT, null)

    fun setSingleEventId(context: Context, eventId: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SINGLE_EVENT, eventId)
            .apply()
    }
}

/**
 * The event shown by the card widget: the user's pick if it still exists,
 * else auto mode (nearest upcoming, else nearest past). Deliberately
 * supports past events so the tag can flip to 过去.
 */
internal fun singleEvent(context: Context): CountdownEvent? {
    val all = EventStore(context).events.value
    WidgetPrefs.getSingleEventId(context)?.let { id ->
        all.firstOrNull { it.id == id }?.let { return it }
    }
    return upcomingEvents(context).firstOrNull()
        ?: all.sortedByDescending { DateUtils.effectiveEpochDay(it) }
            .firstOrNull()
}

/** Date line under the title: repeat rule for recurring, otherwise date + weekday. */
internal fun eventDateLine(event: CountdownEvent): String {
    val repeat = DateUtils.repeatLabel(event)
    if (repeat.isNotBlank()) return repeat
    val day = DateUtils.effectiveEpochDay(event)
    return "${DateUtils.formatDate(day)} ${DateUtils.weekdayLabel(day)}"
}

/** "今日 · M月d日 周X" line shown next to the tag pill on card/minimal widgets. */
internal fun todayLine(): String {
    val today = java.time.LocalDate.now()
    return "今日 · ${today.monthValue}月${today.dayOfMonth}日 ${DateUtils.weekdayLabel(today.toEpochDay())}"
}

/** Open the event detail page (deep link into MainActivity). */
private fun openEvent(context: Context, eventId: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return PendingIntent.getActivity(
        context,
        eventId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Open the app home. */
private fun openApp(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

// ---------------------------------------------------------------------------
// Card widget (2x2) — one event with 距离/过去 tag, supports past events
// ---------------------------------------------------------------------------

class CardWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateCardWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
        } else {
            // DATE_CHANGED / TIMEZONE_CHANGED: refresh everything.
            push(context)
        }
    }

    /** The launcher resized the widget — recompute the square card. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        updateCardWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CardWidget::class.java))
            ids.forEach { updateCardWidget(context, manager, it) }
        }
    }
}

/**
 * MIUI 2x2 cells are taller than wide, so the whole-cell card looked
 * non-square. Instead, the visible card (widget_card_box) is sized to a true
 * square (min of the widget's portrait width/height in dp) and centered
 * inside the transparent widget_root. Needs API 31+ RemoteViews size APIs;
 * below that the card simply fills the cell as before.
 */
private fun squareCardBox(context: Context, views: RemoteViews, manager: AppWidgetManager, appWidgetId: Int) {
    if (Build.VERSION.SDK_INT < 31) return
    val opts = manager.getAppWidgetOptions(appWidgetId)
    val width = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val height = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    if (width <= 0 || height <= 0) return
    val density = context.resources.displayMetrics.density
    val side = min(width, height).toFloat()
    android.util.Log.d(
        "HyperDayWidget",
        "squareCardBox id=$appWidgetId optW=$width optH=$height density=$density side=$side"
    )
    views.setViewLayoutWidth(R.id.widget_card_box, side, TypedValue.COMPLEX_UNIT_DIP)
    views.setViewLayoutHeight(R.id.widget_card_box, side, TypedValue.COMPLEX_UNIT_DIP)
}

private fun updateCardWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
) {
    val event = singleEvent(context)
    val views = RemoteViews(context.packageName, R.layout.widget_card)
    squareCardBox(context, views, manager, appWidgetId)
    views.setTextViewText(R.id.widget_today, todayLine())
    if (event == null) {
        views.setTextViewText(R.id.widget_tag, "")
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
        views.setTextViewText(R.id.widget_date, "点击打开 HyperDay 添加")
        views.setTextViewText(R.id.widget_days, "--")
        views.setTextViewText(R.id.widget_days_unit, "")
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 1000 + appWidgetId))
    } else {
        val past = DateUtils.isPastEvent(event)
        views.setTextViewText(R.id.widget_tag, if (past) "过去" else "距离")
        views.setTextViewText(R.id.widget_title, event.title)
        views.setTextViewText(R.id.widget_date, eventDateLine(event))
        val days = DateUtils.dayNumber(event)
        views.setTextViewText(R.id.widget_days, days.toString())
        views.setTextViewText(R.id.widget_days_unit, "天")
        views.setOnClickPendingIntent(R.id.widget_root, openEvent(context, event.id))
    }
    manager.updateAppWidget(appWidgetId, views)
}

// ---------------------------------------------------------------------------
// List widget (4x2)
// ---------------------------------------------------------------------------

class ListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateListWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
        } else {
            push(context)
        }
    }

    companion object {
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ListWidget::class.java))
            ids.forEach { updateListWidget(context, manager, it) }
        }
    }
}

private fun updateListWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
) {
    val events = feedEvents(context).take(4)
    val views = RemoteViews(context.packageName, R.layout.widget_list)
    views.setTextViewText(R.id.widget_header_date, todayLine())
    views.removeAllViews(R.id.widget_rows)
    if (events.isEmpty()) {
        val row = RemoteViews(context.packageName, R.layout.widget_list_row)
        row.setTextViewText(R.id.widget_row_tag, "")
        row.setTextViewText(R.id.widget_row_title, context.getString(R.string.widget_empty_title))
        row.setTextViewText(R.id.widget_row_date, "")
        row.setTextViewText(R.id.widget_row_days, "--")
        row.setTextViewText(R.id.widget_row_days_unit, "")
        row.setOnClickPendingIntent(R.id.widget_row_root, openApp(context, appWidgetId))
        views.addView(R.id.widget_rows, row)
    } else {
        events.forEach { event ->
            val row = RemoteViews(context.packageName, R.layout.widget_list_row)
            row.setTextViewText(
                R.id.widget_row_tag,
                if (DateUtils.isPastEvent(event)) "过去" else "距离",
            )
            row.setTextViewText(R.id.widget_row_title, event.title)
            row.setTextViewText(R.id.widget_row_date, eventDateLine(event))
            row.setTextViewText(R.id.widget_row_days, DateUtils.dayNumber(event).toString())
            row.setTextViewText(R.id.widget_row_days_unit, "天")
            row.setOnClickPendingIntent(R.id.widget_row_root, openEvent(context, event.id))
            views.addView(R.id.widget_rows, row)
        }
    }
    // Header area (outside rows) opens the app home.
    views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 2000 + appWidgetId))
    manager.updateAppWidget(appWidgetId, views)
}

// ---------------------------------------------------------------------------
// Minimal widget (2x1)
// ---------------------------------------------------------------------------

class MinimalWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateMinimalWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            super.onReceive(context, intent)
        } else {
            push(context)
        }
    }

    companion object {
        fun push(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MinimalWidget::class.java))
            ids.forEach { updateMinimalWidget(context, manager, it) }
        }
    }
}

private fun updateMinimalWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
) {
    val event = feedEvents(context).firstOrNull()
    val views = RemoteViews(context.packageName, R.layout.widget_minimal)
    views.setTextViewText(R.id.widget_today, todayLine())
    if (event == null) {
        views.setTextViewText(R.id.widget_tag, "")
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
        views.setTextViewText(R.id.widget_days, "--")
        views.setTextViewText(R.id.widget_days_unit, "")
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, 3000 + appWidgetId))
    } else {
        views.setTextViewText(
            R.id.widget_tag,
            if (DateUtils.isPastEvent(event)) "过去" else "距离",
        )
        views.setTextViewText(R.id.widget_title, event.title)
        views.setTextViewText(R.id.widget_days, DateUtils.dayNumber(event).toString())
        views.setTextViewText(R.id.widget_days_unit, "天")
        views.setOnClickPendingIntent(R.id.widget_root, openEvent(context, event.id))
    }
    manager.updateAppWidget(appWidgetId, views)
}

/**
 * Refresh every HyperDay widget. Called by EventStore after each write so the
 * home screen stays in sync with in-app changes.
 */
fun updateAllWidgets(context: Context) {
    CardWidget.push(context)
    ListWidget.push(context)
    MinimalWidget.push(context)
}
