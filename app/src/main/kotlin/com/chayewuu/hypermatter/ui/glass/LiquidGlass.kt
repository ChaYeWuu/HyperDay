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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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
 * Scale applied to tab content while the pill is pressed (official
 * LocalLiquidBottomTabScale port): grows to 1.2x with pressProgress — but
 * only inside the invisible accent layer sampled by the lens pill.
 */
private val LocalGlassTabScale = staticCompositionLocalOf { { 1f } }

/**
 * Faithful port of the official LiquidBottomTabs (Kyant0/AndroidLiquidGlass
 * catalog, Apache-2.0) — the press refraction is the whole point:
 *
 * - [DampedDragAnimation]: pressProgress ramps 0→1 while pressed and the
 *   pill's lens refraction / highlight / shadow / inner shadow all scale
 *   with it; the pill stretches to 78/56 and squashes with drag velocity.
 * - [InteractiveHighlight]: a white radial glow follows the finger.
 * - An invisible accent-tinted copy of the tab row is recorded into a
 *   second backdrop; the pill samples the combined backdrop, so the
 *   selected tab content is refracted (enlarged + tinted) through the lens.
 * - The whole capsule scales slightly while pressed and shifts a few
 *   pixels with the drag.
 */
@Composable
fun LiquidGlassTabBar(
    backdrop: LayerBackdrop,
    tabs: List<NavigationItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    actionVisible: Boolean = true,
) {
    val contentColor = if (isDarkTheme) Color.White else Color.Black
    val accentColor = MiuixTheme.colorScheme.primary
    val containerColor =
        if (isDarkTheme) Color(0xFF121212).copy(alpha = 0.4f)
        else Color(0xFFFAFAFA).copy(alpha = 0.4f)

    val view = LocalView.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    val tabsBackdrop = rememberLayerBackdrop()

    // When an action button is docked to the right, the capsule shares the
    // row with it (weight 1f); otherwise the capsule spans the full width.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            val density = LocalDensity.current
            val tabWidth = with(density) {
                (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabs.size
            }

            // The whole capsule shifts subtly with the drag.
            val offsetAnimation = remember { Animatable(0f) }
            val panelOffset by remember(density) {
                derivedStateOf {
                    val fraction = (offsetAnimation.value / constraints.maxWidth).coerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                    }
                }
            }

            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val animationScope = rememberCoroutineScope()
            var currentIndex by remember { mutableIntStateOf(selected) }
            val dampedDragAnimation = remember(animationScope) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = selected.toFloat(),
                    valueRange = 0f..(tabs.size - 1).toFloat(),
                    visibilityThreshold = 0.001f,
                    initialScale = 1f,
                    pressedScale = 78f / 56f,
                    onDragStarted = {},
                    onDragStopped = {
                        val targetIndex = targetValue.roundToInt().coerceIn(0, tabs.size - 1)
                        val changed = targetIndex != currentIndex
                        currentIndex = targetIndex
                        animateToValue(targetIndex.toFloat())
                        animationScope.launch {
                            offsetAnimation.animateTo(
                                0f,
                                spring(1f, 300f, 0.5f)
                            )
                        }
                        if (changed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onDrag = { _, dragAmount ->
                        updateValue(
                            (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                                .coerceIn(0f, (tabs.size - 1).toFloat())
                        )
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                )
            }
            // Sync from the external selection (e.g. pager swipe).
            LaunchedEffect(selected) {
                currentIndex = selected
            }
            // Animate the pill on internal changes (click / drag release)
            // and notify the host — but only when the change is internal,
            // so external pager swipes don't re-trigger a page animation.
            LaunchedEffect(dampedDragAnimation) {
                snapshotFlow { currentIndex }
                    .drop(1)
                    .collectLatest { index ->
                        dampedDragAnimation.animateToValue(index.toFloat())
                        if (index != currentSelected) currentOnSelect(index)
                    }
            }

            val interactiveHighlight = remember(animationScope) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, offset ->
                        Offset(
                            if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                            size.height / 2f
                        )
                    }
                )
            }

            val onTabClick: (Int) -> Unit = { index ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                currentIndex = index
            }
            val tabRow: @Composable RowScope.() -> Unit = {
                tabs.forEachIndexed { index, item ->
                    GlassTab(
                        item = item,
                        contentColor = contentColor,
                        onClick = { onTabClick(index) },
                    )
                }
            }

            // 1) Visible capsule: frost + refract the content beneath.
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabRow,
            )

            // 2) Invisible accent-tinted copy of the tab row, recorded into
            // tabsBackdrop — the pill refracts this layer, which is what
            // makes the selected content appear tinted and enlarged inside
            // the lens while pressed.
            CompositionLocalProvider(
                LocalGlassTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedCornerShape(50) },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(
                                    24.dp.toPx() * progress,
                                    24.dp.toPx() * progress
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabRow,
                )
            }

            // 3) The draggable refracting pill. All of its glass properties
            // scale with pressProgress — the press refraction.
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { RoundedCornerShape(50) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10.dp.toPx() * progress,
                                14.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8.dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isDarkTheme) Color.White.copy(0.1f)
                                else Color.Black.copy(0.1f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .height(56.dp)
                    .fillMaxWidth(1f / tabs.size)
            )
        }

        // Docked action (e.g. the + add button) sharing the bar row. It
        // expands/collapses with [actionVisible] — the capsule (weight 1f)
        // grows back to full width as the action shrinks away.
        if (action != null) {
            AnimatedVisibility(
                visible = actionVisible,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    action()
                }
            }
        }
    }
}

/**
 * Circular glass action button docked next to the liquid glass tab bar.
 * Uses the exact same glass recipe as the capsule (vibrancy + 8dp blur +
 * 24/24 lens over the shared [backdrop]) but tinted with the theme primary
 * — the same look the old in-page glass FAB had — so the affordance reads
 * as the primary action.
 */
@Composable
fun GlassNavAction(
    icon: ImageVector,
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
) {
    val view = LocalView.current
    // Primary-tinted glass (matches the previous GlassFab look), not the
    // neutral capsule tint — otherwise the button looks washed-out white.
    val tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(50) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(24.dp.toPx(), 24.dp.toPx())
                },
                // Explicit small shadow — the library default is a large
                // Shadow.Default (24dp) which the expand/collapse animation
                // can clip into a rectangular artifact.
                shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.08f)) },
                onDrawSurface = { drawRect(tint) },
            )
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Single tab inside the glass bar: icon + label, no ripple (official). */
@Composable
private fun RowScope.GlassTab(
    item: NavigationItem,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalGlassTabScale.current
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            color = contentColor,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}
