package org.akkirrai.hibiki.shared.catalog.model

private val LIBRARY_META_SPLIT_REGEX = Regex("\\s*[\\u2022\\u00b7|]\\s*")

fun Anime.buildLibraryMeta(maxSubtitleParts: Int = 2, separator: String = " \u2022 "): String = subtitle
    .split(LIBRARY_META_SPLIT_REGEX)
    .map(String::trim)
    .filter { it.isNotEmpty() && it != "Unknown" }
    .take(maxSubtitleParts)
    .filter(String::isNotBlank)
    .joinToString(separator)
