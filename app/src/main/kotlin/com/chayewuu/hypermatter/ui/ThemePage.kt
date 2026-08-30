package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.R
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Theme style page: Miuix-style grouped preferences.
 *  - 显示: three appearance preview cards side by side (自动 / 浅色 / 深色).
 *  - 应用风格: Classic vs Liquid Glass (placeholder — value persisted, no
 *    visual effect yet).
 *  - 莫奈取色: switch (placeholder — value persisted, not wired yet).
 */
@Composable
fun ThemePage(
    onBack: () -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val appStyle by settingsStore.appStyle.collectAsState()
    val monetColor by settingsStore.monetColor.collectAsState()

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // Miuix overscroll bounce + boundary haptic
                    .overScrollVertical()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item {
                    SmallTitle(text = "显示")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppearanceCard(
                            drawableRes = R.drawable.theme_preview_auto,
                            selected = colorMode == 0,
                            onClick = { settingsStore.setColorMode(0) },
                            modifier = Modifier.weight(1f),
                        )
                        AppearanceCard(
                            drawableRes = R.drawable.theme_preview_light,
                            selected = colorMode == 1,
                            onClick = { settingsStore.setColorMode(1) },
                            modifier = Modifier.weight(1f),
                        )
                        AppearanceCard(
                            drawableRes = R.drawable.theme_preview_night,
                            selected = colorMode == 2,
                            onClick = { settingsStore.setColorMode(2) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(text = "应用风格")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = "应用风格",
                            summary = "切换应用的整体视觉风格",
                            items = listOf("经典", "液态玻璃"),
                            selectedIndex = appStyle,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { settingsStore.setAppStyle(it) },
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(text = "莫奈取色")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "莫奈取色",
                            summary = "从系统壁纸提取主题色",
                            checked = monetColor,
                            onCheckedChange = { settingsStore.setMonetColor(it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One appearance-mode preview card: full-width screenshot image only.
 * Selection is indicated by the primary-color border, no text or checkmark.
 */
@Composable
private fun AppearanceCard(
    drawableRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(6.dp),
        onClick = onClick,
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(480f / 680f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected)
                        MiuixTheme.colorScheme.primary
                    else
                        Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
        )
    }
}
