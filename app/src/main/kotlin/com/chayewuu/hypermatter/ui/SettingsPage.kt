package com.chayewuu.hypermatter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chayewuu.hypermatter.BuildConfig
import com.chayewuu.hypermatter.data.BackupManager
import com.chayewuu.hypermatter.data.CalendarSyncManager
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    onOpenAbout: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenWidget: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenReminder: () -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val viewModel = LocalEventViewModel.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }
    // Parsed import waiting for the user's confirmation (null = idle).
    var pendingImport by remember { mutableStateOf<BackupManager.ImportResult?>(null) }
    var importing by remember { mutableStateOf(false) }

    // ---- calendar sync state ----
    var calendarSyncing by remember { mutableStateOf(false) }
    var showCalendarRemoveDialog by remember { mutableStateOf(false) }
    // Bumped after each sync so the summary line re-reads the prefs.
    var calendarSyncTick by remember { mutableStateOf(0) }

    // ---- update state ----
    val updateState = rememberUpdateDialogState()

    val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    fun hasCalendarPermission(): Boolean = calendarPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun runCalendarSync() {
        if (calendarSyncing) return
        calendarSyncing = true
        scope.launch(Dispatchers.IO) {
            val result = CalendarSyncManager.syncAll(context, viewModel.events.value)
            withContext(Dispatchers.Main) {
                calendarSyncing = false
                calendarSyncTick++
                result.onSuccess { count ->
                    Toast.makeText(context, "已同步 $count 个倒数日到系统日历", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "同步失败：${it.message ?: "日历不可用"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            runCalendarSync()
        } else {
            Toast.makeText(context, "需要日历权限才能同步", Toast.LENGTH_SHORT).show()
        }
    }

    val modeName = when (colorMode) {
        1 -> "浅色"
        2 -> "深色"
        else -> "跟随系统"
    }

    // Export: SAF "create document" — HyperDay's own JSON format.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val events = viewModel.events.value
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                BackupManager.exportBackup(context, uri, events)
            }.getOrDefault(false)
            scope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (ok) "已备份 ${events.size} 个倒数日" else "备份失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Import: SAF "open document" — HyperDay JSON or official .idmbaks.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                BackupManager.importBackup(context, uri)
            }
            scope.launch(Dispatchers.Main) {
                importing = false
                result.onSuccess { parsed ->
                    if (parsed.events.isEmpty()) {
                        Toast.makeText(context, "备份文件中没有可导入的事件", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingImport = parsed
                    }
                }.onFailure {
                    Toast.makeText(context, "导入失败：${it.message ?: "无法识别的备份文件"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Content scrolls under the blurred top bar; bar height becomes
    // list content padding so the first item starts below the bar.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // Miuix overscroll bounce + boundary haptic
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "外观")
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                ArrowPreference(
                    title = "主题风格",
                    summary = modeName,
                    onClick = onOpenTheme,
                )
                ArrowPreference(
                    title = "小部件",
                    summary = "预览桌面小部件，为单个事件小部件选择绑定",
                    onClick = onOpenWidget,
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SmallTitle(text = "分类与提醒")
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                ArrowPreference(
                    title = "分类管理",
                    summary = "管理事件的分类，支持自定义",
                    onClick = onOpenCategory,
                )
                ArrowPreference(
                    title = "日程提醒",
                    summary = "自由选择要提醒的分组与倒数日",
                    onClick = onOpenReminder,
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SmallTitle(text = "数据")
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                // Summary re-reads the prefs after each sync (tick).
                val lastSync = remember(calendarSyncTick) {
                    CalendarSyncManager.getLastSyncInfo(context)
                }
                ArrowPreference(
                    title = "同步到系统日历",
                    summary = when {
                        calendarSyncing -> "正在同步…"
                        lastSync != null -> {
                            val time = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(lastSync.first),
                                ZoneId.systemDefault(),
                            )
                            "上次同步 ${time.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))} · ${lastSync.second} 个事件"
                        }
                        else -> "把全部倒数日写入系统日历（本地日历，不上传）"
                    },
                    enabled = !calendarSyncing,
                    onClick = {
                        if (hasCalendarPermission()) {
                            runCalendarSync()
                        } else {
                            calendarPermissionLauncher.launch(calendarPermissions)
                        }
                    },
                )
                ArrowPreference(
                    title = "移除日历同步",
                    summary = "删除系统日历中的全部 HyperDay 事件",
                    enabled = !calendarSyncing,
                    onClick = { showCalendarRemoveDialog = true },
                )
                ArrowPreference(
                    title = "备份数据",
                    summary = "把全部倒数日导出为一个备份文件",
                    onClick = {
                        val stamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        backupLauncher.launch("HyperDay_backup_$stamp.json")
                    },
                )
                ArrowPreference(
                    title = "导入数据",
                    summary = "从备份文件恢复倒数日，重复的自动跳过",
                    enabled = !importing,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
                ArrowPreference(
                    title = "清除所有倒数日",
                    summary = "删除全部已保存的事件",
                    onClick = { showClearDialog = true },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            SmallTitle(text = "其他")
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                ArrowPreference(
                    title = "检查更新",
                    summary = when {
                        updateState.isBusy -> "正在检查…"
                        else -> "当前版本 v${BuildConfig.VERSION_NAME}"
                    },
                    enabled = !updateState.isBusy,
                    onClick = { updateState.checkManual(scope) },
                )
                ArrowPreference(
                    title = "关于应用",
                    summary = "版本、开源许可与技术栈",
                    onClick = onOpenAbout,
                )
            }
        }
    }

    OverlayDialog(
        title = "清除所有倒数日",
        summary = "确定要删除全部已保存的事件吗？此操作不可撤销。",
        show = showClearDialog,
        onDismissRequest = { showClearDialog = false },
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
                text = "删除",
                onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    OverlayDialog(
        title = "移除日历同步",
        summary = "将删除系统日历中由 HyperDay 创建的全部事件与日历，不影响应用内的倒数日。确定继续吗？",
        show = showCalendarRemoveDialog,
        onDismissRequest = { showCalendarRemoveDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showCalendarRemoveDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "删除",
                onClick = {
                    showCalendarRemoveDialog = false
                    calendarSyncing = true
                    scope.launch(Dispatchers.IO) {
                        val result = CalendarSyncManager.removeAll(context)
                        withContext(Dispatchers.Main) {
                            calendarSyncing = false
                            calendarSyncTick++
                            result.onSuccess {
                                Toast.makeText(context, "已移除日历同步", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "移除失败：${it.message ?: "日历不可用"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    pendingImport?.let { pending ->
        OverlayDialog(
            title = "导入数据",
            summary = "检测到来自 ${pending.source} 的备份，共 ${pending.events.size} 个事件。" +
                "导入后与现有事件重复的会被跳过。",
            show = true,
            onDismissRequest = { pendingImport = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { pendingImport = null },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "导入",
                    onClick = {
                        val added = viewModel.importEvents(pending.events)
                        pendingImport = null
                        Toast.makeText(
                            context,
                            if (added > 0) "已导入 $added 个事件" else "没有新事件（全部已存在）",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Manual update check result overlay. Rendered here (inside the
    // MainTabs Scaffold content) so the dialog reaches the popup host.
    UpdateDialogContent(updateState)
}
