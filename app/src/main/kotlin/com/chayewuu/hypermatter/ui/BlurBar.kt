// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Official Miuix example blur helpers (utils/PageUtils.kt), ported for this
 * app: a top bar with a progressive (gradient) backdrop blur, strongest at
 * the status-bar edge and fading to clear at the bar's bottom edge.
 *
 * Usage pattern:
 * ```
 * val backdrop = rememberBlurBackdrop()
 * Scaffold(
 *     topBar = {
 *         BlurredBar(backdrop) {
 *             SmallTopAppBar(title = ..., color = if (backdrop != null) Color.Transparent else surface)
 *         }
 *     },
 * ) { padding ->
 *     Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
 *         // scrollable content that slides under the bar
 *     }
 * }
 * ```
 */
@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Wraps a top app bar with the official progressive blur: full-strength blur
 * at the top edge ramping to pixel-sharp at the bottom edge (curve 2.2f,
 * radius 10dp, surface 30% scrim — the official example's progressive-bar
 * preset). Content must be recorded into [backdrop] via
 * `Modifier.layerBackdrop(backdrop)`.
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (backdrop != null) {
            Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                blurRadius = 10f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.3f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
