package org.akkirrai.hibiki.shared.source

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
