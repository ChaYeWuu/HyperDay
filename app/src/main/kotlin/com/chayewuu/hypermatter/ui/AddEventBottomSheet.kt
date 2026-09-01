package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Repeat options shown as chips. Index == repeatType value. */
private val RepeatItems = listOf("不重复", "每天", "每周", "每月", "每年", "每年农历")

/**
 * Holiday presets: name -> (repeatType, lunarMonth?, lunarDay?, month, day).
 * Lunar entries anchor via lunar date; solar entries via month/day.
 */
private data class HolidayPreset(
    val name: String,
    val repeatType: Int,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val month: Int,
    val day: Int,
)

private val HolidayPresets = listOf(
    // Lunar festivals
    HolidayPreset("春节", 5, lunarMonth = 1, lunarDay = 1, month = 1, day = 1),
    HolidayPreset("元宵节", 5, lunarMonth = 1, lunarDay = 15, month = 1, day = 15),
    HolidayPreset("端午节", 5, lunarMonth = 5, lunarDay = 5, month = 5, day = 5),
    HolidayPreset("七夕节", 5, lunarMonth = 7, lunarDay = 7, month = 7, day = 7),
    HolidayPreset("中秋节", 5, lunarMonth = 8, lunarDay = 15, month = 8, day = 15),
    HolidayPreset("重阳节", 5, lunarMonth = 9, lunarDay = 9, month = 9, day = 9),
    HolidayPreset("腊八节", 5, lunarMonth = 12, lunarDay = 8, month = 12, day = 8),
    // Solar festivals
    HolidayPreset("元旦", 4, month = 1, day = 1),
    HolidayPreset("情人节", 4, month = 2, day = 14),
    HolidayPreset("劳动节", 4, month = 5, day = 1),
    HolidayPreset("儿童节", 4, month = 6, day = 1),
    HolidayPreset("国庆节", 4, month = 10, day = 1),
    HolidayPreset("圣诞节", 4, month = 12, day = 25),
)

/**
 * Bottom sheet for adding a new countdown event.
 * Supports one-off events plus recurring ones (daily/weekly/monthly/yearly
 * and yearly-lunar festivals) with holiday quick presets.
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

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var repeatType by remember { mutableIntStateOf(0) }
    // Lunar anchor for "yearly lunar" repeats chosen via presets
    var lunarMonth by remember { mutableStateOf<Int?>(null) }
    var lunarDay by remember { mutableStateOf<Int?>(null) }

    fun applyPreset(preset: HolidayPreset) {
        title = preset.name
        repeatType = preset.repeatType
        lunarMonth = preset.lunarMonth
        lunarDay = preset.lunarDay
        // Anchor date: next occurrence of this holiday
        val anchor = when (preset.repeatType) {
            5 -> LunarCalendar.nextLunarAnniversary(
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
    }

    OverlayBottomSheet(
        title = "添加倒数日",
        show = show,
        startAction = {
            IconButton(onClick = onDismiss) {
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
                    if (title.isNotBlank()) {
                        val date = safeLocalDate(year, month, day)
                        onConfirm(title, date.toEpochDay(), note, repeatType, lunarMonth, lunarDay)
                    }
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "确定",
                    tint = if (title.isNotBlank())
                        MiuixTheme.colorScheme.primary
                    else
                        MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title input
            TextField(
                value = title,
                onValueChange = { title = it },
                label = "事件名称",
                modifier = Modifier.fillMaxWidth(),
            )

            // Note input
            TextField(
                value = note,
                onValueChange = { note = it },
                label = "备注（可选）",
                modifier = Modifier.fillMaxWidth(),
            )

            // Repeat selection chips
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text(
                        text = "重复",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RepeatItems.forEachIndexed { index, label ->
                            val selected = repeatType == index
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = if (selected)
                                    MiuixTheme.colorScheme.primary
                                else
                                    MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected)
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else
                                            MiuixTheme.colorScheme.surfaceContainer
                                    )
                                    .clickable {
                                        repeatType = index
                                        if (index != 5) {
                                            // Lunar anchor only valid for lunar repeats
                                            lunarMonth = null
                                            lunarDay = null
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (repeatType == 5 && lunarMonth == null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "提示：每年农历按选定公历日期换算农历；可用下方节假日快捷选择",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }

            // Holiday quick presets
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text(
                        text = "节假日",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HolidayPresets.forEach { preset ->
                            Text(
                                text = preset.name,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .clickable { applyPreset(preset) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
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

            // Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text(
                        text = "预览",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(Modifier.height(8.dp))
                    val selectedDate = safeLocalDate(year, month, day)
                    val repeatLabel = if (repeatType == 0) "" else RepeatItems[repeatType]
                    // Effective next occurrence for the preview
                    val nextDate = if (repeatType == 5 && lunarMonth != null) {
                        LunarCalendar.nextLunarAnniversary(lunarMonth!!, lunarDay ?: 1, today)
                    } else if (repeatType in 1..4) {
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
                        text = if (title.isNotBlank()) title else "（未命名事件）",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (repeatLabel.isNotBlank()) "「$repeatLabel」 $dayText" else dayText,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }

            // Hint
            Text(
                text = "· 重复事件始终显示到下一次发生的倒计时，不会归入「已经过去」",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(horizontal = 16.dp),
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
