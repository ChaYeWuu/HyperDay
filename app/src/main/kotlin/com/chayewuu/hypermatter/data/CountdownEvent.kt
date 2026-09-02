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
 * @param textColor    0 = auto-adaptive, 1 = white, 2 = dark, 3 = custom
 *                     (null = default). Also drives the action buttons.
 * @param textColorCustom  ARGB used when textColor == 3 (null = white).
 * @param fontStroke   contrast outline on the card texts (null = off).
 * @param fontStrokeWidth outline width in dp (null = 2.5).
 * @param strokeColor  0 = auto (contrast), 1 = white, 2 = black, 3 = custom.
 * @param strokeColorCustom ARGB used when strokeColor == 3.
 * @param fontShadow   drop shadow on the card texts (null = off).
 * @param shadowColor  0 = auto (contrast), 1 = white, 2 = black, 3 = custom.
 * @param shadowColorCustom ARGB used when shadowColor == 3.
 * @param shadowBlur   shadow blur radius in dp (null = 8).
 * @param shadowAlpha  shadow opacity 0..1 (null = 0.45).
 * @param repeatType      0/NULL = one-off; 1 = daily; 2 = weekly;
 *                        3 = monthly; 4 = yearly (solar); 5 = yearly lunar.
 *                        Recurring events always count down to their next
 *                        occurrence (never "past").
 * @param lunarMonth      For repeatType 5: lunar month of the anniversary.
 * @param lunarDay        For repeatType 5: lunar day of the anniversary.
 * @param repeatWeekday   For repeatType 2: weekday 1(周一)..7(周日); null =
 *                        derive from epochDay (legacy events).
 * @param repeatMonthDay  For repeatType 3/4: day of month 1..31; null =
 *                        derive from epochDay.
 * @param repeatYearMonth For repeatType 4: month 1..12; null = derive from
 *                        epochDay.
 * @param timeHour        For repeatType 1: hour of day 0..23 (display only).
 * @param timeMinute      For repeatType 1: minute 0..59 (display only).
 */
@Serializable
data class CountdownEvent(
    val id: String,
    val title: String,
    val epochDay: Long,
    val note: String? = null,
    val isPast: Boolean? = null,
    /** Category id (CategoryStore); null = uncategorized. */
    val category: String? = null,
    val repeatType: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val repeatWeekday: Int? = null,
    val repeatMonthDay: Int? = null,
    val repeatYearMonth: Int? = null,
    val timeHour: Int? = null,
    val timeMinute: Int? = null,
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
    val textColorCustom: Long? = null,
    val fontStroke: Boolean? = null,
    val fontStrokeWidth: Float? = null,
    val strokeColor: Int? = null,
    val strokeColorCustom: Long? = null,
    val fontShadow: Boolean? = null,
    val shadowColor: Int? = null,
    val shadowColorCustom: Long? = null,
    val shadowBlur: Float? = null,
    val shadowAlpha: Float? = null,
)
