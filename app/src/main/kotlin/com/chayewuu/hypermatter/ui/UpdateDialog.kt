package com.chayewuu.hypermatter.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.BuildConfig
import com.chayewuu.hypermatter.data.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phase of the in-app update flow. */
internal sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    data class Available(val release: AppUpdater.Release) : UpdatePhase

    data object UpToDate : UpdatePhase

    data class Failed(val message: String) : UpdatePhase

    data class Downloading(val release: AppUpdater.Release, val progress: Float) : UpdatePhase

    data class Ready(val release: AppUpdater.Release, val file: File) : UpdatePhase
}

/**
 * State holder for the update flow, shared by the settings entry
 * (manual check, every result surfaces) and the startup auto-check
 * (only a found update with a not-yet-dismissed tag surfaces).
 *
 * Must be remembered inside a Scaffold content subtree so the rendered
 * [UpdateDialogContent] overlay can reach the scaffold popup host.
 */
internal class UpdateDialogState {
    var phase by mutableStateOf<UpdatePhase>(UpdatePhase.Idle)
        private set

    val isBusy: Boolean
        get() = phase is UpdatePhase.Checking || phase is UpdatePhase.Downloading

    /** Manual check from settings: always reports the outcome. */
    fun checkManual(scope: kotlinx.coroutines.CoroutineScope) {
        if (isBusy) return
        phase = UpdatePhase.Checking
        scope.launch(Dispatchers.IO) {
            val result = AppUpdater.checkForUpdate()
            withContext(Dispatchers.Main) {
                phase = when (result) {
                    is AppUpdater.CheckResult.Checked ->
                        if (result.hasUpdate) UpdatePhase.Available(result.release)
                        else UpdatePhase.UpToDate
                    is AppUpdater.CheckResult.Failed -> UpdatePhase.Failed(result.message)
                }
            }
        }
    }

    /** Startup check: once per calendar day, only surfaces a new release. */
    fun autoCheckIfDue(context: Context, scope: kotlinx.coroutines.CoroutineScope) {
        if (isBusy) return
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (prefs.getString("last_check_date", "") == today) return
        prefs.edit().putString("last_check_date", today).apply()

        scope.launch(Dispatchers.IO) {
            val result = AppUpdater.checkForUpdate()
            withContext(Dispatchers.Main) {
                if (result is AppUpdater.CheckResult.Checked &&
                    result.hasUpdate &&
                    result.release.tagName != prefs.getString("dismissed_tag", "")
                ) {
                    phase = UpdatePhase.Available(result.release)
                }
                // UpToDate / Failed stay silent on startup.
            }
        }
    }

    /** Downloads the release APK, reporting progress, then auto-installs. */
    fun downloadAndInstall(context: Context, scope: kotlinx.coroutines.CoroutineScope, release: AppUpdater.Release) {
        if (phase is UpdatePhase.Downloading) return
        // Already downloaded earlier? Straight to install.
        AppUpdater.downloadedApk(context, release.tagName)?.let { apk ->
            phase = UpdatePhase.Ready(release, apk)
            runCatching { AppUpdater.installApk(context, apk) }
            return
        }
        phase = UpdatePhase.Downloading(release, 0f)
        scope.launch(Dispatchers.IO) {
            try {
                val apk = AppUpdater.downloadApk(context, release) { progress ->
                    phase = UpdatePhase.Downloading(release, progress)
                }
                withContext(Dispatchers.Main) {
                    phase = UpdatePhase.Ready(release, apk)
                    runCatching { AppUpdater.installApk(context, apk) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    phase = UpdatePhase.Failed("下载失败：${e.message ?: "网络错误"}")
                }
            }
        }
    }

    /** Later: remember the tag so the startup check stays quiet about it. */
    fun dismiss(context: Context, release: AppUpdater.Release) {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            .edit().putString("dismissed_tag", release.tagName).apply()
        phase = UpdatePhase.Idle
    }

    fun reset() {
        phase = UpdatePhase.Idle
    }
}

@Composable
internal fun rememberUpdateDialogState(): UpdateDialogState = remember { UpdateDialogState() }

/**
 * Renders the update overlay for [state] (nothing while idle/checking).
 * Host it inside a Scaffold content subtree.
 */
@Composable
internal fun UpdateDialogContent(state: UpdateDialogState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    when (val p = state.phase) {
        is UpdatePhase.Available -> UpdatePromptDialog(
            release = p.release,
            downloading = false,
            progress = 0f,
            onLater = { state.dismiss(context, p.release) },
            onUpdate = { state.downloadAndInstall(context, scope, p.release) },
        )

        is UpdatePhase.Downloading -> UpdatePromptDialog(
            release = p.release,
            downloading = true,
            progress = p.progress,
            onLater = { state.reset() },
            onUpdate = {},
        )

        is UpdatePhase.Ready -> UpdatePromptDialog(
            release = p.release,
            downloading = false,
            progress = 1f,
            onLater = { state.reset() },
            onUpdate = {
                runCatching { AppUpdater.installApk(context, p.file) }
            },
        )

        is UpdatePhase.UpToDate -> OverlayDialog(
            title = "检查更新",
            summary = "已是最新版本 v${BuildConfig.VERSION_NAME}",
            show = true,
            onDismissRequest = { state.reset() },
        ) {
            TextButton(
                text = "确定",
                onClick = { state.reset() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is UpdatePhase.Failed -> OverlayDialog(
            title = "检查更新",
            summary = p.message,
            show = true,
            onDismissRequest = { state.reset() },
        ) {
            TextButton(
                text = "确定",
                onClick = { state.reset() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        UpdatePhase.Idle, UpdatePhase.Checking -> Unit
    }
}

/** Startup auto-check host: silent daily check + overlay when a release is found. */
@Composable
fun UpdateAutoCheckHost() {
    val state = rememberUpdateDialogState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        state.autoCheckIfDue(context, scope)
    }
    UpdateDialogContent(state)
}

@Composable
private fun UpdatePromptDialog(
    release: AppUpdater.Release,
    downloading: Boolean,
    progress: Float,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    OverlayDialog(
        title = "发现新版本",
        summary = "最新版本 v${release.versionLabel}（当前 v${BuildConfig.VERSION_NAME}）",
        show = true,
        onDismissRequest = { if (!downloading) onLater() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (release.body.isNotBlank()) {
                Text(
                    text = release.body.take(600),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = if (downloading) "取消" else "稍后",
                    onClick = onLater,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "立即更新",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
