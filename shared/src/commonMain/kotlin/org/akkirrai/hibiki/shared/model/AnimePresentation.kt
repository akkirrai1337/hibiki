package org.akkirrai.hibiki.shared.model

private val LIBRARY_META_SPLIT_REGEX = Regex("\\s*[•·|]\\s*")

fun Anime.buildLibraryMeta(maxSubtitleParts: Int = 2, separator: String = " • "): String = subtitle
    .split(LIBRARY_META_SPLIT_REGEX)
    .map(String::trim)
    .filter { it.isNotEmpty() && it != "Unknown" }
    .take(maxSubtitleParts)
    .filter(String::isNotBlank)
    .joinToString(separator)
