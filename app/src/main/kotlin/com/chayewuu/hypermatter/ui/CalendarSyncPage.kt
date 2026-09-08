package com.chayewuu.hypermatter.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import com.chayewuu.hypermatter.data.CalendarSyncManager
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 同步系统日历 settings page (设置 → 数据 → 同步系统日历).
 *
 * A free selection of which countdown events get synced into the system
 * calendar: 「全选」tristate row + one check row per event (persisted via
 * [CalendarSyncManager.getSelectedIds], null = all). 「立即同步」does a
 * full rebuild of the local calendar with exactly the selected events;
 * 「移除日历同步」deletes the calendar itself.
 */
@Composable
fun CalendarSyncPage(onBack: () -> Unit) {
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    // Explicit selection (null = all events, never customized).
    var selectedIds by remember {
        mutableStateOf(CalendarSyncManager.getSelectedIds(context))
    }
    // Bumped after each sync so the summary line re-reads the prefs.
    var syncTick by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }

    // The effective selection: stored set, or every event id when null.
    val effective: Set<String> = selectedIds ?: events.map { it.id }.toSet()

    val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    fun runSync() {
        if (syncing) return
        if (effective.isEmpty()) {
            Toast.makeText(context, "请先勾选要同步的倒数日", Toast.LENGTH_SHORT).show()
            return
        }
        syncing = true
        scope.launch(Dispatchers.IO) {
            val toSync = events.filter { it.id in effective }
            val result = CalendarSyncManager.syncAll(context, toSync)
            withContext(Dispatchers.Main) {
                syncing = false
                syncTick++
                result.onSuccess { count ->
                    Toast.makeText(context, "已同步 $count 个倒数日到系统日历", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "同步失败：${it.message ?: "日历不可用"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) runSync()
        else Toast.makeText(context, "需要日历权限才能同步", Toast.LENGTH_SHORT).show()
    }

    fun syncOrAskPermission() {
        val granted = calendarPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) runSync() else permissionLauncher.launch(calendarPermissions)
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "同步系统日历",
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
                        SmallTitle(text = "同步")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            // Summary re-reads the prefs after each sync (tick).
                            val lastSync = remember(syncTick) {
                                CalendarSyncManager.getLastSyncInfo(context)
                            }
                            ArrowPreference(
                                title = "立即同步",
                                summary = when {
                                    syncing -> "正在同步…"
                                    lastSync != null -> {
                                        val time = LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(lastSync.first),
                                            ZoneId.systemDefault(),
                                        )
                                        "已选 ${effective.size}/${events.size} 个倒数日 · " +
                                            "上次同步 " + time.format(
                                                DateTimeFormatter.ofPattern("M月d日 HH:mm"),
                                            )
                                    }
                                    else -> "已选 ${effective.size}/${events.size} 个倒数日，点击开始同步"
                                },
                                enabled = !syncing,
                                onClick = {
                                    if (effective.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "请先勾选要同步的倒数日",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        showSyncDialog = true
                                    }
                                },
                            )
                            ArrowPreference(
                                title = "移除日历同步",
                                summary = "删除系统日历中的全部 HyperDay 事件",
                                enabled = !syncing,
                                onClick = { showRemoveDialog = true },
                            )
                        }
                    }

                    if (events.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            SmallTitle(text = "选择倒数日")
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            ) {
                                val allState = when {
                                    effective.isEmpty() -> ToggleableState.Off
                                    effective.size == events.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                }
                                CheckRow(
                                    title = "全选",
                                    summary = "${effective.size}/${events.size} 个倒数日",
                                    state = allState,
                                    onToggle = { on ->
                                        selectedIds = if (on) events.map { it.id }.toSet()
                                        else emptySet()
                                        CalendarSyncManager.setSelectedIds(context, selectedIds ?: emptySet())
                                    },
                                )
                                val sorted = events.sortedBy { DateUtils.effectiveEpochDay(it) }
                                sorted.forEach { event ->
                                    CheckRow(
                                        title = event.title,
                                        summary = DateUtils.formatDate(
                                            DateUtils.effectiveEpochDay(event),
                                        ),
                                        checked = event.id in effective,
                                        onToggle = { on ->
                                            val next = if (on) effective + event.id
                                            else effective - event.id
                                            selectedIds = next
                                            CalendarSyncManager.setSelectedIds(context, next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Both confirmation dialogs must live INSIDE this Scaffold's
            // content lambda (the popup host lives there — a dialog written
            // outside it silently never shows) AND pass
            // renderInRootScaffold = false: this page is a pushed route
            // covering the main-tabs scaffold, so a dialog rendered into
            // the root scaffold would be invisible behind this page.
            if (showSyncDialog) {
                OverlayDialog(
                    title = "立即同步",
                    summary = "将把已选的 ${effective.size} 个倒数日写入系统日历，并覆盖之前同步的旧事件。确定继续吗？",
                    show = true,
                    onDismissRequest = { showSyncDialog = false },
                    renderInRootScaffold = false,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showSyncDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "同步",
                            onClick = {
                                showSyncDialog = false
                                syncOrAskPermission()
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            OverlayDialog(
                title = "移除日历同步",
                summary = "将删除系统日历中由 HyperDay 创建的全部事件与日历，不影响应用内的倒数日。确定继续吗？",
                show = showRemoveDialog,
                onDismissRequest = { showRemoveDialog = false },
                renderInRootScaffold = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRemoveDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        onClick = {
                            showRemoveDialog = false
                            syncing = true
                            scope.launch(Dispatchers.IO) {
                                val result = CalendarSyncManager.removeAll(context)
                                withContext(Dispatchers.Main) {
                                    syncing = false
                                    syncTick++
                                    result.onSuccess {
                                        Toast.makeText(context, "已移除日历同步", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "移除失败：${it.message ?: "日历不可用"}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One checkable row: [Checkbox] + title + summary line. */
@Composable
private fun CheckRow(
    title: String,
    summary: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    CheckRow(
        title = title,
        summary = summary,
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onToggle = onToggle,
    )
}

/** One checkable row: [Checkbox] (possibly tristate) + title + summary. */
@Composable
private fun CheckRow(
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
