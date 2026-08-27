package org.akkirrai.hibiki.core.model

/**
 * Small, closed set of broadcast states every source's free-text status string can be mapped
 * into, unlike genres -- an open, source-specific vocabulary no fixed mapping can keep up with.
 */
enum class AnimeStatusCategory {
    Ongoing,
    Announced,
    Released,
}

fun classifyAnimeStatus(status: String): AnimeStatusCategory {
    val normalized = status.trim().lowercase()
    return when {
        ONGOING_KEYWORDS.any(normalized::contains) -> AnimeStatusCategory.Ongoing
        ANNOUNCED_KEYWORDS.any(normalized::contains) -> AnimeStatusCategory.Announced
        else -> AnimeStatusCategory.Released
    }
}

private val ONGOING_KEYWORDS = listOf(
    "ongoing", "airing", "releasing",
    "онгоинг", "онґоінг", "выходит", "триває",
)

private val ANNOUNCED_KEYWORDS = listOf(
    "announcement", "announced", "anons", "not yet released", "not_yet_released",
    "анонс",
)
