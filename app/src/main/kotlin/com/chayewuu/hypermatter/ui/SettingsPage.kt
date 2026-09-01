package com.chayewuu.hypermatter.ui

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
import com.chayewuu.hypermatter.data.BackupManager
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    onOpenAbout: () -> Unit,
    onOpenTheme: () -> Unit,
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
}
