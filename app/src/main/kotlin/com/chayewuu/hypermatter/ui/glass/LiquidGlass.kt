// Liquid Glass integration for the HyperDay app style switch (appStyle=1).
//
// Built on Kyant0/AndroidLiquidGlass (io.github.kyant0:backdrop): every glass
// surface records the page content into a LayerBackdrop
// (`Modifier.layerBackdrop`) and then redraws a blurred + refracted sample of
// it clipped to its own shape (`Modifier.drawBackdrop` with vibrancy + blur +
// lens effects). When the app style is "classic" (or the device cannot do
// RenderEffect), every component here falls back to the standard Miuix
// counterpart, so pages can use them unconditionally.

package com.chayewuu.hypermatter.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Whether the Liquid Glass app style is active (appStyle=1 + RenderEffect). */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/**
 * The backdrop recording page content for glass surfaces, null in classic
 * mode. Provided by the page's Scaffold content Box, which attaches
 * `Modifier.layerBackdrop(backdrop)` on itself.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Creates the liquid-glass sampling backdrop, or null when the style is
 * off / unsupported. Mirrors the Miuix blur helper: the recorded layer is
 * pre-filled with the canvas color so glass surfaces over empty canvas blur
 * the page color instead of transparency.
 */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop? {
    if (!LocalGlassEnabled.current || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Core glass surface modifier: samples [backdrop] behind the caller,
 * blurred + saturated + refracted through the given [shape], with a light
 * [tint] drawn on top of the sample for content readability.
 */
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurRadius: Dp,
    tint: Color,
    lensHeight: Dp = 14.dp,
    lensAmount: Dp = 20.dp,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(lensHeight.toPx(), lensAmount.toPx())
    },
    shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.08f)) },
    onDrawSurface = { drawRect(tint) },
)

/**
 * Miuix-Card-shaped liquid glass card. Falls back to the standard Miuix
 * [Card] when glass is disabled, keeping the same corner radius (16dp) and
 * inside-margin defaults.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        if (onClick != null) {
            Card(
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                onClick = onClick,
            ) { content() }
        } else {
            Card(
                modifier = modifier,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
            ) { content() }
        }
        return
    }
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                shape = shape,
                blurRadius = 18.dp,
                tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.25f),
            )
            // Bound the click ripple to the same rounded shape.
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(insideMargin),
        content = content,
    )
}

/**
 * Liquid glass floating action button (tinted with the theme primary so the
 * affordance stays visible on glass). Falls back to the Miuix FAB when glass
 * is disabled.
 */
@Composable
fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f),
    content: @Composable () -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        FloatingActionButton(onClick = onClick, modifier = modifier) { content() }
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                // RoundedCornerShape(50) (percent) — a CornerBasedShape the
                // lens effect supports (plain CircleShape is an oval and
                // would throw).
                shape = RoundedCornerShape(50),
                blurRadius = 8.dp,
                tint = tint,
                lensHeight = 12.dp,
                lensAmount = 16.dp,
            )
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 60.dp, minHeight = 60.dp),
    ) { content() }
}

/**
 * Liquid glass bottom navigation bar: the standard Miuix NavigationBar with
 * a transparent background wrapped in a top-rounded glass surface that
 * frosts whatever scrolls beneath it. Insets/height/selection behavior all
 * come from the Miuix bar.
 */
@Composable
fun GlassNavigationBar(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            backdrop = backdrop,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            blurRadius = 10.dp,
            tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.35f),
            lensHeight = 22.dp,
            lensAmount = 12.dp,
        ),
    ) {
        NavigationBar(
            color = Color.Transparent,
            showDivider = false,
            content = content,
        )
    }
}
