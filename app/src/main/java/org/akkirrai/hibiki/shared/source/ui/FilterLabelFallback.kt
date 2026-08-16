package org.akkirrai.hibiki.shared.source

fun formatFilterFallbackLabel(alias: String, preferEnglish: Boolean): String {
    if (!preferEnglish) return alias
    return alias
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
}
