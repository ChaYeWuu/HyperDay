package com.chayewuu.hypermatter.data

import kotlinx.serialization.Serializable

/**
 * A countdown event.
 *
 * @param id        stable identifier (UUID string)
 * @param title     user-visible event title
 * @param epochDay  target date as epoch-day value (days since 1970-01-01).
 *                  Using epoch day avoids timezone ambiguity and serializes as a plain Long.
 * @param note      optional note shown under the title.
 * @param isPast    when true, this event counts "days since"; when false, "days until".
 *                  If null, the direction is auto-calculated from today's date.
 * @param cardColor    legacy detail-card color (kept for storage compat; no
 *                     longer selectable in the UI).
 * @param wallpaperUri optional content URI of a gallery image used as the
 *                     detail-page background (blurred, adaptive glass card).
 * @param dynamicBg    when true (and no wallpaper), the detail page uses the
 *                     official dynamic color-blending background.
 * @param fontScale    detail-page text size multiplier (null = default 1f).
 * @param fontWeight   0 = per-element default, 1 = 常规, 2 = 中等, 3 = 粗体
 *                     (null = default).
 * @param textColor    0 = auto-adaptive, 1 = white, 2 = dark (null = default).
 * @param fontStroke   contrast outline on the card texts (null = off).
 * @param fontStrokeWidth outline width in dp (null = 2.5).
 * @param fontShadow   drop shadow on the card texts (null = off).
 */
@Serializable
data class CountdownEvent(
    val id: String,
    val title: String,
    val epochDay: Long,
    val note: String? = null,
    val isPast: Boolean? = null,
    val cardColor: Long? = null,
    val wallpaperUri: String? = null,
    val dynamicBg: Boolean? = null,
    val wallpaperBlur: Int? = null,
    val wallpaperDim: Float? = null,
    val cardBlur: Float? = null,
    val cardOpacity: Float? = null,
    val fontScale: Float? = null,
    val fontWeight: Int? = null,
    val textColor: Int? = null,
    val fontStroke: Boolean? = null,
    val fontStrokeWidth: Float? = null,
    val fontShadow: Boolean? = null,
)
