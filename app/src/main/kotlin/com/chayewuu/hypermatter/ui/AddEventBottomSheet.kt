package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.DateUtils
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

/**
 * Bottom sheet for adding a new countdown event.
 * Uses NumberPicker for year/month/day selection.
 */
@Composable
fun AddEventBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, epochDay: Long, note: String?) -> Unit,
) {
    val today = DateUtils.today()

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }

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
                        onConfirm(title, date.toEpochDay(), note)
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
            val selectedDate = safeLocalDate(year, month, day)
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
                    val diff = selectedDate.toEpochDay() - DateUtils.todayEpochDay()
                    val dayText = when {
                        diff == 0L -> "就是今天"
                        diff > 0 -> "距离 ${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 还有 $diff 天"
                        else -> "距离 ${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 已过去 ${-diff} 天"
                    }
                    Text(
                        text = if (title.isNotBlank()) title else "（未命名事件）",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dayText,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }

            // Hint
            Text(
                text = "· 日期在今天之前会归入「已经过去」，在今天及之后归入「即将到来」",
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
