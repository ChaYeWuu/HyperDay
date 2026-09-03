package com.chayewuu.hypermatter.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.reminder.LiveUpdateNotifier
import com.chayewuu.hypermatter.reminder.ReminderScheduler
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.theme.LocalReminderStore
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 实时动态 (Live Updates) sub-page: the live-updates master switch, the
 * system notification settings entry and the direct entry to the system's
 * per-app 实时动态 grant.
 */
@Composable
fun LiveUpdatesPage(onBack: () -> Unit) {
    val reminderStore = LocalReminderStore.current
    val enabled by reminderStore.enabled.collectAsState()
    val liveUpdatesEnabled by reminderStore.liveUpdatesEnabled.collectAsState()
    val context = LocalContext.current
    val barBackdrop = rememberBlurBackdrop()

    // Live Updates promoted-notification grant. There is no request API —
    // the per-app toggle lives in system settings; we re-check whenever the
    // user returns from there and whenever the toggles change.
    var promoted by remember { mutableStateOf(LiveUpdateNotifier.canPostPromoted(context)) }
    LaunchedEffect(enabled, liveUpdatesEnabled) {
        promoted = LiveUpdateNotifier.canPostPromoted(context)
    }
    val promotedSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        promoted = LiveUpdateNotifier.canPostPromoted(context)
    }

    /** Jump straight to the system Live Updates toggle; fall back to the app's notification settings. */
    fun launchPromotedSettings() {
        try {
            val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                data = Uri.parse("package:${context.packageName}")
            }
            promotedSettingsLauncher.launch(intent)
        } catch (_: Exception) {
            runCatching {
                promotedSettingsLauncher.launch(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
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
                    title = "实时动态",
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
                    SmallTitle(text = "实时动态")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "实时动态",
                            summary = when {
                                Build.VERSION.SDK_INT < 36 ->
                                    "当前系统不支持实时动态样式，将以带实时倒计时的普通通知显示"
                                promoted ->
                                    "系统已允许实时动态，提醒将以实时动态通知展示"
                                else ->
                                    "系统未开启实时动态，将以带实时倒计时的普通通知显示"
                            },
                            checked = liveUpdatesEnabled,
                            onCheckedChange = { on ->
                                apply { reminderStore.setLiveUpdatesEnabled(on) }
                            },
                        )
                        ArrowPreference(
                            title = "系统通知设置",
                            summary = "查看通知权限与「实时动态」开关",
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
                    }
                }

                // Standalone 实时动态 entry (mirrors NexioSchedule): shown when
                // Live Updates is wanted but the system grant is still missing.
                if (enabled && liveUpdatesEnabled && Build.VERSION.SDK_INT >= 36 && !promoted) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = "开启实时动态",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    style = MiuixTheme.textStyles.body1,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "开启后，倒数日倒计时将实时显示在状态栏和锁屏上，无需打开应用即可查看",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2,
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(
                                    text = "前往开启实时动态",
                                    onClick = { launchPromotedSettings() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
