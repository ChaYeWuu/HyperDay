package com.chayewuu.hypermatter.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.DeerTrackerStore
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 🦌🦌记录器 page (小工具 → 🦌🦌记录器).
 *
 * A month calendar where long-pressing a past/today cell toggles that
 * day between 记录了(打了) and unrecorded — an unrecorded day simply
 * means 没打, no explicit marking needed (no tap cycling). Stats card
 * shows current streak / best streak / this month / total; today also
 * gets a quick toggle button. All data lives in [DeerTrackerStore].
 */
@Composable
fun DeerTrackerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    val today = remember { LocalDate.now() }
    var shownMonth by remember { mutableStateOf(YearMonth.now()) }
    var records by remember { mutableStateOf(DeerTrackerStore.getRecords(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun write(day: LocalDate, status: Int) {
        DeerTrackerStore.setRecord(context, day.toEpochDay(), status)
        records = DeerTrackerStore.getRecords(context)
    }

    fun toggleHit(day: LocalDate) {
        val current = records[day.toEpochDay()] ?: DeerTrackerStore.STATUS_NONE
        write(
            day,
            if (current == DeerTrackerStore.STATUS_HIT) DeerTrackerStore.STATUS_NONE
            else DeerTrackerStore.STATUS_HIT,
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "🦌🦌记录器",
                    color = if (barBackdrop != null) Color.Transparent
                    else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (barBackdrop != null)
                        Modifier.layerBackdrop(barBackdrop)
                    else Modifier
                ),
        ) {
            GlassCanvasRecorder(glassBackdrop)
            CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    // ---- Stats ----
                    item {
                        SmallTitle(text = "坚持统计")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                StatCell(
                                    value = DeerTrackerStore.currentStreak(records, today).toString(),
                                    label = "连续天数",
                                    modifier = Modifier.weight(1f),
                                )
                                StatCell(
                                    value = DeerTrackerStore.bestStreak(records).toString(),
                                    label = "最长连续",
                                    modifier = Modifier.weight(1f),
                                )
                                StatCell(
                                    value = DeerTrackerStore.monthHits(records, shownMonth).toString(),
                                    label = "本月记录",
                                    modifier = Modifier.weight(1f),
                                )
                                StatCell(
                                    value = DeerTrackerStore.totalHits(records).toString(),
                                    label = "累计记录",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // ---- Calendar ----
                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "打卡日历")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                // Month navigation: < 2026年9月 >
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = {
                                            shownMonth = shownMonth.minusMonths(1)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Back,
                                            contentDescription = "上个月",
                                            tint = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.rotate(180f),
                                        )
                                    }
                                    Text(
                                        text = shownMonth.format(
                                            DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA),
                                        ),
                                        style = MiuixTheme.textStyles.title3,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            shownMonth = shownMonth.plusMonths(1)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = "下个月",
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                // Weekday header (Monday-first, MIUI calendar
                                // convention).
                                val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    weekLabels.forEach { label ->
                                        Text(
                                            text = label,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 4.dp),
                                        )
                                    }
                                }

                                // Day grid: leading blanks (Monday-first
                                // offset) + month days, fixed 7 columns.
                                val cells = monthCells(shownMonth)
                                val rows = ceil(cells.size / 7f).toInt()
                                repeat(rows) { row ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        repeat(7) { col ->
                                            val day = cells.getOrNull(row * 7 + col)
                                            DayCell(
                                                day = day,
                                                today = today,
                                                status = day?.let {
                                                    records[it.toEpochDay()]
                                                } ?: DeerTrackerStore.STATUS_NONE,
                                                onLongPress = { d ->
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress,
                                                    )
                                                    toggleHit(d)
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }

                                // Legend.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LegendDot(
                                        color = MiuixTheme.colorScheme.primary,
                                        filled = true,
                                        label = "记录了",
                                    )
                                    Spacer(Modifier.size(16.dp))
                                    Text(
                                        text = "没打（未记录）",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = "长按日期记录当天，再长按取消",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                )
                            }
                        }
                    }

                    // ---- Today quick action ----
                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "今天")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            val todayHit =
                                records[today.toEpochDay()] == DeerTrackerStore.STATUS_HIT
                            Button(
                                onClick = { toggleHit(today) },
                                colors = if (todayHit)
                                    ButtonDefaults.buttonColorsPrimary()
                                else ButtonDefaults.buttonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Text(
                                    text = if (todayHit) "已记录 ✓" else "今天打了 🦌",
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }

                    // ---- Danger zone ----
                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "数据")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            ArrowPreference(
                                title = "清空记录",
                                summary = "删除全部打卡数据，不可恢复",
                                onClick = { showClearDialog = true },
                            )
                        }
                    }
                }
            }

            // Clear-all confirmation. Lives INSIDE the Scaffold content (an
            // overlay composed outside never shows) and opts out of root
            // rendering so it is not covered by this page.
            OverlayDialog(
                title = "清空打卡记录",
                summary = "确定要删除全部 🦌🦌 记录吗？此操作不可撤销。",
                show = showClearDialog,
                onDismissRequest = { showClearDialog = false },
                renderInRootScaffold = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showClearDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "清空",
                        onClick = {
                            DeerTrackerStore.clearAll(context)
                            records = emptyMap()
                            showClearDialog = false
                            Toast.makeText(context, "已清空全部记录", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Calendar cells for [month]: `null` for the Monday-first leading blanks,
 * then each day of the month. Trailing blanks are implicit (the grid
 * simply renders fewer cells in the last row).
 */
private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1 // Monday=1 → 0 blanks
    return List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
}

/** One day cell: number + state marker, long-press toggles the record. */
@Composable
private fun DayCell(
    day: LocalDate?,
    today: LocalDate,
    status: Int,
    onLongPress: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Spacer(modifier = modifier.aspectRatio(1f))
        return
    }
    val isFuture = day.isAfter(today)
    val isToday = day == today
    val textColor = when {
        isFuture -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
        status == DeerTrackerStore.STATUS_HIT -> MiuixTheme.colorScheme.onPrimary
        else -> MiuixTheme.colorScheme.onSurface
    }
    // Unrecorded (and legacy KEPT) days render as plain "没打".
    val cellColor = when (status) {
        DeerTrackerStore.STATUS_HIT -> MiuixTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .padding(3.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(cellColor)
            // Today ring when it carries no record of its own yet.
            .then(
                if (isToday && status != DeerTrackerStore.STATUS_HIT)
                    Modifier.border(
                        width = 1.5.dp,
                        color = MiuixTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                else Modifier
            )
            .then(
                if (isFuture) Modifier
                else Modifier.pointerInput(day) {
                    detectTapGestures(onLongPress = { onLongPress(day) })
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (status == DeerTrackerStore.STATUS_HIT || isToday)
                FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** One stat column: big number over a small label. */
@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.title2,
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/** Legend entry: filled/hollow dot + label. */
@Composable
private fun LegendDot(
    color: Color,
    filled: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .then(
                    if (filled) Modifier.background(color)
                    else Modifier.border(1.dp, color, CircleShape)
                ),
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
