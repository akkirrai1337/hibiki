package org.akkirrai.hibiki.shared.home.model

enum class SearchSortAlias {
    RELEVANCE,
    RATING,
    TITLE,
    YEAR,
    VOTES,
    VIEWS,
    COMMENTS,
}

fun resolveSearchSortAlias(alias: String): SearchSortAlias = when (alias) {
    "top" -> SearchSortAlias.RATING
    "title" -> SearchSortAlias.TITLE
    "year" -> SearchSortAlias.YEAR
    "votes" -> SearchSortAlias.VOTES
    "views" -> SearchSortAlias.VIEWS
    "comments" -> SearchSortAlias.COMMENTS
    else -> SearchSortAlias.RELEVANCE
}
