package com.chayewuu.hypermatter.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Fixed palette preview colors, matching the Miuix light/dark color scheme
// (light canvas #F7F7F7 + white cards / dark canvas #000000 + #242424 cards).
private val LightCanvas = Color(0xFFF7F7F7)
private val LightBlock = Color(0xFFFFFFFF)
private val LightLine = Color(0xFFD8D8D8)
private val DarkCanvas = Color(0xFF000000)
private val DarkBlock = Color(0xFF242424)
private val DarkLine = Color(0xFF484848)
private val LightAccent = Color(0xFF3482FF)
private val DarkAccent = Color(0xFF277AF7)

/**
 * Theme style picker, styled after the HyperOS Settings → 显示与亮度
 * light/dark mode cards: two large side-by-side preview cards plus a
 * full-width follow-system card. A selected card gets a primary ring and a
 * check mark.
 */
@Composable
fun ThemePage(
    onBack: () -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val view = LocalView.current

    val barBackdrop = rememberBlurBackdrop()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "主题风格",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (barBackdrop != null)
                        Modifier.layerBackdrop(barBackdrop)
                    else
                        Modifier
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(
                        label = "浅色",
                        selected = colorMode == 1,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            settingsStore.setColorMode(1)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        ModePreview(
                            canvas = LightCanvas,
                            block = LightBlock,
                            line = LightLine,
                            accent = LightAccent,
                        )
                    }
                    ModeCard(
                        label = "深色",
                        selected = colorMode == 2,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            settingsStore.setColorMode(2)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        ModePreview(
                            canvas = DarkCanvas,
                            block = DarkBlock,
                            line = DarkLine,
                            accent = DarkAccent,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                ModeCard(
                    label = "跟随系统",
                    selected = colorMode == 0,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        settingsStore.setColorMode(0)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Split preview: left half light, right half dark.
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                .background(LightCanvas),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                                .background(DarkCanvas),
                        )
                    }
                }
            }
        }
    }
}

/** Selectable mode card: preview mockup on top, label + check mark below. */
@Composable
private fun ModeCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (selected)
                    MiuixTheme.colorScheme.primary
                else
                    Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .clip(RoundedCornerShape(18.dp)),
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            content = preview,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected)
                    MiuixTheme.colorScheme.primary
                else
                    MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            )
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Mini HyperDay page mockup: canvas + skeleton title line + one content
 * card (with a small accent pill) + a floating action button dot.
 */
@Composable
private fun BoxScope.ModePreview(
    canvas: Color,
    block: Color,
    line: Color,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(canvas),
    ) {
        // Skeleton title line (top bar).
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .fillMaxWidth(0.4f)
                .height(7.dp)
                .clip(CircleShape)
                .background(line),
        )
        // Content card with an accent pill.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(block)
                .border(0.5.dp, line, RoundedCornerShape(9.dp)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .fillMaxWidth(0.35f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(line),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(width = 22.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
        }
        // Floating action button.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(accent),
        )
    }
}
