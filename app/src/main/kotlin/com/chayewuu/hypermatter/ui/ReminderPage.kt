package com.chayewuu.hypermatter.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.reminder.IslandNotifier
import com.chayewuu.hypermatter.reminder.LiveUpdateNotifier
import com.chayewuu.hypermatter.reminder.ReminderScheduler
import com.chayewuu.hypermatter.shizuku.ShizukuManager
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalCategoryStore
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalReminderStore
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Advance options; index == advanceDays (0..3). */
private val AdvanceItems = listOf("当天 09:00", "提前 1 天", "提前 2 天", "提前 3 天")

/**
 * 日程提醒 settings: master switch, advance time, and the free selection
 * of which categories / which individual events get reminded. Every change
 * immediately re-plans the alarm schedule.
 */
@Composable
fun ReminderPage(onBack: () -> Unit) {
    val reminderStore = LocalReminderStore.current
    val categoryStore = LocalCategoryStore.current
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val categories by categoryStore.categories.collectAsState()
    val enabled by reminderStore.enabled.collectAsState()
    val advanceDays by reminderStore.advanceDays.collectAsState()
    val selectedCategories by reminderStore.categoryIds.collectAsState()
    val selectedEvents by reminderStore.eventIds.collectAsState()
    val islandEnabled by reminderStore.islandEnabled.collectAsState()
    val liveUpdatesEnabled by reminderStore.liveUpdatesEnabled.collectAsState()
    val context = LocalContext.current
    val barBackdrop = rememberBlurBackdrop()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val shizukuRunning = remember {
        ShizukuManager.init(context)
        ShizukuManager.isShizukuRunning()
    }
    var shizukuGranted by remember { mutableStateOf(ShizukuManager.isAuthorized(context)) }
    val needsExactPermission = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            }.getOrDefault(true) == false
    }

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
                    title = "日程提醒",
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
                    SmallTitle(text = "提醒")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "开启日程提醒",
                            summary = "在倒数日到来前发送通知提醒",
                            checked = enabled,
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33) {
                                    if (ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    }
                                }
                                apply { reminderStore.setEnabled(on) }
                            },
                        )
                        if (enabled) {
                            OverlayDropdownPreference(
                                title = "提醒时间",
                                summary = AdvanceItems.getOrElse(advanceDays) { "提前 1 天" },
                                items = AdvanceItems,
                                selectedIndex = advanceDays,
                                renderInRootScaffold = false,
                                onSelectedIndexChange = { index ->
                                    apply { reminderStore.setAdvanceDays(index) }
                                },
                            )
                            if (needsExactPermission) {
                                ArrowPreference(
                                    title = "精确闹钟权限",
                                    summary = "未授权时提醒时间可能存在偏差，点击前往系统设置开启",
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(text = "小米超级岛与 Live Updates")
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
                        // Real system state: promoted style needs the user's
                        // per-app grant (no request API — toggle lives in the
                        // system notification settings, if the ROM exposes it).
                        val promoted = LiveUpdateNotifier.canPostPromoted(context)
                        SwitchPreference(
                            title = "Live Updates",
                            summary = when {
                                Build.VERSION.SDK_INT < 36 ->
                                    "当前系统不支持 Live Updates 样式，将以带实时倒计时的普通通知显示"
                                promoted ->
                                    "系统已允许实时更新样式，提醒将以 Live Updates 持续通知展示"
                                else ->
                                    "系统未开启实时更新样式，将以带实时倒计时的普通通知显示"
                            },
                            checked = liveUpdatesEnabled,
                            onCheckedChange = { on ->
                                apply { reminderStore.setLiveUpdatesEnabled(on) }
                            },
                        )
                        ArrowPreference(
                            title = "系统通知设置",
                            summary = "查看通知权限与「实时更新 / Live Updates」开关",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(
                                                Settings.EXTRA_APP_PACKAGE,
                                                context.packageName,
                                            )
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
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

                if (enabled && categories.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "提醒分组")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "勾选要提醒的分组；分组与倒数日均未勾选时提醒全部",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            categories.forEach { category ->
                                val count = events.count { it.category == category.id }
                                CheckRow(
                                    title = category.name,
                                    summary = "$count 个倒数日",
                                    checked = category.id in selectedCategories,
                                    onToggle = { on ->
                                        apply {
                                            reminderStore.setCategoryIds(
                                                if (on) selectedCategories + category.id
                                                else selectedCategories - category.id,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                if (enabled && events.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SmallTitle(text = "提醒倒数日")
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            val sorted = events.sortedBy { DateUtils.effectiveEpochDay(it) }
                            sorted.forEach { event ->
                                CheckRow(
                                    title = event.title,
                                    summary = DateUtils.formatDate(
                                        DateUtils.effectiveEpochDay(event),
                                    ),
                                    checked = event.id in selectedEvents,
                                    onToggle = { on ->
                                        apply {
                                            reminderStore.setEventIds(
                                                if (on) selectedEvents + event.id
                                                else selectedEvents - event.id,
                                            )
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

/** One checkable row: [Checkbox] + title + summary line. */
@Composable
private fun CheckRow(
    title: String,
    summary: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { onToggle(!checked) },
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
