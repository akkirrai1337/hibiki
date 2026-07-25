package org.akkirrai.hibiki.shared.model

data class AnimeSearchFilters(
    val sortAlias: String = "relevance",
    val typeAlias: String? = null,
    val statusAlias: String? = null,
    val includedGenreAliases: Set<String> = emptySet(),
    val excludedGenreAliases: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
) {
    fun hasActiveFilters(): Boolean = sortAlias != "relevance" ||
        typeAlias != null ||
        statusAlias != null ||
        includedGenreAliases.isNotEmpty() ||
        excludedGenreAliases.isNotEmpty() ||
        yearFrom != null ||
        yearTo != null
}
