package org.akkirrai.hibiki.shared.player

fun sortQualityLabels(values: List<String>): List<String> = values
    .mapNotNull { it.trim().takeIf(String::isNotBlank) }
    .distinct()
    .sortedByDescending { value -> value.filter(Char::isDigit).toIntOrNull() ?: 0 }
