package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.CountdownEvent
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.widget.CardWidget
import com.chayewuu.hypermatter.widget.WidgetPrefs
import com.chayewuu.hypermatter.widget.eventDateLine
import com.chayewuu.hypermatter.widget.todayLine
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

    // Widget feed: upcoming soonest first, then past most-recent (matches
    // the list/minimal widget providers).
    val feed = upcoming + events
        .filter { DateUtils.isPastEvent(it) }
        .sortedByDescending { DateUtils.effectiveEpochDay(it) }

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
                                        // Full 2x2 cell (MIUI cells are taller
                                        // than wide, ~0.82 w/h).
                                        .aspectRatio(0.82f),
                                )
                                PreviewCaption("卡片 2×2")
                            }
                            Spacer(Modifier.width(10.dp))
                            // Bottom-aligned so the minimal widget's bottom
                            // edge lines up with the card (home-screen style:
                            // card top-left, minimal bottom-right).
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                MinimalWidgetPreview(
                                    event = feed.firstOrNull(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Full 2x1 cell (~1.79 w/h).
                                        .aspectRatio(1.79f),
                                )
                                PreviewCaption("极简 2×1")
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        ListWidgetPreview(
                            events = feed.take(4),
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

/** 距离/过去 tag pill shared by all widget previews.
 *  Defaults mirror the small row pill (10sp, 6/2dp); the card widget's
 *  pill is bigger (11sp, 8/3dp) — see widget_card.xml vs widget_list_row.xml. */
@Composable
private fun WidgetTagPill(
    event: CountdownEvent?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    horizontal: Dp = 6.dp,
    vertical: Dp = 2.dp,
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = horizontal, vertical = vertical),
    ) {
        Text(
            text = if (event == null) "距离"
            else if (DateUtils.isPastEvent(event)) "过去" else "距离",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = fontSize,
        )
    }
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
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
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
            .padding(start = 14.dp, top = 12.dp, end = 18.dp, bottom = 10.dp),
    ) {
        // Mirrors widget_card.xml: 11sp pill (8/3dp), 18sp title,
        // 12sp date, 42sp day number centered in the remaining space.
        WidgetTagPill(event, fontSize = 11.sp, horizontal = 8.dp, vertical = 3.dp)
        Box(Modifier.padding(start = 4.dp, top = 6.dp)) { PreviewHeader(event) }
        CenteredDayNumber(event, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** "今日 · M月d日 周X" right-aligned label shared by card/minimal previews. */
@Composable
private fun TodayLabel() {
    Text(
        text = remember { todayLine() },
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Title + date line shared by the card preview. */
@Composable
private fun PreviewHeader(event: CountdownEvent?) {
    Text(
        text = event?.title ?: "还没有倒数日",
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = if (event == null) "点击打开 HyperDay 添加"
        else eventDateLine(event),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Header: calendar glyph + today's date (matches the real widget).
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.widget_calendar_icon),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
            )
            Text(
                text = remember { todayLine() },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        if (events.isEmpty()) {
            Text(
                text = "还没有倒数日",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            // Mirrors widget_list_row.xml: each row carries 8dp vertical
            // padding, title weight(1f) fills, date wraps, 20sp day number.
            Column {
                events.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetTagPill(event)
                        Text(
                            text = event.title,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                        )
                        Text(
                            text = eventDateLine(event),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = DateUtils.dayNumber(event).toString(),
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp),
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

@Composable
private fun MinimalWidgetPreview(
    event: CountdownEvent?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widgetPreviewSurface()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        // Top row: tag pill (left) + today's date (right).
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetTagPill(event)
            Spacer(Modifier.weight(1f))
            TodayLabel()
        }
        // Mirrors widget_minimal.xml: the bottom row fills the remaining
        // space with its contents vertically centered; 18sp title,
        // 24sp day number, 11sp unit.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event?.title ?: "还没有倒数日",
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Text(
                text = event?.let { DateUtils.dayNumber(it).toString() } ?: "--",
                color = MiuixTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "天",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 3.dp),
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
