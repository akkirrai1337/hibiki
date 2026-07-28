package org.akkirrai.hibiki.shared.source

fun resolveAlternativeTitles(
    primaryTitle: String,
    titleCandidates: List<String?>,
    fallbackTitles: List<String>,
): List<String> = (titleCandidates.filterNotNull() + fallbackTitles)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .filterNot { it.equals(primaryTitle, ignoreCase = true) }
