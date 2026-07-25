package org.akkirrai.hibiki.shared.model

import kotlin.math.round

private val CARD_META_SPLIT_REGEX = Regex("\\s*[•·|]\\s*")
private val CARD_META_YEAR_REGEX = Regex("\\d{4}")

fun Anime.buildCardMeta(
    announcementLabel: String,
    movieLabel: String = "Movie",
    maxSubtitleParts: Int = 2,
    separator: String = " • ",
): String {
    if (isAnnouncement()) return announcementLabel

    val subtitleParts = subtitle
        .split(CARD_META_SPLIT_REGEX)
        .map(String::trim)
        .filter { it.isNotEmpty() && it != "Unknown" }
        .take(maxSubtitleParts)

    val type = subtitleParts
        .firstOrNull { !it.matches(CARD_META_YEAR_REGEX) }
        ?.toCardTypeLabel(movieLabel)
    val year = subtitleParts.firstOrNull { it.matches(CARD_META_YEAR_REGEX) }
    val rating = ratings.firstNotNullOfOrNull { item ->
        item.value.takeIf { it.isFinite() && it > 0.0 }
    }
    val ratingLabel = rating?.let { "${formatOneDecimal(it)} ★" }

    return listOfNotNull(type, year, ratingLabel).joinToString(separator)
}

fun Anime.isAnnouncement(): Boolean {
    val values = listOf(status, episodesLabel).map { it.trim().lowercase() }
    return values.any { it == "анонс" || it == "announcement" || it == "announced" || it == "anons" }
}

private fun formatOneDecimal(value: Double): String {
    val roundedTenths = round(value * 10.0).toLong()
    return "${roundedTenths / 10}.${kotlin.math.abs(roundedTenths % 10)}"
}

private fun String.toCardTypeLabel(movieLabel: String): String? {
    val normalized = trim().lowercase().replace('_', ' ').replace('-', ' ')
    return when (normalized) {
        "tv", "tv series", "tv short", "short serial", "serial", "сериал" -> "TV"
        "ona" -> "ONA"
        "ova" -> "OVA"
        "movie", "short movie", "film", "полнометражный фильм", "короткометражный фильм" -> "MOVIE"
        "special", "спэшл" -> "SPECIAL"
        else -> movieLabel.takeIf { normalized.contains("movie") || normalized.contains("фильм") }
    }
}
