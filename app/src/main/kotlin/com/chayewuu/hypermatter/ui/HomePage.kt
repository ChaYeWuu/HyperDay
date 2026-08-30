package com.chayewuu.hypermatter.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.ui.glass.GlassFab
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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

    val upcoming = remember(events) {
        events.filter { !DateUtils.isPastEvent(it) }.sortedBy { it.epochDay }
    }
    val past = remember(events) {
        events.filter { DateUtils.isPastEvent(it) }.sortedByDescending { it.epochDay }
    }

    // Pending deletion: set by the card's delete button, consumed by the
    // confirmation dialog below.
    var deleteTarget by remember { mutableStateOf<CountdownEvent?>(null) }

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
}

@Composable
private fun EventCard(
    event: CountdownEvent,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayNum = DateUtils.dayNumber(event)
    val isPast = DateUtils.isPastEvent(event)
    val dateStr = DateUtils.formatDate(event.epochDay)
    val weekday = DateUtils.weekdayLabel(event.epochDay)

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: title row (with trailing delete) + date + note
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
                    Spacer(Modifier.width(4.dp))
                    // Delete aligned with the title line — compact circular
                    // touch target with a circle-bounded ripple.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "删除",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$dateStr $weekday",
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

            // Right: big day number, vertically centered
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = dayNum.toString(),
                        color = if (isPast)
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else
                            MiuixTheme.colorScheme.primary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "天",
                        color = if (isPast)
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else
                            MiuixTheme.colorScheme.primary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
