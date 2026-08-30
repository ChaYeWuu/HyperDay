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

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** Whether the Liquid Glass app style is active (appStyle=1 + RenderEffect). */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/**
 * The backdrop recording page content for glass surfaces, null in classic
 * mode. Provided by the page, typically recorded from a flat-canvas sibling
 * (see [GlassCanvasRecorder]).
 *
 * IMPORTANT wiring rule (from the official catalog usage): a glass surface
 * must NEVER live inside the subtree that records its own sample — the
 * recorded layer would contain a draw of the glass surface, which draws the
 * recorded layer, nesting the render tree one level deeper every frame
 * until the renderer overflows its native stack (prepareTree recursion).
 * Always record the background/content and keep glass surfaces as siblings
 * (or in a different Scaffold slot, e.g. the bottom bar).
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Creates the liquid-glass sampling backdrop, or null when the style is
 * off / unsupported. The recorded layer is pre-filled with the canvas color
 * so glass surfaces over empty canvas blur the page color instead of
 * transparency.
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
 * Backdrop recording the live page content — for glass surfaces that
 * overlay scrolling content (e.g. the bottom navigation bar, which lives in
 * its own Scaffold slot outside the recorded subtree). Do NOT attach the
 * resulting recorder to a subtree that itself contains glass sampling it.
 */
@Composable
fun rememberContentGlassBackdrop(): LayerBackdrop? {
    if (!LocalGlassEnabled.current || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Flat-canvas recorder node: records only the page color (draws nothing
 * visible), giving glass surfaces a clean page sample. Pages place this as
 * a sibling BEHIND their content so glass cards/FABs can sample the canvas
 * without being part of the recording themselves.
 */
@Composable
fun GlassCanvasRecorder(backdrop: LayerBackdrop?) {
    if (backdrop == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop),
    )
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
 * Official-catalog-style liquid glass bottom tab bar (LiquidBottomTabs):
 * a floating capsule that frosts + refracts the content scrolling beneath
 * it, with a refracting lens pill that slides under the selected tab.
 */
@Composable
fun LiquidGlassTabBar(
    backdrop: LayerBackdrop,
    tabs: List<NavigationItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
) {
    val containerColor =
        if (isDarkTheme) Color(0xFF121212).copy(alpha = 0.4f)
        else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val pillScrim =
        if (isDarkTheme) Color.White.copy(alpha = 0.1f)
        else Color.Black.copy(alpha = 0.1f)
    val accent = MiuixTheme.colorScheme.primary
    val summary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        val tabWidth = (maxWidth - 8.dp) / tabs.size
        val density = LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }

        // The lens pill is draggable (official LiquidBottomTabs behavior):
        // it follows the finger while dragging, then springs onto the
        // nearest tab on release.
        val pillAnim = remember { Animatable(0f) }
        var isDragging by remember { mutableStateOf(false) }
        // Tab highlighted while dragging (follows the pill), else the
        // selected one.
        val hoverIndex = if (isDragging)
            (pillAnim.value / tabWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
        else selected

        // Keep the pill parked under the selected tab (animated, so a
        // release-fling lands smoothly from the dragged position).
        LaunchedEffect(selected, tabs.size) {
            if (!isDragging) {
                pillAnim.animateTo(
                    targetValue = tabWidthPx * selected,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
                )
            }
        }
        val pillOffset = with(density) { pillAnim.value.toDp() }

        // Floating capsule bar: vibrancy + blur + lens over the recorded
        // content, lightly tinted for icon readability. The whole capsule
        // is horizontally draggable to fling the selection pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(tabs.size) {
                    val maxOffset = tabWidthPx * (tabs.size - 1)
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            val target = (pillAnim.value / tabWidthPx)
                                .roundToInt()
                                .coerceIn(0, tabs.lastIndex)
                            if (target != selected) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(target)
                            } else {
                                // Snap back even when the tab didn't change.
                                scope.launch {
                                    pillAnim.animateTo(
                                        targetValue = tabWidthPx * target,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            scope.launch {
                                pillAnim.animateTo(
                                    targetValue = tabWidthPx * selected,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
                                )
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            pillAnim.snapTo(
                                (pillAnim.value + dragAmount).coerceIn(0f, maxOffset)
                            )
                        }
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(50) },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    shadow = { Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.15f)) },
                    onDrawSurface = { drawRect(containerColor) },
                ),
        ) {
            // Refracting selection pill (official recipe: lens with
            // dispersion + highlight + shadow + inner shadow).
            Box(
                modifier = Modifier
                    .offset(x = 4.dp + pillOffset, y = 4.dp)
                    .width(tabWidth)
                    .height(56.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true)
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.15f)) },
                        innerShadow = { InnerShadow(radius = 8.dp, alpha = 1f) },
                        onDrawSurface = { drawRect(pillScrim) },
                    ),
            )

            // Tabs: icon + label, tinted with the accent when highlighted.
            // Clicks use no ripple indication — the glass pill is the
            // selection feedback (a gray ripple block over glass looks wrong).
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                tabs.forEachIndexed { index, item ->
                    val isHighlighted = index == hoverIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(index)
                            },
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isHighlighted) accent else summary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = item.label,
                            color = if (isHighlighted) accent else summary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                }
            }
        }
    }
}
