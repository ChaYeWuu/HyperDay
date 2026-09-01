package com.chayewuu.hypermatter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.LunarCalendar
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Repeat options. Index == repeatType value. */
private val RepeatItems = listOf("不重复", "每天", "每周", "每月", "每年", "每年农历")

/** Which sub-page of the add sheet is visible. */
private enum class AddSheetPage { FORM, REPEAT, HOLIDAY }

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

/**
 * Bottom sheet for adding a new countdown event.
 * The form itself stays compact (no scrolling); tapping the 重复/节假日
 * rows slides in a dedicated selection page inside the sheet.
 */
@Composable
fun AddEventBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        epochDay: Long,
        note: String?,
        repeatType: Int,
        lunarMonth: Int?,
        lunarDay: Int?,
    ) -> Unit,
) {
    val today = DateUtils.today()
    val view = LocalView.current

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var repeatType by remember { mutableIntStateOf(0) }
    var lunarMonth by remember { mutableStateOf<Int?>(null) }
    var lunarDay by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableStateOf(AddSheetPage.FORM) }

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
        page = AddSheetPage.FORM
    }

    fun selectRepeat(index: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        repeatType = index
        if (index == 5) {
            // Yearly-lunar anchored on the currently selected solar date.
            val lunar = LunarCalendar.solarToLunar(safeLocalDate(year, month, day))
            lunarMonth = lunar.month
            lunarDay = lunar.day
        } else {
            lunarMonth = null
            lunarDay = null
        }
        page = AddSheetPage.FORM
    }

    val repeatSummary = when (repeatType) {
        0 -> "不重复"
        5 -> {
            val lm = lunarMonth ?: 1
            val ld = lunarDay ?: 1
            "每年农历${if (lm == 12) "腊月" else "${lm}月"}${ld}日"
        }
        else -> RepeatItems[repeatType]
    }

    val sheetTitle = when (page) {
        AddSheetPage.FORM -> "添加倒数日"
        AddSheetPage.REPEAT -> "选择重复"
        AddSheetPage.HOLIDAY -> "选择节假日"
    }

    OverlayBottomSheet(
        title = sheetTitle,
        show = show,
        startAction = {
            IconButton(
                onClick = {
                    if (page != AddSheetPage.FORM) {
                        page = AddSheetPage.FORM
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
                                onConfirm(title, date.toEpochDay(), note, repeatType, lunarMonth, lunarDay)
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
        onDismissRequest = onDismiss,
    ) {
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

                    // Entry rows: repeat rule + holiday quick presets.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = "重复",
                            summary = repeatSummary,
                            onClick = { page = AddSheetPage.REPEAT },
                        )
                        ArrowPreference(
                            title = "节假日",
                            summary = "快速添加节日，自动设置日期与重复",
                            onClick = { page = AddSheetPage.HOLIDAY },
                        )
                    }

                    // Date picker — three NumberPickers side by side
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

                    // One-line live preview (compact — keeps the form
                    // short enough to never need scrolling).
                    val selectedDate = safeLocalDate(year, month, day)
                    val nextDate = if (repeatType != 0) {
                        val probe = com.chayewuu.hypermatter.data.CountdownEvent(
                            id = "preview",
                            title = "",
                            epochDay = selectedDate.toEpochDay(),
                            note = null,
                            repeatType = repeatType,
                            lunarMonth = lunarMonth,
                            lunarDay = lunarDay,
                        )
                        DateUtils.effectiveDate(probe)
                    } else {
                        selectedDate
                    }
                    val diff = nextDate.toEpochDay() - DateUtils.todayEpochDay()
                    val dayText = when {
                        diff == 0L -> "就是今天"
                        diff > 0 -> "距离 ${nextDate.year}年${nextDate.monthValue}月${nextDate.dayOfMonth}日 还有 $diff 天"
                        else -> "距离 ${nextDate.year}年${nextDate.monthValue}月${nextDate.dayOfMonth}日 已过去 ${-diff} 天"
                    }
                    Text(
                        text = if (repeatType != 0) "「$repeatSummary」 $dayText" else dayText,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                AddSheetPage.REPEAT -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        RepeatItems.forEachIndexed { index, label ->
                            SelectRow(
                                label = label,
                                selected = repeatType == index,
                                onClick = { selectRepeat(index) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "· 重复事件始终显示到下一次发生的倒计时，不会归入「已经过去」",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                AddSheetPage.HOLIDAY -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                ) {
                    SmallTitle(text = "农历节日")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LunarHolidays.forEach { preset ->
                            HolidayRow(preset = preset) { applyPreset(preset) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(text = "公历节日")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SolarHolidays.forEach { preset ->
                            HolidayRow(preset = preset) { applyPreset(preset) }
                        }
                    }
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

/** One holiday preset row: name + repeat rule on the right. */
@Composable
private fun HolidayRow(
    preset: HolidayPreset,
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
            text = preset.name,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (preset.repeatType == 5) "每年农历" else "每年",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
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
