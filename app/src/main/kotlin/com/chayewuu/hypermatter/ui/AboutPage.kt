package com.chayewuu.hypermatter.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.ui.effect.BgEffectBackground
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

private const val REPO_URL = "https://github.com/ChaYeWuu/HyperDay"
private const val MIUIX_URL = "https://github.com/miuix-kotlin-ui/miuix"

// Official Miuix example card-blend presets
// (example component/blend/ColorBlendToken.kt, Apache-2.0): dark theme uses
// Overlay_Thin_Light, light theme uses Pured_Regular_Light.
private val CardBlendDark = listOf(
    BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
    BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
)

private val CardBlendLight = listOf(
    BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
)

// Official logo-text frosted-glass presets (example AboutPage.kt logoBlend):
// text glyphs act as a mask (contentBlendMode = DstIn) over the blurred,
// color-blended background.
private val LogoBlendDark = listOf(
    BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
    BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
)

private val LogoBlendLight = listOf(
    BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
    BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
)

/**
 * Standalone about route (pushed from Settings): the official Miuix dynamic
 * color-blending background with frosted logo text, about links, tech stack,
 * and credits. Owns its top bar with a back button and the progressive blur.
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }

    // Resolve the effective dark theme state from the app appearance setting.
    val settingsStore = LocalSettingsStore.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val isDarkTheme = when (colorMode) {
        2 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    // Official Miuix blur pattern (utils/PageUtils.kt rememberBlurBackdrop):
    // capture the shader background layer so cards can textureBlur it.
    val cardBackdrop: LayerBackdrop? = if (isRuntimeShaderSupported()) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val cardBlend = if (isDarkTheme) CardBlendDark else CardBlendLight

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(intent) }
    }

    // Top-bar progressive blur samples the whole page (shader + content).
    val barBackdrop = rememberBlurBackdrop()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "关于",
                    color = if (barBackdrop != null)
                        Color.Transparent
                    else
                        MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        // Official Miuix dynamic color-blending shader background (AGSL, API 33+).
        // The card backdrop records the shader layer so blurred cards sample it;
        // the bar backdrop records the whole page for the frosted top bar.
        BgEffectBackground(
            dynamicBackground = true,
            isFullSize = true,
            isDarkTheme = isDarkTheme,
            modifier = Modifier
                .fillMaxSize()
                .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier),
            bgModifier = if (cardBackdrop != null)
                Modifier.layerBackdrop(cardBackdrop)
            else
                Modifier,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // Miuix overscroll bounce + boundary haptic
                    .overScrollVertical()
                    .scrollEndHaptic(),
                // Content scrolls under the blurred top bar (the logo frosts
                // beneath it instead of being clipped at the bar's edge).
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
            ) {
                // App icon + name + version
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Official Miuix about-page logo presentation: an 88dp
                        // rounded white tile with the app icon inside.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_image),
                                contentDescription = "HyperDay",
                                modifier = Modifier.size(74.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "HyperDay",
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 35.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.then(
                                if (cardBackdrop != null) {
                                    // Official frosted-glass logo text: the glyphs
                                    // mask a max-radius blurred, color-blended
                                    // sample of the dynamic background.
                                    Modifier.textureBlur(
                                        backdrop = cardBackdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 150f,
                                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                        colors = BlurDefaults.blurColors(
                                            blendColors = if (isDarkTheme) LogoBlendDark else LogoBlendLight,
                                        ),
                                        contentBlendMode = ComposeBlendMode.DstIn,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "版本 1.0.0",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "一个简洁的倒数日应用",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }

                // About section
                item {
                    SmallTitle(text = "关于")
                    BlurredCard(
                        backdrop = cardBackdrop,
                        blend = cardBlend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        ArrowPreference(
                            title = "项目主页",
                            summary = "在浏览器中打开 GitHub 仓库",
                            onClick = { openUrl(REPO_URL) },
                        )
                        ArrowPreference(
                            title = "开源许可",
                            summary = "查看第三方开源库",
                            onClick = { showLicenseDialog = true },
                        )
                    }
                }

                // Tech stack
                item {
                    SmallTitle(text = "技术栈")
                    BlurredCard(
                        backdrop = cardBackdrop,
                        blend = cardBlend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            InfoLine("UI 框架", "Miuix (v0.9.4-rc01)")
                            Spacer(Modifier.height(8.dp))
                            InfoLine("开发语言", "Kotlin 2.4.0")
                            Spacer(Modifier.height(8.dp))
                            InfoLine("构建工具", "Gradle 9.4.1 + AGP 9.2.1")
                            Spacer(Modifier.height(8.dp))
                            InfoLine("设计语言", "HyperOS Design")
                        }
                    }
                }

                // Credits
                item {
                    SmallTitle(text = "致谢")
                    BlurredCard(
                        backdrop = cardBackdrop,
                        blend = cardBlend,
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
                                text = "感谢 Miuix UI 项目提供的 HyperOS 设计语言 Compose 组件库。",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = MIUIX_URL.removePrefix("https://"),
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        title = "开源许可",
        summary = "本应用使用了以下开源项目：\n\n" +
            "· Miuix UI — HyperOS 风格 Compose 组件库\n" +
            "· Jetpack Compose — Android 声明式 UI 框架\n" +
            "· Kotlin & kotlinx.serialization — JetBrains\n" +
            "· desugar_jdk_libs — Google\n\n" +
            "各项目的完整许可文本请见其源码仓库。",
        show = showLicenseDialog,
        onDismissRequest = { showLicenseDialog = false },
    ) {
        TextButton(
            text = "关闭",
            onClick = { showLicenseDialog = false },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Official Miuix blurred-card pattern: the card itself becomes transparent and
 * a textureBlur modifier samples the background layer captured by
 * [rememberLayerBackdrop], blended with a preset color mix. Falls back to a
 * plain surfaceContainer card when runtime shaders are unavailable.
 */
@Composable
private fun BlurredCard(
    backdrop: LayerBackdrop?,
    blend: List<BlendColorEntry>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.then(
            if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 60f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurDefaults.blurColors(blendColors = blend),
                )
            } else {
                Modifier
            }
        ),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
    ) {
        content()
    }
}

@Composable
private fun InfoLine(
    key: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
        )
    }
}
