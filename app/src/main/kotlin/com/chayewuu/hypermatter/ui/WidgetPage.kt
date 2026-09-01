package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.widget.CardWidget
import com.chayewuu.hypermatter.widget.WidgetPrefs
import com.chayewuu.hypermatter.widget.eventDateLine
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Widget preview & binding page (设置 → 小部件).
 *
 *  - 小部件预览: live Compose re-drawings of all three home-screen widgets
 *    (卡片 2×2 / 列表 4×2 / 极简 2×1) using the current event data, so the
 *    user can see what each looks like before adding it.
 *  - 卡片事件绑定: pick which event the card widget is pinned to
 *    (persisted via [WidgetPrefs], refreshed via [CardWidget.push]).
 */
@Composable
fun WidgetPage(
    onBack: () -> Unit,
) {
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current

    // Selection state mirrors WidgetPrefs so rows re-render immediately;
    // null = auto (nearest event).
    var selectedId by remember { mutableStateOf(WidgetPrefs.getSingleEventId(context)) }

    val upcoming = events
        .filter { !DateUtils.isPastEvent(it) }
        .sortedBy { DateUtils.effectiveEpochDay(it) }

    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "小部件",
                    color = if (barBackdrop != null)
                        Color.Transparent
                    else
                        MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
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
                    else
                        Modifier
                ),
        ) {
            // Flat-canvas recorder for the glass cards: a sibling with no
            // glass inside it (glass surfaces must never be part of the
            // subtree recording their own sample — infinite render nesting).
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
                    item {
                        SmallTitle(text = "小部件预览")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CardWidgetPreview(
                                    event = selectedEvent(events, selectedId),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                )
                                PreviewCaption("卡片 2×2")
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MinimalWidgetPreview(
                                    event = upcoming.firstOrNull(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f),
                                )
                                PreviewCaption("极简 2×1")
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        ListWidgetPreview(
                            events = upcoming.take(4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .aspectRatio(2f),
                        )
                        PreviewCaption("列表 4×2")
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "卡片事件绑定")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            // Auto row: nearest upcoming (or nearest past).
                            SelectRow(
                                title = "自动",
                                summary = "跟随最近的倒数日",
                                selected = selectedId == null,
                                onClick = {
                                    selectedId = null
                                    WidgetPrefs.setSingleEventId(context, null)
                                    CardWidget.push(context)
                                },
                            )
                            events.forEach { event ->
                                SelectRow(
                                    title = event.title,
                                    summary = DateUtils.describe(event),
                                    selected = selectedId == event.id,
                                    onClick = {
                                        selectedId = event.id
                                        WidgetPrefs.setSingleEventId(context, event.id)
                                        CardWidget.push(context)
                                    },
                                )
                            }
                            if (events.isEmpty()) {
                                Text(
                                    text = "还没有倒数日，先去首页添加一个吧",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }
                        Text(
                            text = "「倒数日 · 卡片」小部件会固定显示所选事件，" +
                                "左上角标注距离或过去",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The pinned event if it still exists, else the auto pick. */
private fun selectedEvent(
    events: List<CountdownEvent>,
    selectedId: String?,
): CountdownEvent? {
    return selectedId?.let { id -> events.firstOrNull { it.id == id } }
        ?: events.filter { !DateUtils.isPastEvent(it) }
            .minByOrNull { DateUtils.effectiveEpochDay(it) }
        ?: events.maxByOrNull { DateUtils.effectiveEpochDay(it) }
}

// ---------------------------------------------------------------------------
// Widget previews — Compose re-drawings of the RemoteViews layouts, styled
// with Miuix tokens (surfaceContainer card, primary numbers, secondary text).
// ---------------------------------------------------------------------------

/** Shared card surface: 24dp rounded, surfaceContainer background. */
@Composable
private fun Modifier.widgetPreviewSurface(): Modifier = this
    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
    .background(MiuixTheme.colorScheme.surfaceContainer)

@Composable
private fun PreviewCaption(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

/** Big centered day number used by the card & single previews. */
@Composable
private fun CenteredDayNumber(
    event: CountdownEvent?,
    modifier: Modifier = Modifier,
) {
    if (event == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "--",
                color = MiuixTheme.colorScheme.primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = DateUtils.dayNumber(event).toString(),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "天",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun CardWidgetPreview(
    event: CountdownEvent?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widgetPreviewSurface()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Tag pill: 距离 / 过去
        Box(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = if (event == null) "距离"
                else if (DateUtils.isPastEvent(event)) "过去" else "距离",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        PreviewHeader(event)
        CenteredDayNumber(event, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Title + date line shared by the card preview. */
@Composable
private fun PreviewHeader(event: CountdownEvent?) {
    Text(
        text = event?.title ?: "还没有倒数日",
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = if (event == null) "点击打开 HyperDay 添加"
        else eventDateLine(event),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ListWidgetPreview(
    events: List<CountdownEvent>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widgetPreviewSurface()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "即将到来",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Text(
                text = "还没有倒数日",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                events.forEach { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = event.title,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = shortDate(event),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = DateUtils.dayNumber(event).toString(),
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "天",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "6月15日" — matches the widget list row date format. */
private fun shortDate(event: CountdownEvent): String {
    val day = DateUtils.effectiveEpochDay(event)
    val date = java.time.LocalDate.ofEpochDay(day)
    return "${date.monthValue}月${date.dayOfMonth}日"
}

@Composable
private fun MinimalWidgetPreview(
    event: CountdownEvent?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widgetPreviewSurface()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = event?.title ?: "还没有倒数日",
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = event?.let { DateUtils.dayNumber(it).toString() } ?: "--",
                color = MiuixTheme.colorScheme.primary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "天",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** One selectable event row (auto row or a pinned event). */
@Composable
private fun SelectRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "已选择",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
