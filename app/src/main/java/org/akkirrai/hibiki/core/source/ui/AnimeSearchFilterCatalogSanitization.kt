package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption

fun AnimeSearchFilterCatalog.sanitizedForApp(
    preferEnglish: Boolean,
    sourceLanguage: SourceLanguage,
): AnimeSearchFilterCatalog = copy(
    sortOptions = sortOptions.sanitizeOptions(preferEnglish),
    typeOptions = typeOptions.takeIf { capabilities.supports(AnimeSearchFilter.TYPE) }
        .orEmpty().sanitizeOptions(preferEnglish),
    statusOptions = statusOptions.takeIf { capabilities.supports(AnimeSearchFilter.STATUS) }
        .orEmpty().sanitizeOptions(preferEnglish, isStatus = true, sourceLanguage = sourceLanguage),
    genreOptions = genreOptions.takeIf {
        capabilities.supports(AnimeSearchFilter.INCLUDED_GENRES) ||
            capabilities.supports(AnimeSearchFilter.EXCLUDED_GENRES)
    }.orEmpty().sanitizeOptions(preferEnglish),
)

private fun List<SearchFilterOption>.sanitizeOptions(
    preferEnglish: Boolean,
    isStatus: Boolean = false,
    sourceLanguage: SourceLanguage? = null,
): List<SearchFilterOption> = mapNotNull { option ->
    val id = option.id.trim()
    if (id.isBlank()) return@mapNotNull null
    val rawTitle = option.title.trim()
    val title = when {
        isStatus -> canonicalStatusLabel(id, rawTitle, preferEnglish, sourceLanguage)
        rawTitle.isBlank() || rawTitle == id -> id.humanizedAlias()
        else -> rawTitle
    }
    title.takeUnless { it.isBlank() || it.all(Char::isDigit) }
        ?.let { option.copy(id = id, title = it) }
}.distinctBy { option ->
    if (isStatus) option.title.trim().lowercase() else option.id
}

private fun canonicalStatusLabel(
    id: String,
    title: String,
    preferEnglish: Boolean,
    sourceLanguage: SourceLanguage?,
): String {
    val normalized = listOf(id, title).joinToString(" ").trim().lowercase()
    val isRussianSource = sourceLanguage == SourceLanguage.RUSSIAN
    return when {
        isRussianSource && (normalized.contains("ongoing") || normalized.contains("is_ongoing")) -> "\u041e\u043d\u0433\u043e\u0438\u043d\u0433"
        isRussianSource && (normalized.contains("released") || normalized.contains("completed")) -> "\u0412\u044b\u0448\u043b\u043e"
        isRussianSource && (normalized.contains("announcement") || normalized.contains("announced")) -> "\u0410\u043d\u043e\u043d\u0441"
        title.isNotBlank() && !title.equals(id, ignoreCase = true) -> title
        normalized.contains("ongoing") || normalized.contains("is_ongoing") -> "Ongoing"
        normalized.contains("released") || normalized.contains("completed") -> "Released"
        normalized.contains("announcement") || normalized.contains("announced") -> "Announcement"
        else -> id.humanizedAlias()
    }
}

private fun String.humanizedAlias(): String =
    replace('-', ' ')
        .replace('_', ' ')
        .lowercase()
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
