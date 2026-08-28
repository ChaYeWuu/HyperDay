package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsPage(
    contentPadding: PaddingValues,
    onOpenAbout: () -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val viewModel = LocalEventViewModel.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val advancedMaterial by settingsStore.advancedMaterial.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    // Derived toggle states from colorMode: 0=System, 1=Light, 2=Dark
    val followSystem = colorMode == 0
    val darkMode = colorMode == 2

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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                SwitchPreference(
                    title = "跟随系统",
                    summary = "跟随系统深色模式",
                    checked = followSystem,
                    onCheckedChange = { checked ->
                        if (checked) {
                            settingsStore.setColorMode(0)
                        } else {
                            settingsStore.setColorMode(if (darkMode) 2 else 1)
                        }
                    },
                )
                SwitchPreference(
                    title = "深色模式",
                    summary = "手动切换深色/浅色",
                    checked = darkMode,
                    onCheckedChange = { checked ->
                        settingsStore.setColorMode(if (checked) 2 else 1)
                    },
                    enabled = !followSystem,
                )
                SwitchPreference(
                    title = "高级材质",
                    summary = "详情页卡片与按钮使用玻璃拟态模糊（需 Android 13+）",
                    checked = advancedMaterial,
                    onCheckedChange = { checked ->
                        settingsStore.setAdvancedMaterial(checked)
                    },
                )
            }
        }

        item {
            SmallTitle(text = "数据")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                ArrowPreference(
                    title = "清除所有倒数日",
                    summary = "删除全部已保存的事件",
                    onClick = { showClearDialog = true },
                )
            }
        }

        item {
            SmallTitle(text = "其他")
            Card(
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
        Row(modifier = Modifier.fillMaxWidth()) {
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
}
