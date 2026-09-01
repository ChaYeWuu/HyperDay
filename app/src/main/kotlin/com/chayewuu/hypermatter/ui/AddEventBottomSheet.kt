package com.chayewuu.hypermatter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.LunarCalendar
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Repeat options. Index == repeatType value. */
private val RepeatItems = listOf("不重复", "每天", "每周", "每月", "每年", "每年农历")

/** Which sub-page of the add sheet is visible. */
private enum class AddSheetPage { FORM, CONFIG }

/** Holiday quick presets. */
private data class HolidayPreset(
    val name: String,
    val repeatType: Int,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val month: Int,
    val day: Int,
)

private val LunarHolidays = listOf(
    HolidayPreset("春节", 5, lunarMonth = 1, lunarDay = 1, month = 1, day = 1),
    HolidayPreset("元宵节", 5, lunarMonth = 1, lunarDay = 15, month = 1, day = 15),
    HolidayPreset("端午节", 5, lunarMonth = 5, lunarDay = 5, month = 5, day = 5),
    HolidayPreset("七夕节", 5, lunarMonth = 7, lunarDay = 7, month = 7, day = 7),
    HolidayPreset("中秋节", 5, lunarMonth = 8, lunarDay = 15, month = 8, day = 15),
    HolidayPreset("重阳节", 5, lunarMonth = 9, lunarDay = 9, month = 9, day = 9),
    HolidayPreset("腊八节", 5, lunarMonth = 12, lunarDay = 8, month = 12, day = 8),
    HolidayPreset("除夕", 5, lunarMonth = 12, lunarDay = 30, month = 12, day = 31),
)

private val SolarHolidays = listOf(
    HolidayPreset("元旦", 4, month = 1, day = 1),
    HolidayPreset("情人节", 4, month = 2, day = 14),
    HolidayPreset("劳动节", 4, month = 5, day = 1),
    HolidayPreset("儿童节", 4, month = 6, day = 1),
    HolidayPreset("国庆节", 4, month = 10, day = 1),
    HolidayPreset("圣诞节", 4, month = 12, day = 25),
)

/** All holiday presets in dropdown order (lunar first, then solar). */
private val AllHolidays = LunarHolidays + SolarHolidays

/** Weekday list for the weekly config page (index 0 == 周一, value 1..7). */
private val WeekdayItems = (1..7).map { DateUtils.weekdayName(it) }

/**
 * Bottom sheet for adding a new countdown event.
 * The form itself stays compact (no scrolling); tapping the 重复/节假日
 * rows slides in a dedicated selection page inside the sheet, and every
 * repeat type except 不重复 then slides one level deeper into its own
 * config page (每天→时间, 每周→星期, 每月→几号, 每年→月日,
 * 每年农历→农历月日). Back gesture walks back one page at a time.
 */
