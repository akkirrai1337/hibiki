package org.akkirrai.hibiki.shared.source

fun localizeYummySortFilterLabel(
    id: String,
    title: String,
    preferEnglish: Boolean,
): String {
    val label = when (id.lowercase()) {
        "relevance" -> "Релевантности" to "Relevance"
        "top", "rating" -> "Рейтингу" to "Rating"
        "title" -> "Названию" to "Title"
        "year" -> "Дате выхода" to "Release date"
        "rating_counters" -> "Количеству оценок" to "Rating count"
        "votes" -> "Голосам" to "Votes"
        "views" -> "Просмотрам" to "Views"
        "comments" -> "Комментариям" to "Comments"
        "random" -> "Случайно" to "Random"
        "id" -> "Сначала новые" to "Newest added"
        else -> null
    }
    return when {
        label != null -> if (preferEnglish) label.second else label.first
        title.trim().equals(id.trim(), ignoreCase = true) ->
            formatFilterFallbackLabel(id, preferEnglish)
        else -> title
    }
}
