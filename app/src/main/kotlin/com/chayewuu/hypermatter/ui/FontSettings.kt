package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.CountdownEvent
import top.yukonga.miuix.kmp.basic.Text

/**
 * Per-event typography for the detail-page card texts (the 字体 dialog).
 *  - [scale]: size multiplier (0.8..1.6).
 *  - [weightIdx]: 0 = per-element default, 1 = 常规, 2 = 中等, 3 = 粗体.
 *  - [colorIdx]: 0 = auto (adaptive to the background), 1 = white,
 *    2 = dark, 3 = custom ([colorCustom]). Also drives the action buttons.
 *  - [strokeOn]/[strokeWidthDp]/[strokeColorIdx]/[strokeColorCustom]:
 *    contrast outline drawn behind the fill.
 *  - [shadowOn]/[shadowColorIdx]/[shadowColorCustom]/[shadowBlurDp]/
 *    [shadowAlpha]: soft drop shadow.
 * Derived from [CountdownEvent]'s font* fields; defaults when null.
 */
data class FontSettings(
    val scale: Float = 1f,
    val weightIdx: Int = 0,
    val colorIdx: Int = 0,
    val colorCustom: Color = Color.White,
    val strokeOn: Boolean = false,
    val strokeWidthDp: Float = 2.5f,
    val strokeColorIdx: Int = 0,
    val strokeColorCustom: Color = Color.White,
    val shadowOn: Boolean = false,
    val shadowColorIdx: Int = 0,
    val shadowColorCustom: Color = Color.Black,
    val shadowBlurDp: Float = 8f,
    val shadowAlpha: Float = 0.45f,
)

/** Reads an event's font fields into a [FontSettings] snapshot. */
fun CountdownEvent.fontSettings(): FontSettings = FontSettings(
    scale = fontScale ?: 1f,
    weightIdx = fontWeight ?: 0,
    colorIdx = textColor ?: 0,
    colorCustom = textColorCustom?.let { Color(it.toInt()) } ?: Color.White,
    strokeOn = fontStroke ?: false,
    strokeWidthDp = fontStrokeWidth ?: 2.5f,
    strokeColorIdx = strokeColor ?: 0,
    strokeColorCustom = strokeColorCustom?.let { Color(it.toInt()) } ?: Color.White,
    shadowOn = fontShadow ?: false,
    shadowColorIdx = shadowColor ?: 0,
    shadowColorCustom = shadowColorCustom?.let { Color(it.toInt()) } ?: Color.Black,
    shadowBlurDp = shadowBlur ?: 8f,
    shadowAlpha = shadowAlpha ?: 0.45f,
)

/**
 * The effective text color for the given adaptive default — used by the
 * card texts AND the action buttons / back arrow, so a custom font color
 * recolors the whole page chrome consistently.
 */
fun FontSettings.resolvedTextColor(autoColor: Color): Color = when (colorIdx) {
    1 -> Color.White
    2 -> Color(0xFF1B1B1F)
    3 -> colorCustom
    else -> autoColor
}

/** Whether the user picked an explicit (non-adaptive) font color. */
val FontSettings.hasExplicitTextColor: Boolean
    get() = colorIdx != 0

/**
 * A detail-card text with the event's font settings applied: size scale,
 * weight override, color override (or the adaptive [autoColor]), an
 * optional contrast outline behind the glyphs and an optional drop
 * shadow. With the outline enabled the stroke layer and the fill layer
 * are stacked in a Box so both share the same layout.
 *
 * The size scale is purely visual (a graphicsLayer transform around the
 * text's center): the text is always LAID OUT at its base size, so the
 * card's measured geometry never changes while the size slider is being
 * dragged. Callers can opt an element out entirely with
 * [applyScale] = false (used by the big day number, whose 88sp glyphs
 * would visually overflow the card at high scales).
 */
@Composable
fun FancyText(
    text: String,
    autoColor: Color,
    settings: FontSettings,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    fontSize: TextUnit = TextUnit.Unspecified,
    defaultWeight: FontWeight? = null,
    applyScale: Boolean = true,
) {
    val weight = when (settings.weightIdx) {
        1 -> FontWeight.Normal
        2 -> FontWeight.Medium
        3 -> FontWeight.Bold
        else -> defaultWeight
    }
    val fill = settings.resolvedTextColor(autoColor)
    val baseSize = (if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize)
        .takeIf { it != TextUnit.Unspecified }
        ?: 16.sp

    val density = LocalDensity.current
    val shadow = if (settings.shadowOn) {
        val shadowColor = when (settings.shadowColorIdx) {
            1 -> Color.White
            2 -> Color.Black
            3 -> settings.shadowColorCustom
            else -> if (fill.luminance() > 0.5f) Color.Black else Color.White
        }
        with(density) {
            Shadow(
                color = shadowColor.copy(alpha = settings.shadowAlpha),
                offset = Offset(0f, 2.dp.toPx()),
                blurRadius = settings.shadowBlurDp.dp.toPx(),
            )
        }
    } else {
        null
    }
    val base = style.copy(
        color = fill,
        fontSize = baseSize,
        fontWeight = weight,
        shadow = shadow,
    )

    // Layout-stable visual scale: laid out at the base size, scaled around
    // the center at draw time — the card never resizes.
    val scaleModifier = if (applyScale && settings.scale != 1f) {
        Modifier.graphicsLayer {
            scaleX = settings.scale
            scaleY = settings.scale
        }
    } else {
        Modifier
    }

    if (settings.strokeOn) {
        // Contrast outline: light glyphs get a dark rim and vice versa, so
        // the outline is always visible against both the fill and the page.
        val strokeColor = when (settings.strokeColorIdx) {
            1 -> Color.White
            2 -> Color.Black
            3 -> settings.strokeColorCustom
            else -> if (fill.luminance() > 0.5f) Color.Black else Color.White
        }
        val drawStroke = with(density) {
            Stroke(
                width = settings.strokeWidthDp.dp.toPx(),
                join = StrokeJoin.Round,
                cap = StrokeCap.Round,
            )
        }
        Box(modifier = modifier.then(scaleModifier)) {
            Text(text = text, style = base.copy(color = strokeColor, drawStyle = drawStroke))
            Text(text = text, style = base)
        }
    } else {
        Text(text = text, style = base, modifier = modifier.then(scaleModifier))
    }
}
