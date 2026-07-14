package org.akkirrai.hibiki.core.model

import java.util.Locale

private val META_SPLIT_REGEX = Regex("\\s*[•·|]\\s*")

fun Anime.buildCardMeta(
    announcementLabel: String,
    movieLabel: String = "Movie",
    maxSubtitleParts: Int = 2,
    separator: String = " • ",
): String {
    if (isAnnouncement()) return announcementLabel

    val subtitleParts = subtitle
        .split(META_SPLIT_REGEX)
        .map(String::trim)
        .filter { it.isNotEmpty() && it != UNKNOWN_META_VALUE }
        .take(maxSubtitleParts)

    val type = subtitleParts
        .firstOrNull { !it.matches(YEAR_REGEX) }
        ?.toCardTypeLabel(movieLabel)
    val year = subtitleParts.firstOrNull { it.matches(YEAR_REGEX) }
    val rating = ratings
        .firstOrNull { it.source.contains("yummy", ignoreCase = true) }
        ?.value
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: ratings.firstOrNull()?.value?.takeIf { it.isFinite() && it > 0.0 }
    val ratingLabel = rating?.let { String.format(Locale.US, "%.1f ★", it) }

    return listOfNotNull(type, year, ratingLabel).joinToString(separator)
}

fun Anime.buildLibraryMeta(
    maxSubtitleParts: Int = 2,
    separator: String = " • ",
): String {
    val subtitleParts = subtitle
        .split(META_SPLIT_REGEX)
        .map(String::trim)
        .filter { it.isNotEmpty() && it != UNKNOWN_META_VALUE }
        .take(maxSubtitleParts)

    return subtitleParts
        .filter { it.isNotBlank() }
        .joinToString(separator)
}

fun Anime.isAnnouncement(): Boolean {
    val values = listOf(status, episodesLabel).map { it.trim().lowercase() }
    return values.any { it == "анонс" || it == "announcement" || it == "announced" || it == "anons" }
}

private const val UNKNOWN_META_VALUE = "Unknown"
private val YEAR_REGEX = Regex("\\d{4}")

private fun String.toCardTypeLabel(movieLabel: String): String? {
    val normalized = trim().lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ')
    return when (normalized) {
        "tv", "tv series", "tv short", "short serial", "serial", "сериал" -> "TV"
        "ona" -> "ONA"
        "ova" -> "OVA"
        "movie", "short movie", "film", "полнометражный фильм", "короткометражный фильм" -> "MOVIE"
        "special", "спэшл" -> "SPECIAL"
        else -> movieLabel.takeIf {
            normalized.contains("movie") || normalized.contains("фильм")
        }
    }
}
