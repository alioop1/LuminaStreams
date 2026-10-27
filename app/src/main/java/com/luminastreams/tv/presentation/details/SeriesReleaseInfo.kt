package com.luminastreams.tv.presentation.details

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "Is the next episode out yet?" — everything the details screen needs to answer that,
 * derived from TMDB's `status` / `next_episode_to_air` / `last_episode_to_air`.
 */

enum class SeriesReleaseState {
    /** Next episode has a known future air date — show a countdown. */
    PREMIERES_SOON,
    /** A new episode dropped within the last few days. */
    AVAILABLE_NOW,
    /** Renewed / in production, but TMDB has no date yet. */
    RETURNING_NO_DATE,
    /** Series finished airing. */
    ENDED,
    /** Series was cut short. */
    CANCELED,
    /** Not a series, or nothing useful known. */
    UNKNOWN,
}

@Immutable
data class NextEpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    /** yyyy-MM-dd, or "" when unknown. */
    val airDateIso: String,
    val stillUrl: String = "",
) {
    val airDate: LocalDate? get() = parseIsoDate(airDateIso)

    /** Whole days from *today* until the air date. Negative = already aired. null = unknown. */
    val daysUntilAir: Long? get() = airDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    val isReleased: Boolean get() = (daysUntilAir ?: Long.MAX_VALUE) <= 0L

    val label: String get() = "S$seasonNumber · E$episodeNumber"
}

/** Parse a leading `yyyy-MM-dd` out of an ISO date or datetime string. */
fun parseIsoDate(iso: String?): LocalDate? {
    val head = iso?.trim()?.take(10) ?: return null
    if (head.length != 10) return null
    return runCatching { LocalDate.parse(head) }.getOrNull()
}

fun resolveSeriesReleaseState(
    isSeries: Boolean,
    tmdbStatus: String,
    next: NextEpisodeInfo?,
    lastAiredIso: String,
): SeriesReleaseState {
    if (!isSeries) return SeriesReleaseState.UNKNOWN

    if (next != null) {
        val d = next.daysUntilAir
        return when {
            d == null -> SeriesReleaseState.RETURNING_NO_DATE
            d > 0L -> SeriesReleaseState.PREMIERES_SOON
            d >= -3L -> SeriesReleaseState.AVAILABLE_NOW      // aired in the last 3 days
            else -> SeriesReleaseState.RETURNING_NO_DATE       // stale pointer, treat as "between episodes"
        }
    }

    val lastAiredDaysAgo = parseIsoDate(lastAiredIso)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }
    if (lastAiredDaysAgo != null && lastAiredDaysAgo in 0L..3L) return SeriesReleaseState.AVAILABLE_NOW

    return when (tmdbStatus.trim().lowercase()) {
        "ended" -> SeriesReleaseState.ENDED
        "canceled", "cancelled" -> SeriesReleaseState.CANCELED
        "returning series", "in production", "planned", "pilot" -> SeriesReleaseState.RETURNING_NO_DATE
        else -> SeriesReleaseState.UNKNOWN
    }
}

/** Big countdown headline, e.g. "TOMORROW", "IN 4 DAYS", "IN 2 WEEKS". */
fun countdownHeadline(days: Long?, hebrew: Boolean): String = when {
    days == null -> if (hebrew) "בקרוב" else "SOON"
    days <= 0L -> if (hebrew) "עכשיו" else "NOW"
    days == 1L -> if (hebrew) "מחר" else "TOMORROW"
    days < 7L -> if (hebrew) "בעוד $days ימים" else "IN $days DAYS"
    days < 14L -> if (hebrew) "בעוד שבוע" else "IN 1 WEEK"
    days < 31L -> if (hebrew) "בעוד ${days / 7} שבועות" else "IN ${days / 7} WEEKS"
    days < 365L -> if (hebrew) "בעוד ${(days / 30).coerceAtLeast(1)} חודשים" else "IN ${(days / 30).coerceAtLeast(1)} MONTHS"
    else -> if (hebrew) "בעוד שנה+" else "IN 1+ YEAR"
}

/** "Fri · Mar 14" style date line. */
fun airDateLine(date: LocalDate?, hebrew: Boolean): String {
    if (date == null) return ""
    val locale = if (hebrew) Locale.forLanguageTag("he") else Locale.ENGLISH
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val month = date.month.getDisplayName(TextStyle.SHORT, locale)
    return "$dow · $month ${date.dayOfMonth}"
}

/** Short "AIRS Mar 14" pill text for an unreleased episode row. */
fun airsPillText(iso: String, hebrew: Boolean): String {
    val date = parseIsoDate(iso) ?: return if (hebrew) "טרם שודר" else "NOT AIRED"
    val locale = if (hebrew) Locale.forLanguageTag("he") else Locale.ENGLISH
    val month = date.month.getDisplayName(TextStyle.SHORT, locale)
    val prefix = if (hebrew) "משודר" else "AIRS"
    return "$prefix $month ${date.dayOfMonth}"
}
