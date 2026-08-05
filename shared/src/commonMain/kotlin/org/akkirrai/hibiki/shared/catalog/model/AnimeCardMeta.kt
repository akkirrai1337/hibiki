package org.akkirrai.hibiki.shared.catalog.model

import kotlin.math.round

private val CARD_META_SPLIT_REGEX = Regex("\\s*[\\u2022\\u00b7|]\\s*")
private val CARD_META_YEAR_REGEX = Regex("\\d{4}")

fun Anime.buildCardMeta(
    announcementLabel: String,
    movieLabel: String = "Movie",
    maxSubtitleParts: Int = 2,
    separator: String = " \u2022 ",
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
    val ratingLabel = rating?.let { "${formatOneDecimal(it)} \u2605" }

    return listOfNotNull(type, year, ratingLabel).joinToString(separator)
}

fun Anime.isAnnouncement(): Boolean {
    val values = listOf(status, episodesLabel).map { it.trim().lowercase() }
    return values.any { it == "\u0430\u043d\u043e\u043d\u0441" || it == "announcement" || it == "announced" || it == "anons" }
}

private fun formatOneDecimal(value: Double): String {
    val roundedTenths = round(value * 10.0).toLong()
    return "${roundedTenths / 10}.${kotlin.math.abs(roundedTenths % 10)}"
}

private fun String.toCardTypeLabel(movieLabel: String): String? {
    val normalized = trim().lowercase().replace('_', ' ').replace('-', ' ')
    return when (normalized) {
        "tv", "tv series", "tv short", "short serial", "serial", "\u0441\u0435\u0440\u0438\u0430\u043b" -> "TV"
        "ona" -> "ONA"
        "ova" -> "OVA"
        "movie", "short movie", "film", "\u043f\u043e\u043b\u043d\u043e\u043c\u0435\u0442\u0440\u0430\u0436\u043d\u044b\u0439 \u0444\u0438\u043b\u044c\u043c", "\u043a\u043e\u0440\u043e\u0442\u043a\u043e\u043c\u0435\u0442\u0440\u0430\u0436\u043d\u044b\u0439 \u0444\u0438\u043b\u044c\u043c" -> "MOVIE"
        "special", "\u0441\u043f\u044d\u0448\u043b" -> "SPECIAL"
        else -> movieLabel.takeIf { normalized.contains("movie") || normalized.contains("\u0444\u0438\u043b\u044c\u043c") }
    }
}
