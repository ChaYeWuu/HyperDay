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
 *  - [colorIdx]: 0 = auto (adaptive to the background), 1 = white, 2 = dark.
 *  - [strokeOn]/[strokeWidthDp]: contrast outline drawn behind the fill.
 *  - [shadowOn]: soft drop shadow.
 * Derived from [CountdownEvent]'s font* fields; defaults when null.
 */
data class FontSettings(
    val scale: Float = 1f,
    val weightIdx: Int = 0,
    val colorIdx: Int = 0,
    val strokeOn: Boolean = false,
    val strokeWidthDp: Float = 2.5f,
    val shadowOn: Boolean = false,
)

/** Reads an event's font fields into a [FontSettings] snapshot. */
fun CountdownEvent.fontSettings(): FontSettings = FontSettings(
    scale = fontScale ?: 1f,
    weightIdx = fontWeight ?: 0,
    colorIdx = textColor ?: 0,
    strokeOn = fontStroke ?: false,
    strokeWidthDp = fontStrokeWidth ?: 2.5f,
    shadowOn = fontShadow ?: false,
)

/**
 * A detail-card text with the event's font settings applied: size scale,
 * weight override, color override (or the adaptive [autoColor]), an
 * optional contrast outline behind the glyphs and an optional drop
 * shadow. With the outline enabled the stroke layer and the fill layer
 * are stacked in a Box so both share the same layout.
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
) {
    val weight = when (settings.weightIdx) {
        1 -> FontWeight.Normal
        2 -> FontWeight.Medium
        3 -> FontWeight.Bold
        else -> defaultWeight
    }
    val fill = when (settings.colorIdx) {
        1 -> Color.White
        2 -> Color(0xFF1B1B1F)
        else -> autoColor
    }
    val baseSize = (if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize)
        .takeIf { it != TextUnit.Unspecified }
        ?: 16.sp

    val density = LocalDensity.current
    val shadow = if (settings.shadowOn) {
        with(density) {
            Shadow(
                color = Color.Black.copy(alpha = 0.45f),
                offset = Offset(0f, 2.dp.toPx()),
                blurRadius = 8.dp.toPx(),
            )
        }
    } else {
        null
    }
    val base = style.copy(
        color = fill,
        fontSize = baseSize * settings.scale,
        fontWeight = weight,
        shadow = shadow,
    )

    if (settings.strokeOn) {
        // Contrast outline: light glyphs get a dark rim and vice versa, so
        // the outline is always visible against both the fill and the page.
        val strokeColor = if (fill.luminance() > 0.5f) Color.Black else Color.White
        val drawStroke = with(density) {
            Stroke(
                width = settings.strokeWidthDp.dp.toPx(),
                join = StrokeJoin.Round,
                cap = StrokeCap.Round,
            )
        }
        Box(modifier = modifier) {
            Text(text = text, style = base.copy(color = strokeColor, drawStyle = drawStroke))
            Text(text = text, style = base)
        }
    } else {
        Text(text = text, style = base, modifier = modifier)
    }
}
