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
 * @param cardColor     optional detail-card background color stored as ARGB Long;
 *                      null means the Miuix default Card color.
 * @param wallpaperUri  optional content URI of a gallery image used as the
 *                      detail-page background (shown blurred, frosted-glass card).
 * @param wallpaperBlur wallpaper blur radius in dp (0..50); null = default 28.
 * @param wallpaperDim  dark scrim alpha over the wallpaper (0..0.8); null = 0.35.
 * @param cardBlur      frosted-glass card blur radius (0..120); null = 60.
 * @param cardOpacity   extra surface-tint opacity layered over the glass card
 *                      (0..1, 0 = fully transparent glass); null = 0.
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
    val wallpaperBlur: Int? = null,
    val wallpaperDim: Float? = null,
    val cardBlur: Float? = null,
    val cardOpacity: Float? = null,
)
