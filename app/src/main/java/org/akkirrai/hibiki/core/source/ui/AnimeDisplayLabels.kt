package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.home.ui.resolveDisplayTypeLabel

/** English-first when preferred, Russian-first otherwise -- either way the other stays as fallback. */
fun preferredSourceLanguages(preferEnglish: Boolean): List<SourceLanguage> = if (preferEnglish) {
    listOf(SourceLanguage.ENGLISH, SourceLanguage.RUSSIAN)
} else {
    listOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH)
}

fun resolveAlternativeTitles(
    primaryTitle: String,
    titleCandidates: List<String?>,
    fallbackTitles: List<String>,
): List<String> = (titleCandidates.filterNotNull() + fallbackTitles)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .filterNot { it.equals(primaryTitle, ignoreCase = true) }

fun resolveReleaseStatusLabel(statusKey: String, preferEnglish: Boolean): String = when {
    statusKey.equals("ongoing", ignoreCase = true) ->
        if (preferEnglish) "Ongoing" else "Онгоинг"
    statusKey.equals("released", ignoreCase = true) ->
        if (preferEnglish) "Released" else "Вышел"
    statusKey.equals("announcement", ignoreCase = true) ->
        if (preferEnglish) "Announcement" else "Анонс"
    else -> if (preferEnglish) "Unknown" else "Неизвестно"
}

fun resolveAnimeSubtitle(
    type: String?,
    year: Int?,
    fallbackSubtitle: String?,
): String = listOfNotNull(
    type?.let(::resolveDisplayTypeLabel),
    year?.toString(),
).joinToString(" · ").ifBlank { fallbackSubtitle.orEmpty() }

fun formatReleaseDateLabel(year: Int?, season: Int?, preferEnglish: Boolean): String? {
    val releaseYear = year ?: return null
    val seasonTitle = when (season) {
        1 -> if (preferEnglish) "Winter" else "Зима"
        2 -> if (preferEnglish) "Spring" else "Весна"
        3 -> if (preferEnglish) "Summer" else "Лето"
        4 -> if (preferEnglish) "Autumn" else "Осень"
        else -> null
    }
    return listOfNotNull(seasonTitle, releaseYear.toString()).joinToString(" ")
}

fun resolveEpisodesLabel(
    releasedCount: Int?,
    fallbackLabel: String?,
    preferEnglish: Boolean,
): String {
    val normalizedFallback = fallbackLabel?.trim()?.lowercase().orEmpty()
    val fallbackIsUnknown = normalizedFallback == "unknown" || normalizedFallback.contains("unknown") ||
        normalizedFallback == "episode unknown" ||
        normalizedFallback == "episodes unknown" ||
        normalizedFallback == "количество серий неизвестно"
    return when (val count = releasedCount) {
        null -> fallbackLabel.orEmpty().takeUnless { fallbackIsUnknown || it.isBlank() } ?:
            if (preferEnglish) "Episodes unknown" else "Количество серий неизвестно"
        else -> "$count ${episodeCountWord(count, preferEnglish)}"
    }
}

/** "1 episode" / "2 episodes"; "1 серия" / "2 серии" / "5 серий" -- lowercase either way. */
private fun episodeCountWord(count: Int, preferEnglish: Boolean): String {
    if (preferEnglish) return if (count == 1) "episode" else "episodes"
    val hundredRemainder = count % 100
    val tenRemainder = count % 10
    return when {
        hundredRemainder in 11..14 -> "серий"
        tenRemainder == 1 -> "серия"
        tenRemainder in 2..4 -> "серии"
        else -> "серий"
    }
}
