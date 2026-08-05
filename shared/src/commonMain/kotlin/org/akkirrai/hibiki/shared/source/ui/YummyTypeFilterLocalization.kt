package org.akkirrai.hibiki.shared.source

fun localizeYummyTypeFilterLabel(
    id: String,
    title: String,
    preferEnglish: Boolean,
): String {
    val label = when (id.lowercase()) {
        "tv" -> "Сериал" to "Series"
        "movie" -> "Полнометражный фильм" to "Feature film"
        "short_movie" -> "Короткометражный фильм" to "Short film"
        "ova" -> "OVA" to "OVA"
        "special" -> "Спэшл" to "Special"
        "short_serial" -> "Малометражный сериал" to "Short series"
        "ona" -> "ONA" to "ONA"
        else -> null
    }
    return when {
        label != null -> if (preferEnglish) label.second else label.first
        title.trim().equals(id.trim(), ignoreCase = true) ->
            formatFilterFallbackLabel(id, preferEnglish)
        else -> title
    }
}
