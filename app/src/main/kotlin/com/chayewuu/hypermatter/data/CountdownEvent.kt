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
 * @param cardColor optional detail-card background color stored as ARGB Long;
 *                  null means the Miuix default Card color.
 */
@Serializable
data class CountdownEvent(
    val id: String,
    val title: String,
    val epochDay: Long,
    val note: String? = null,
    val isPast: Boolean? = null,
    val cardColor: Long? = null,
)
