package com.chayewuu.hypermatter.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.ui.glass.GlassFab
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalCategoryStore
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePage(
    contentPadding: PaddingValues,
    onOpenEvent: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    showFab: Boolean = true,
) {
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val view = LocalView.current
    val categoryStore = LocalCategoryStore.current
    val categories by categoryStore.categories.collectAsState()

    // null = 全部 (no category filter).
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val upcoming = remember(events, selectedCategory) {
        // Recurring events always target their next occurrence and never
        // fall into the "past" bucket; sorting uses the effective date.
        events
            .filter { !DateUtils.isPastEvent(it) }
            .filter { selectedCategory == null || it.category == selectedCategory }
            .sortedBy { DateUtils.effectiveEpochDay(it) }
    }
    val past = remember(events, selectedCategory) {
        events
            .filter { DateUtils.isPastEvent(it) }
            .filter { selectedCategory == null || it.category == selectedCategory }
            .sortedByDescending { it.epochDay }
    }

    /** Category display name for a card pill (null = uncategorized). */
    fun categoryName(id: String?): String? = categoryStore.byId(id)?.name

    // Pending deletion: set by the card's long-press menu, consumed by the
    // confirmation dialog below.
    var deleteTarget by remember { mutableStateOf<CountdownEvent?>(null) }
    // Pending edit: set by the card's long-press menu, opens the add sheet
    // in edit mode (prefilled).
    var editTarget by remember { mutableStateOf<CountdownEvent?>(null) }

    fun requestDelete(event: CountdownEvent) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        deleteTarget = event
    }

    // Content extends under the blurred top bar (frosted when scrolled);
    // the bar's height is fed to the list as content padding instead.
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Miuix overscroll bounce + boundary haptic
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            // Category filter chips (全部 + every category), HyperOS pill
            // style; only shown once events exist.
            if (events.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryChip(
                            label = "全部",
                            selected = selectedCategory == null,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedCategory = null
                            },
                        )
                        categories.forEach { category ->
                            CategoryChip(
                                label = category.name,
                                selected = selectedCategory == category.id,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    selectedCategory = category.id
                                },
                            )
                        }
                    }
                }
            }

            if (upcoming.isEmpty() && past.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = MiuixIcons.Months,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "还没有倒数日",
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "点击右下角 + 添加",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }

            if (upcoming.isNotEmpty()) {
                item { SmallTitle(text = "即将到来") }
                items(upcoming, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        categoryName = categoryName(event.category),
                        onEdit = { editTarget = event },
                        onDelete = { requestDelete(event) },
                        onOpen = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenEvent(event.id)
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    )
                }
            }

            if (past.isNotEmpty()) {
                item { SmallTitle(text = "已经过去") }
                items(past, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        categoryName = categoryName(event.category),
                        onEdit = { editTarget = event },
                        onDelete = { requestDelete(event) },
                        onOpen = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenEvent(event.id)
                        },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }

        // FAB: lifted above the bottom NavigationBar via outer contentPadding.
        // In liquid-glass mode this becomes a primary-tinted glass circle —
        // or is hidden entirely (the + moves into the glass bottom bar).
        if (showFab) {
            GlassFab(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAddClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "添加倒数日",
                    tint = Color.White,
                )
            }
        }
    }

    // Miuix-style delete confirmation (same pattern as the settings
    // clear-all dialog): cancel / delete side by side.
    deleteTarget?.let { target ->
        OverlayDialog(
            title = "删除倒数日",
            summary = "确定要删除「${target.title}」吗？此操作不可撤销。",
            show = true,
            onDismissRequest = { deleteTarget = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { deleteTarget = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    onClick = {
                        viewModel.deleteEvent(target.id)
                        deleteTarget = null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Edit sheet: the add sheet in edit mode, prefilled with the target's
    // current values. Confirm writes back with updateEvent — a copy of the
    // fresh event so wallpaper / font customizations are preserved.
    editTarget?.let { target ->
        AddEventBottomSheet(
            show = true,
            editEvent = target,
            onDismiss = { editTarget = null },
            onConfirm = { title, epochDay, note, repeatType,
                          lunarMonth, lunarDay, repeatWeekday, repeatMonthDay,
                          repeatYearMonth, timeHour, timeMinute, category ->
                val fresh = viewModel.events.value.firstOrNull { it.id == target.id } ?: target
                viewModel.updateEvent(
                    fresh.copy(
                        title = title.trim(),
                        epochDay = epochDay,
                        note = note?.trim()?.ifBlank { null },
                        repeatType = repeatType.takeIf { it != 0 },
                        lunarMonth = lunarMonth,
                        lunarDay = lunarDay,
                        repeatWeekday = repeatWeekday,
                        repeatMonthDay = repeatMonthDay,
                        repeatYearMonth = repeatYearMonth,
                        timeHour = timeHour,
                        timeMinute = timeMinute,
                        category = category,
                    )
                )
                editTarget = null
            },
        )
    }
}

@Composable
private fun EventCard(
    event: CountdownEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
) {
    val dayNum = DateUtils.dayNumber(event)
    val isPast = DateUtils.isPastEvent(event)
    val effectiveDay = DateUtils.effectiveEpochDay(event)
    val dateStr = DateUtils.formatDate(effectiveDay)
    val weekday = DateUtils.weekdayLabel(effectiveDay)
    val repeatLabel = DateUtils.repeatLabel(event)
    // Date line: recurring events show their repeat rule + next occurrence.
    val dateLine = if (repeatLabel.isNotBlank()) "$repeatLabel · $dateStr" else "$dateStr $weekday"

    val view = LocalView.current
    var showMenu by remember { mutableStateOf(false) }
    // Tap the day number to toggle 天数 ↔ 年月天 conversion.
    var showPeriod by remember { mutableStateOf(false) }
    val dayColor = if (isPast)
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    else
        MiuixTheme.colorScheme.primary

    Box(modifier = modifier) {
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp),
            onClick = onOpen,
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showMenu = true
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: title + category pill + date + note + countdown description
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = event.title,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (categoryName != null) {
                            Spacer(Modifier.width(6.dp))
                            CategoryPill(name = categoryName)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dateLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    if (!event.note.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = event.note,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = DateUtils.describe(event),
                        color = if (isPast)
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else
                            MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }

                // Right: big day number. Tap toggles the 天数 ↔ 年月天
                // conversion (e.g. 400 天 ↔ 1年1月5天).
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showPeriod = !showPeriod
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (showPeriod) {
                        Text(
                            text = DateUtils.periodSpan(event),
                            color = dayColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = dayNum.toString(),
                            color = dayColor,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "天",
                            color = dayColor,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Long-press context menu, Miuix list popup anchored to this card.
        OverlayListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.BottomStart,
            onDismissRequest = { showMenu = false },
        ) {
            ListPopupColumn {
                MenuRow(
                    icon = MiuixIcons.Edit,
                    label = "编辑",
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                )
                MenuRow(
                    icon = MiuixIcons.Delete,
                    label = "删除",
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** HyperOS-style filter pill for the category row. */
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** Small category tag shown next to an event card's title. */
@Composable
private fun CategoryPill(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** One row of the card long-press context menu. */
@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
        )
    }
}
