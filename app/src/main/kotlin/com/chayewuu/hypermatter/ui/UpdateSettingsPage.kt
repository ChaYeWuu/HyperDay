package com.chayewuu.hypermatter.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.data.AppUpdater
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 更新设置 secondary page (软件版本 → 三点菜单 → 软件更新设置), ported
 * from the HyperIntervals UpdateSettingsScreen layout: a single glass card
 * with the auto-check switch, the download-source dropdown and the
 * clean-packages row. Shares the "update_prefs" preferences with
 * AppUpdater / UpdateAutoCheckHost.
 */
@Composable
fun UpdateSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    }
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    var autoCheck by remember { mutableStateOf(prefs.getBoolean("auto_check_update", true)) }
    var downloadSource by remember { mutableStateOf(AppUpdater.getDownloadSource(context)) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "更新设置",
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
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    item(key = "update_settings") {
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            SwitchPreference(
                                title = "自动检查更新",
                                summary = "每天启动时自动检查是否有新版本",
                                checked = autoCheck,
                                onCheckedChange = { enabled ->
                                    autoCheck = enabled
                                    prefs.edit()
                                        .putBoolean("auto_check_update", enabled)
                                        .apply()
                                },
                            )
                            OverlayDropdownPreference(
                                title = "下载源",
                                summary = when (downloadSource) {
                                    AppUpdater.SOURCE_GITEE -> "Gitee（国内直连）"
                                    AppUpdater.SOURCE_GITHUB -> "GitHub（含加速镜像）"
                                    else -> "自动识别（优先 Gitee）"
                                },
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "自动识别",
                                            selected = downloadSource == AppUpdater.SOURCE_AUTO,
                                            onClick = {
                                                downloadSource = AppUpdater.SOURCE_AUTO
                                                AppUpdater.setDownloadSource(
                                                    context, AppUpdater.SOURCE_AUTO,
                                                )
                                            },
                                        ),
                                        DropdownItem(
                                            text = "Gitee",
                                            selected = downloadSource == AppUpdater.SOURCE_GITEE,
                                            onClick = {
                                                downloadSource = AppUpdater.SOURCE_GITEE
                                                AppUpdater.setDownloadSource(
                                                    context, AppUpdater.SOURCE_GITEE,
                                                )
                                            },
                                        ),
                                        DropdownItem(
                                            text = "GitHub",
                                            selected = downloadSource == AppUpdater.SOURCE_GITHUB,
                                            onClick = {
                                                downloadSource = AppUpdater.SOURCE_GITHUB
                                                AppUpdater.setDownloadSource(
                                                    context, AppUpdater.SOURCE_GITHUB,
                                                )
                                            },
                                        ),
                                    ),
                                ),
                            )
                            BasicComponent(
                                title = "清理安装包",
                                summary = "删除已下载的新版本安装包",
                                endActions = {
                                    IconButton(
                                        onClick = { clearDownloadedPackages(context) },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "清理安装包",
                                        )
                                    }
                                },
                                onClick = { clearDownloadedPackages(context) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun clearDownloadedPackages(context: Context) {
    val deleted = context.filesDir.listFiles()
        ?.count { file ->
            file.name.startsWith("update-") &&
                file.name.endsWith(".apk") &&
                file.name != "update-.apk" &&
                file.delete()
        } ?: 0
    Toast.makeText(
        context,
        if (deleted > 0) "已清理 $deleted 个安装包" else "暂无可清理的安装包",
        Toast.LENGTH_SHORT,
    ).show()
}