@Composable
fun AddEventBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    editEvent: CountdownEvent? = null,
    onConfirm: (
        title: String,
        epochDay: Long,
        note: String?,
        repeatType: Int,
        lunarMonth: Int?,
        lunarDay: Int?,
        repeatWeekday: Int?,
        repeatMonthDay: Int?,
        repeatYearMonth: Int?,
        timeHour: Int?,
        timeMinute: Int?,
    ) -> Unit,
) {
    val today = DateUtils.today()
    val view = LocalView.current

    // Edit mode: the sheet is freshly composed for each edit target, so a
    // plain remember{} seeded from the event gives the prefilled draft.
    val initialDate = editEvent?.let { LocalDate.ofEpochDay(it.epochDay) } ?: today
    var title by remember { mutableStateOf(editEvent?.title ?: "") }
    var note by remember { mutableStateOf(editEvent?.note ?: "") }
    var year by remember { mutableIntStateOf(initialDate.year) }
    var month by remember { mutableIntStateOf(initialDate.monthValue) }
    var day by remember { mutableIntStateOf(initialDate.dayOfMonth) }
    var repeatType by remember { mutableIntStateOf(editEvent?.repeatType ?: 0) }
    var lunarMonth by remember { mutableStateOf(editEvent?.lunarMonth) }
    var lunarDay by remember { mutableStateOf(editEvent?.lunarDay) }
    // Per-type repeat config drafts.
    var weekday by remember { mutableIntStateOf(editEvent?.repeatWeekday ?: initialDate.dayOfWeek.value) }
    var monthDay by remember { mutableIntStateOf(editEvent?.repeatMonthDay ?: initialDate.dayOfMonth) }
    var yearMonth by remember { mutableIntStateOf(editEvent?.repeatYearMonth ?: initialDate.monthValue) }
    var hour by remember { mutableIntStateOf(editEvent?.timeHour ?: 9) }
    var minute by remember { mutableIntStateOf(editEvent?.timeMinute ?: 0) }
    var page by remember { mutableStateOf(AddSheetPage.FORM) }

    /** Shared "go back one level" used by the back gesture and × button. */
    fun pageBack() {
        page = when (page) {
            AddSheetPage.FORM -> return
            else -> AddSheetPage.FORM
        }
    }

    fun applyPreset(preset: HolidayPreset) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        title = preset.name
        repeatType = preset.repeatType
        lunarMonth = preset.lunarMonth
        lunarDay = preset.lunarDay
        val anchor = when (preset.repeatType) {
            5 -> LunarCalendar.nextLunarAnniversary(
                // 除夕 falls on the last day of the lunar year; the table's
                // 30 is clamped automatically when 腊月 has only 29 days.
                preset.lunarMonth!!, preset.lunarDay!!, today,
            )
            else -> {
                val candidate = safeDateOf(today.year, preset.month, preset.day)
                if (candidate.isBefore(today)) safeDateOf(today.year + 1, preset.month, preset.day)
                else candidate
            }
        }
        year = anchor.year
        month = anchor.monthValue
        day = anchor.dayOfMonth
        // Solar yearly holidays (repeatType 4) must carry their real
        // month/day in the repeat fields — otherwise the confirm callback
        // emits the form's initial values (today) and the event computes
        // "every year today" instead of the holiday date.
        if (preset.repeatType == 4) {
            yearMonth = preset.month
            monthDay = preset.day
        }
        // Picked from the dropdown on the form — no page change needed.
    }

    /**
     * Tapping a repeat option: 不重复 returns straight to the form; every
     * other type slides into its own config page seeded from the current
     * selection.
     */
    fun selectRepeat(index: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val previousType = repeatType
        repeatType = index
        val selected = safeLocalDate(year, month, day)
        when (index) {
            0 -> {
                lunarMonth = null
                lunarDay = null
                // Stays on the form — the dropdown collapses on selection.
            }
            // Keep the previously chosen weekday when re-entering the
            // weekly config; otherwise seed from the selected date.
            2 -> {
                if (previousType != 2) weekday = selected.dayOfWeek.value
                page = AddSheetPage.CONFIG
            }
            3 -> {
                if (previousType != 3 && previousType != 4) monthDay = selected.dayOfMonth
                page = AddSheetPage.CONFIG
            }
            4 -> {
                if (previousType != 4) {
                    yearMonth = selected.monthValue
                    if (previousType != 3) monthDay = selected.dayOfMonth
                }
                page = AddSheetPage.CONFIG
            }
            5 -> {
                if (previousType != 5) {
                    val lunar = LunarCalendar.solarToLunar(selected)
                    lunarMonth = lunar.month
                    lunarDay = lunar.day
                }
                page = AddSheetPage.CONFIG
            }
            else -> page = AddSheetPage.CONFIG
        }
    }

    val repeatSummary = when (repeatType) {
        0 -> "不重复"
        1 -> "每天 %02d:%02d".format(hour, minute)
        2 -> "每周${DateUtils.weekdayName(weekday)}"
        3 -> "每月${monthDay}日"
        4 -> "每年${yearMonth}月${monthDay}日"
        5 -> {
            val lm = lunarMonth ?: 1
            val ld = lunarDay ?: 1
            "每年农历${if (lm == 12) "腊月" else if (lm == 11) "冬月" else "${DateUtils.lunarMonthName(lm)}月"}${DateUtils.lunarDayName(ld)}"
        }
        else -> "不重复"
    }

    val sheetTitle = when (page) {
        AddSheetPage.FORM -> if (editEvent != null) "编辑倒数日" else "添加倒数日"
        AddSheetPage.CONFIG -> RepeatItems[repeatType]
    }

    OverlayBottomSheet(
        title = sheetTitle,
        show = show,
        // Silver-gray canvas so the white cards stand out (light:
        // #F7F7F7, dark: black — same as the page canvas).
        backgroundColor = MiuixTheme.colorScheme.surface,
        // On sub-pages the sheet itself must not consume the back gesture
        // (or drag-dismiss / outside-tap): those are routed one level back
        // by the BackHandler below, which composes inside the sheet's
        // popup AFTER the sheet's own NavigationBackHandler and therefore
        // wins the back dispatch. Only the form page allows direct dismiss.
        allowDismiss = page == AddSheetPage.FORM,
        startAction = {
            IconButton(
                onClick = {
                    if (page != AddSheetPage.FORM) {
                        pageBack()
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "取消",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    when (page) {
                        AddSheetPage.FORM -> {
                            if (title.isNotBlank()) {
                                val date = safeLocalDate(year, month, day)
                                onConfirm(
                                    title, date.toEpochDay(), note,
                                    repeatType, lunarMonth, lunarDay,
                                    if (repeatType == 2) weekday else null,
                                    if (repeatType == 3 || repeatType == 4) monthDay else null,
                                    if (repeatType == 4) yearMonth else null,
                                    if (repeatType == 1) hour else null,
                                    if (repeatType == 1) minute else null,
                                )
                            }
                        }
                        else -> page = AddSheetPage.FORM
                    }
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "确定",
                    tint = if (title.isNotBlank() || page != AddSheetPage.FORM)
                        MiuixTheme.colorScheme.primary
                    else
                        MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onDismissRequest = {
            // Back/outside dismiss from a sub-page only pops one level.
            if (page != AddSheetPage.FORM) pageBack() else onDismiss()
        },
    ) {
        // Must live INSIDE the sheet content: it then composes within the
        // sheet's popup after the sheet's own NavigationBackHandler, so on
        // sub-pages this handler receives the back gesture first and walks
        // back one page instead of the sheet sliding fully off-screen.
        BackHandler(enabled = page != AddSheetPage.FORM) { pageBack() }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == AddSheetPage.FORM) {
                    // back: selection page slides out to the right
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                } else {
                    // forward: selection page slides in from the right
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                }
            },
            label = "addSheetPage",
        ) { current ->
            when (current) {
                AddSheetPage.FORM -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Scrollable so the IME shrinking the sheet never
                        // squashes the date pickers together — content keeps
                        // its intrinsic height and scrolls instead.
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "事件名称",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "备注（可选）",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Entry rows: repeat rule + holiday quick presets, both
                    // as Miuix dropdowns (selection stays on the form; every
                    // repeat type except 不重复 then slides one level deeper
                    // into its own config page).
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OverlayDropdownPreference(
                            title = "重复",
                            summary = repeatSummary,
                            items = RepeatItems,
                            selectedIndex = repeatType,
                            showValue = false,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { selectRepeat(it) },
                        )
                        OverlayDropdownPreference(
                            title = "节假日",
                            summary = "快速添加节日，自动设置日期与重复",
                            items = AllHolidays.map { it.name },
                            // No persistent selection — picking a preset
                            // fills the form instead of marking a value.
                            selectedIndex = -1,
                            maxHeight = 320.dp,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { applyPreset(AllHolidays[it]) },
                        )
                    }

                    // Date picker — only meaningful for one-off events.
                    // Recurring events get their parameters (weekday /
                    // month-day / lunar date / time) from the config page.
                    if (repeatType == 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NumberPicker(
                                value = year,
                                onValueChange = { year = it },
                                range = (today.year - 50)..(today.year + 50),
                                label = { "${it}年" },
                                modifier = Modifier.weight(1.5f),
                            )
                            Text(
                                text = "/",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            NumberPicker(
                                value = month,
                                onValueChange = { month = it },
                                range = 1..12,
                                label = { "${it.toString().padStart(2, '0')}月" },
                                wrapAround = true,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "/",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            val dayRange = 1..safeLocalDate(year, month, 1).lengthOfMonth()
                            NumberPicker(
                                value = day.coerceAtMost(dayRange.last),
                                onValueChange = { day = it },
                                range = dayRange,
                                label = { "${it.toString().padStart(2, '0')}日" },
                                wrapAround = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        }
                    }

                    // One-line live preview (compact — keeps the form
                    // short enough to never need scrolling).
                    val previewText = when (repeatType) {
                        0 -> {
                            val selectedDate = safeLocalDate(year, month, day)
                            val diff = selectedDate.toEpochDay() - DateUtils.todayEpochDay()
                            when {
                                diff == 0L -> "就是今天"
                                diff > 0 -> "距离 ${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 还有 $diff 天"
                                else -> "距离 ${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 已过去 ${-diff} 天"
                            }
                        }
                        // Daily: the countdown is always "today" — just the rule.
                        1 -> repeatSummary
                        else -> {
                            val probe = com.chayewuu.hypermatter.data.CountdownEvent(
                                id = "preview",
                                title = "",
                                epochDay = DateUtils.todayEpochDay(),
                                note = null,
                                repeatType = repeatType,
                                lunarMonth = lunarMonth,
                                lunarDay = lunarDay,
                                repeatWeekday = if (repeatType == 2) weekday else null,
                                repeatMonthDay = if (repeatType == 3 || repeatType == 4) monthDay else null,
                                repeatYearMonth = if (repeatType == 4) yearMonth else null,
                                timeHour = if (repeatType == 1) hour else null,
                                timeMinute = if (repeatType == 1) minute else null,
                            )
                            val nextDate = DateUtils.effectiveDate(probe)
                            val diff = nextDate.toEpochDay() - DateUtils.todayEpochDay()
                            if (diff == 0L) "「$repeatSummary」 就是今天"
                            else "「$repeatSummary」 下次 ${nextDate.year}年${nextDate.monthValue}月${nextDate.dayOfMonth}日，还有 $diff 天"
                        }
                    }
                    Text(
                        text = previewText,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                AddSheetPage.CONFIG -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    when (repeatType) {
                        // 每天 → time of day
                        1 -> Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NumberPicker(
                                    value = hour,
                                    onValueChange = { hour = it },
                                    range = 0..23,
                                    label = { "%02d时".format(it) },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = ":",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                NumberPicker(
                                    value = minute,
                                    onValueChange = { minute = it },
                                    range = 0..59,
                                    label = { "%02d分".format(it) },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 每周 → weekday list
                        2 -> Card(modifier = Modifier.fillMaxWidth()) {
                            WeekdayItems.forEachIndexed { index, label ->
                                SelectRow(
                                    label = label,
                                    selected = weekday == index + 1,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        weekday = index + 1
                                        page = AddSheetPage.FORM
                                    },
                                )
                            }
                        }

                        // 每月 → day of month
                        3 -> Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NumberPicker(
                                    value = monthDay,
                                    onValueChange = { monthDay = it },
                                    range = 1..31,
                                    label = { "${it}日" },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 每年 → month + day
                        4 -> Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                NumberPicker(
                                    value = yearMonth,
                                    onValueChange = { yearMonth = it },
                                    range = 1..12,
                                    label = { "${it}月" },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "/",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                NumberPicker(
                                    value = monthDay,
                                    onValueChange = { monthDay = it },
                                    range = 1..31,
                                    label = { "${it}日" },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 每年农历 → lunar month + lunar day
                        5 -> Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val lm = lunarMonth ?: 1
                                val ld = lunarDay ?: 1
                                NumberPicker(
                                    value = lm,
                                    onValueChange = { lunarMonth = it },
                                    range = 1..12,
                                    label = { m ->
                                        if (m == 12) "腊月" else if (m == 11) "冬月" else "${DateUtils.lunarMonthName(m)}月"
                                    },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "/",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                NumberPicker(
                                    value = ld,
                                    onValueChange = { lunarDay = it },
                                    range = 1..30,
                                    label = { d -> DateUtils.lunarDayName(d) },
                                    wrapAround = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "· 调整完成后点击右上角 ✓ 返回",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** One selectable row with an Ok checkmark when selected (Miuix style). */
@Composable
private fun SelectRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected)
                MiuixTheme.colorScheme.primary
            else
                MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "已选择",
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** Safely construct a LocalDate, clamping the day if the selected day is invalid. */
private fun safeLocalDate(year: Int, month: Int, day: Int): LocalDate {
    val validDay = day.coerceIn(1, LocalDate.of(year, month, 1).lengthOfMonth())
    return LocalDate.of(year, month, validDay)
}

/** Feb 29 tolerant date construction for presets. */
private fun safeDateOf(year: Int, month: Int, day: Int): LocalDate {
    val validDay = day.coerceIn(1, LocalDate.of(year, month, 1).lengthOfMonth())
    return LocalDate.of(year, month, validDay)
}
