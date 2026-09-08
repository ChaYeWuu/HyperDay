package com.chayewuu.hypermatter.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.chayewuu.hypermatter.BuildConfig
import com.chayewuu.hypermatter.data.AppUpdater
import com.chayewuu.hypermatter.ui.data.changelogData
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.File

/** A changelog line split into an optional type tag plus the body text. */
private data class DisplayChange(
    val type: String?,
    val text: String,
)

private fun parseChange(change: String): DisplayChange {
    val prefixes = listOf("新增", "优化", "修复")
    val prefix = prefixes.firstOrNull { change.startsWith("$it ") }
    return if (prefix == null) {
        DisplayChange(null, change)
    } else {
        DisplayChange(prefix, change.removePrefix(prefix).trimStart())
    }
}

/** Colored type pill: 新增 = primary, 优化 = green, 修复 = neutral. */
@Composable
private fun ChangeTypeTag(type: String, modifier: Modifier = Modifier) {
    val tagColor = when (type) {
        "新增" -> MiuixTheme.colorScheme.primary
        "优化" -> Color(0xFF34B759)
        else -> MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    val tagBackground = when (type) {
        "新增" -> MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
        "优化" -> Color(0xFF34B759).copy(alpha = 0.14f)
        else -> MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.10f)
    }
    Text(
        text = type,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tagBackground)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = tagColor,
    )
}

/**
 * 软件版本 update page (设置 → 其他 → 软件版本), ported from the
 * HyperIntervals UpdateAppScreen layout: status header (icon + version),
 * changelog list with colored type tags, an inline settings card (auto
 * check / download source / clean packages) and a bottom progress button
 * that fills with the download progress.
 *
 * Check results are persisted into "update_prefs" so re-entering the page
 * shows the last known release immediately; a silent re-check runs on open.
 */
