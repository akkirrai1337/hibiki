package org.akkirrai.hibiki.shared.source

fun normalizeSourceFilterValue(value: String?): String = value.orEmpty()
    .trim()
    .lowercase()
    .replace('_', ' ')
    .replace('-', ' ')
    .replace(Regex("\\s+"), " ")
