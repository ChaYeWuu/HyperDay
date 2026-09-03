package com.chayewuu.hypermatter.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.reminder.IslandNotifier
import com.chayewuu.hypermatter.reminder.ReminderScheduler
import com.chayewuu.hypermatter.shizuku.ShizukuManager
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalReminderStore
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 小米超级岛 sub-page: the island master switch, the Shizuku
 * authorization entry (cloud whitelist bypass) and a test island trigger.
 */
@Composable
fun IslandPage(onBack: () -> Unit) {
    val reminderStore = LocalReminderStore.current
    val islandEnabled by reminderStore.islandEnabled.collectAsState()
    val context = LocalContext.current
    val barBackdrop = rememberBlurBackdrop()

    val shizukuRunning = remember {
        ShizukuManager.init(context)
        ShizukuManager.isShizukuRunning()
    }
    var shizukuGranted by remember { mutableStateOf(ShizukuManager.isAuthorized(context)) }

    /** Apply a setting change and re-plan all alarms right away. */
    fun apply(change: () -> Unit) {
        change()
        runCatching { ReminderScheduler.reschedule(context) }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "小米超级岛",
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
                    SmallTitle(text = "小米超级岛")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "小米超级岛",
                            summary = "提醒以小米超级岛样式弹出，关闭后以普通通知提醒",
                            checked = islandEnabled,
                            onCheckedChange = { on ->
                                apply { reminderStore.setIslandEnabled(on) }
                            },
                        )
                        ArrowPreference(
                            title = "Shizuku 授权",
                            summary = when {
                                shizukuGranted ->
                                    "已授权：发送超级岛提醒时自动绕过云端白名单校验"
                                shizukuRunning ->
                                    "未授权：点击向 Shizuku 申请权限，授权后可绕过超级岛云端白名单"
                                else ->
                                    "Shizuku 未运行：请先启动 Shizuku，返回后重新进入本页授权"
                            },
                            onClick = {
                                if (shizukuGranted) return@ArrowPreference
                                if (!shizukuRunning) {
                                    runCatching {
                                        context.packageManager
                                            .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            ?.let { context.startActivity(it) }
                                    }
                                }
                                ShizukuManager.requestPermission(context) { granted ->
                                    shizukuGranted = granted
                                }
                            },
                        )
                        ArrowPreference(
                            title = "发送测试超级岛",
                            summary = "立即弹出一条 60 秒倒计时的测试超级岛通知",
                            onClick = { IslandNotifier.sendTestIsland(context) },
                        )
                    }
                }
            }
        }
    }
}