@Composable
fun UpdatePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    }
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    // Effective dark flag for the muted progress-button label color.
    val settingsStore = LocalSettingsStore.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val isDarkTheme = when (colorMode) {
        2 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    var hasUpdate by remember { mutableStateOf(prefs.getBoolean("has_update", false)) }
    var latestRelease by remember {
        mutableStateOf(
            prefs.getString("latest_tag", null)?.let { tag ->
                AppUpdater.Release(
                    tagName = tag,
                    name = tag,
                    body = prefs.getString("latest_body", "") ?: "",
                    apkUrl = prefs.getString("latest_apk_url", "") ?: "",
                    htmlUrl = "",
                    publishedAt = "",
                    source = prefs.getString("latest_source", "github") ?: "github",
                )
            },
        )
    }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var showCancelDownloadDialog by remember { mutableStateOf(false) }
    var hasConfirmedNoUpdate by remember { mutableStateOf(false) }
    var downloadComplete by remember {
        mutableStateOf(
            prefs.getString("latest_tag", null)
                ?.let { AppUpdater.downloadedApk(context, it) != null } == true,
        )
    }

    fun checkForUpdate(showResultToast: Boolean = true) {
        if (isChecking) return
        isChecking = true
        if (showResultToast) hasConfirmedNoUpdate = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { AppUpdater.checkForUpdate(context) }
            isChecking = false
            when (result) {
                is AppUpdater.CheckResult.Checked -> {
                    hasUpdate = result.hasUpdate
                    hasConfirmedNoUpdate = !result.hasUpdate
                    latestRelease = result.release
                    downloadComplete = result.hasUpdate &&
                        AppUpdater.downloadedApk(context, result.release.tagName) != null
                    prefs.edit()
                        .putBoolean("has_update", result.hasUpdate)
                        .putString("latest_tag", result.release.tagName)
                        .putString("latest_body", result.release.body)
                        .putString("latest_apk_url", result.release.apkUrl)
                        .putString("latest_source", result.release.source)
                        .apply()
                    if (showResultToast) {
                        Toast.makeText(
                            context,
                            if (result.hasUpdate) "发现新版本：v${result.release.versionLabel}"
                            else "当前已是最新版本",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                is AppUpdater.CheckResult.Failed -> {
                    hasConfirmedNoUpdate = false
                    if (showResultToast) {
                        Toast.makeText(
                            context,
                            "检查更新失败：${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    fun installDownloadedUpdate(tag: String) {
        val file = AppUpdater.downloadedApk(context, tag) ?: return
        runCatching { AppUpdater.installApk(context, file) }
            .onFailure { Toast.makeText(context, "安装失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    fun startDownload() {
        val release = latestRelease ?: return
        if (isDownloading) return

        AppUpdater.downloadedApk(context, release.tagName)?.let {
            downloadComplete = true
            installDownloadedUpdate(release.tagName)
            return
        }
        if (release.apkUrl.isBlank()) {
            Toast.makeText(context, "暂无新版下载链接", Toast.LENGTH_SHORT).show()
            return
        }

        isDownloading = true
        downloadComplete = false
        downloadProgress = 0f
        downloadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppUpdater.downloadApk(context, release) { progress ->
                        downloadProgress = progress
                        // Cancellation: the cancel handler flips isDownloading
                        // before cancelling the job; the IO loop notices on the
                        // next progress tick and unwinds through here.
                        if (!isDownloading) throw CancellationException("下载已取消")
                    }
                }
                downloadComplete = true
                downloadProgress = 1f
                Toast.makeText(context, "新版安装包下载完成", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                // Remove the partial file so it is not mistaken for a full APK.
                File(context.filesDir, "update-${release.tagName}.apk").delete()
                downloadProgress = 0f
            } catch (e: Exception) {
                Toast.makeText(context, "下载失败：${e.message ?: "网络错误"}", Toast.LENGTH_SHORT).show()
            } finally {
                isDownloading = false
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        val hadDownload = isDownloading
        showCancelDownloadDialog = false
        isDownloading = false
        downloadProgress = 0f
        downloadJob?.cancel()
        downloadJob = null
        if (hadDownload) {
            Toast.makeText(context, "已取消下载", Toast.LENGTH_SHORT).show()
        }
    }

    // Silent re-check every time the page opens (no toast unless manual).
    LaunchedEffect(Unit) {
        checkForUpdate(showResultToast = false)
    }

    val effectiveHasUpdate = hasUpdate && latestRelease != null
    fun onMainButtonClick() {
        if (isChecking) return
        if (isDownloading) {
            showCancelDownloadDialog = true
            return
        }
        if (!effectiveHasUpdate) {
            if (hasConfirmedNoUpdate) {
                // Author's Coolapk profile, deep link first, web fallback.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("coolmarket://u/40700674")),
                    )
                }.onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.coolapk.com/u/40700674"),
                            ),
                        )
                    }
                }
            } else {
                checkForUpdate()
            }
        } else if (downloadComplete) {
            latestRelease?.let { installDownloadedUpdate(it.tagName) }
        } else {
            startDownload()
        }
    }

    val statusText = if (effectiveHasUpdate) "发现新版本" else "已是最新版本"
    val versionText = if (effectiveHasUpdate) {
        "v${latestRelease?.versionLabel.orEmpty()}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }
    val changelog = if (effectiveHasUpdate) {
        latestRelease?.body.orEmpty().lines().filter { it.isNotBlank() }
    } else {
        changelogData.firstOrNull()?.changes.orEmpty()
    }
    val changelogTextColor = lerp(
        MiuixTheme.colorScheme.onSurfaceVariantActions,
        MiuixTheme.colorScheme.onSurface,
        0.35f,
    )
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "软件版本",
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
                        bottom = 132.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "update_status") {
                        Icon(
                            imageVector = MiuixIcons.Update,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(top = 20.dp),
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = versionText,
                            modifier = Modifier.padding(top = 6.dp),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    item(key = "changelog") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                        ) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 28.dp, bottom = 24.dp),
                            )
                            Text(
                                text = "更新日志",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            if (changelog.isEmpty()) {
                                Text(
                                    text = "暂无更新日志",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            } else {
                                changelog.forEach { change ->
                                    val displayChange = parseChange(change)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        displayChange.type?.let { type ->
                                            ChangeTypeTag(type, Modifier.alignByBaseline())
                                        }
                                        Text(
                                            text = displayChange.text,
                                            modifier = Modifier
                                                .alignByBaseline()
                                                .weight(1f)
                                                .padding(
                                                    start = if (displayChange.type != null) 10.dp else 0.dp,
                                                ),
                                            style = MiuixTheme.textStyles.body2,
                                            color = changelogTextColor,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "update_settings") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Spacer(Modifier.height(8.dp))
                            SmallTitle(text = "更新设置")
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                var autoCheck by remember {
                                    mutableStateOf(prefs.getBoolean("auto_check_update", true))
                                }
                                var downloadSource by remember {
                                    mutableStateOf(AppUpdater.getDownloadSource(context))
                                }
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

                UpdateProgressButton(
                    text = when {
                        isChecking -> "检查更新中..."
                        isDownloading -> if (downloadProgress > 0f) {
                            "下载中 ${(downloadProgress * 100).toInt()}%"
                        } else {
                            "下载中..."
                        }
                        effectiveHasUpdate && downloadComplete -> "安装新版本"
                        effectiveHasUpdate -> "下载新版本"
                        hasConfirmedNoUpdate -> "进入酷安讨论"
                        else -> "检查更新"
                    },
                    progress = downloadProgress,
                    showProgress = isDownloading,
                    muted = !effectiveHasUpdate && hasConfirmedNoUpdate,
                    enabled = !isChecking,
                    isDarkTheme = isDarkTheme,
                    onClick = { onMainButtonClick() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp)
                        .padding(bottom = 28.dp + navigationBarPadding)
                        .height(48.dp),
                )
            }

            // Cancel-download confirmation. Lives INSIDE the Scaffold content
            // so it reaches the popup host (an overlay composed outside the
            // Scaffold never shows).
            OverlayDialog(
                show = showCancelDownloadDialog,
                title = "取消下载？",
                onDismissRequest = { showCancelDownloadDialog = false },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "继续下载",
                        onClick = { showCancelDownloadDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "停止下载",
                        onClick = { cancelDownload() },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    )
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

/**
 * Bottom action button with a progress fill (ported from HyperIntervals):
 * a squircle surface whose track is primary-colored while idle and a filled
 * progress bar while downloading.
 */
@Composable
private fun UpdateProgressButton(
    text: String,
    progress: Float,
    showProgress: Boolean,
    muted: Boolean,
    enabled: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressColor = MiuixTheme.colorScheme.primary
    val trackColor = when {
        showProgress -> lerp(MiuixTheme.colorScheme.secondaryVariant, Color.Black, 0.10f)
        muted -> lerp(MiuixTheme.colorScheme.secondaryVariant, Color.Black, 0.06f)
        else -> progressColor
    }
    val contentColor = if (enabled) {
        when {
            showProgress -> Color.White
            muted -> if (isDarkTheme) Color.White else Color.Black
            else -> MiuixTheme.colorScheme.onPrimary
        }
    } else {
        MiuixTheme.colorScheme.disabledOnPrimaryButton
    }

    Box(
        modifier = modifier
            .squircleSurface(
                color = if (enabled) trackColor else MiuixTheme.colorScheme.disabledPrimaryButton,
                cornerRadius = ButtonDefaults.CornerRadius,
            )
            .squircleClip(cornerRadius = ButtonDefaults.CornerRadius)
            .drawBehind {
                if (showProgress) {
                    drawRect(
                        color = progressColor,
                        size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = contentColor,
        )
    }
}
