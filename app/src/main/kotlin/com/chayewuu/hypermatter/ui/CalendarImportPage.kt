package com.chayewuu.hypermatter.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.data.CalendarSyncManager
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.data.SystemCalendarEvent
import com.chayewuu.hypermatter.data.SystemCalendarReader
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 系统日历 → APP 单向导入 (设置 → 数据 → 同步系统日历 → 从系统日历导入).
 *
 * Reads every foreign calendar (our own 「HyperDay 倒数日」 calendar is
 * skipped — those events came from the app), lets the user pick entries and
 * merges them in as countdown events. Imported events carry a `sourceRef`
 * marker, so importing the same system event again updates it instead of
 * piling up duplicates.
 */
@Composable
fun CalendarImportPage(onBack: () -> Unit) {
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    var permissionGranted by remember {
        mutableStateOf(CalendarSyncManager.hasCalendarPermission(context))
    }
    var systemEvents by remember { mutableStateOf<List<SystemCalendarEvent>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }

    // System events already mirrored into the app: shown as imported, not
    // offered for a first-time import (re-importing only updates them).
    val importedRefs = remember(events) {
        events.mapNotNull { it.sourceRef }.toSet()
    }
    val candidates = systemEvents.filter { it.sourceRef !in importedRefs }
    val alreadyImported = systemEvents.size - candidates.size

    suspend fun loadEvents() {
        loading = true
        val list = withContext(Dispatchers.IO) {
            runCatching { SystemCalendarReader.readEvents(context) }
                .getOrDefault(emptyList())
        }
        systemEvents = list
        selected = selected.intersect(list.map { it.sourceRef }.toSet())
        loading = false
    }

    LaunchedEffect(permissionGranted, reloadTick) {
        if (permissionGranted) loadEvents()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionGranted = grants.values.all { it }
        if (!permissionGranted) {
            Toast.makeText(context, "需要日历权限才能读取系统日程", Toast.LENGTH_SHORT).show()
        }
    }

    fun runImport() {
        showImportDialog = false
        val picked = candidates.filter { it.sourceRef in selected }
        if (picked.isEmpty()) return
        val (added, updated) = viewModel.importSystemEvents(
            SystemCalendarReader.toCountdownEvents(picked),
        )
        selected = emptySet()
        Toast.makeText(
            context,
            if (updated > 0) "已导入 $added 个，更新 $updated 个" else "已导入 $added 个日程",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "从系统日历导入",
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
            // Glass canvas recorder must stay a sibling of the glass surfaces.
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
                    if (!permissionGranted) {
                        item {
                            SmallTitle(text = "读取系统日历")
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            ) {
                                ArrowPreference(
                                    title = "授予日历权限",
                                    summary = "读取系统日历中的日程，导入为倒数日",
                                    onClick = { permissionLauncher.launch(calendarPermissions) },
                                )
                            }
                        }
                    } else {
                        item {
                            SmallTitle(text = "导入")
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            ) {
                                ArrowPreference(
                                    title = "导入已选日程",
                                    summary = when {
                                        loading -> "正在读取系统日历…"
                                        candidates.isEmpty() -> "没有可导入的日程"
                                        else -> "已选 ${selected.size}/${candidates.size} 个日程"
                                    },
                                    enabled = !loading && selected.isNotEmpty(),
                                    onClick = { showImportDialog = true },
                                )
                                ArrowPreference(
                                    title = "重新读取",
                                    summary = if (alreadyImported > 0) {
                                        "已导入 $alreadyImported 个系统日程"
                                    } else {
                                        "刷新系统日历中的日程列表"
                                    },
                                    enabled = !loading,
                                    onClick = { reloadTick++ },
                                )
                            }
                        }

                        if (loading) {
                            item {
                                Text(
                                    text = "正在读取系统日历…",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.padding(
                                        start = 28.dp,
                                        top = 16.dp,
                                        bottom = 8.dp,
                                    ),
                                )
                            }
                        } else if (candidates.isEmpty()) {
                            item {
                                Text(
                                    text = if (systemEvents.isEmpty()) {
                                        "系统日历里还没有日程"
                                    } else {
                                        "系统日历中的日程都已导入"
                                    },
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2,
                                    modifier = Modifier.padding(
                                        start = 28.dp,
                                        top = 16.dp,
                                        bottom = 8.dp,
                                    ),
                                )
                            }
                        } else {
                            // One group per system calendar, one check row per event.
                            val groups = candidates.groupBy { it.calendarName }
                            groups.forEach { (calendarName, groupEvents) ->
                                item(key = "group_$calendarName") {
                                    Spacer(Modifier.height(12.dp))
                                    SmallTitle(text = calendarName)
                                }
                                item(key = "card_$calendarName") {
                                    LiquidGlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                    ) {
                                        val allState = when {
                                            groupEvents.all { it.sourceRef in selected } ->
                                                ToggleableState.On
                                            groupEvents.none { it.sourceRef in selected } ->
                                                ToggleableState.Off
                                            else -> ToggleableState.Indeterminate
                                        }
                                        ImportCheckRow(
                                            title = "全选",
                                            summary = "${groupEvents.size} 个日程",
                                            state = allState,
                                            onToggle = { on ->
                                                selected = if (on) {
                                                    selected + groupEvents.map { it.sourceRef }
                                                } else {
                                                    selected - groupEvents.map { it.sourceRef }.toSet()
                                                }
                                            },
                                        )
                                        groupEvents.forEach { event ->
                                            ImportCheckRow(
                                                title = event.title,
                                                summary = buildString {
                                                    append(DateUtils.formatDate(event.date.toEpochDay()))
                                                    repeatLabelOf(event.repeatType)?.let {
                                                        append(" · $it")
                                                    }
                                                },
                                                checked = event.sourceRef in selected,
                                                onToggle = { on ->
                                                    selected = if (on) {
                                                        selected + event.sourceRef
                                                    } else {
                                                        selected - event.sourceRef
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Confirmation must live inside THIS Scaffold's content lambda and
            // opt out of the root scaffold (this page covers it).
            if (showImportDialog) {
                OverlayDialog(
                    title = "导入日程",
                    summary = "将把已选的 ${selected.size} 个系统日程导入为倒数日，" +
                        "已导入过的日程会更新而不是重复添加。确定继续吗？",
                    show = true,
                    onDismissRequest = { showImportDialog = false },
                    renderInRootScaffold = false,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showImportDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "导入",
                            onClick = { runImport() },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** "每天/每周/每月/每年" for an imported system event (null = one-off). */
private fun repeatLabelOf(repeatType: Int): String? = when (repeatType) {
    1 -> "每天"
    2 -> "每周"
    3 -> "每月"
    4 -> "每年"
    else -> null
}

/** One checkable import row: [Checkbox] (possibly tristate) + title + summary. */
@Composable
private fun ImportCheckRow(
    title: String,
    summary: String,
    state: ToggleableState,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(state != ToggleableState.On) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = state,
            onClick = { onToggle(state != ToggleableState.On) },
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

/** Boolean overload of [ImportCheckRow]. */
@Composable
private fun ImportCheckRow(
    title: String,
    summary: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ImportCheckRow(
        title = title,
        summary = summary,
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onToggle = onToggle,
    )
}
