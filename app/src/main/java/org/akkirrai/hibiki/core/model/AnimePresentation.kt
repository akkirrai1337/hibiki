package org.akkirrai.hibiki.core.model

private val META_SPLIT_REGEX = Regex("\\s*[•·|]\\s*")

fun Anime.buildCardMeta(
    announcementLabel: String,
    maxSubtitleParts: Int = 2,
    separator: String = " • ",
): String {
    if (isAnnouncement()) return announcementLabel

    val subtitleParts = subtitle
        .split(META_SPLIT_REGEX)
        .map(String::trim)
        .filter { it.isNotEmpty() && it != UNKNOWN_META_VALUE }
        .take(maxSubtitleParts)

    if (subtitleParts.isNotEmpty()) {
        return subtitleParts.joinToString(separator)
    }

    return episodesLabel
        .takeIf { it.isNotBlank() && it != UNKNOWN_META_VALUE }
        .orEmpty()
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

    val episodes = episodesLabel
        .takeIf { it.isNotBlank() && it != UNKNOWN_META_VALUE }
        .orEmpty()

    return (subtitleParts + episodes)
        .filter { it.isNotBlank() }
        .joinToString(separator)
}

fun Anime.isAnnouncement(): Boolean {
    val values = listOf(status, episodesLabel).map { it.trim().lowercase() }
    return values.any { it == "анонс" || it == "announcement" || it == "announced" || it == "anons" }
}

private const val UNKNOWN_META_VALUE = "Unknown"
