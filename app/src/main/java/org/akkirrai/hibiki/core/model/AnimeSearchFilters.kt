package org.akkirrai.hibiki.core.model

data class AnimeSearchFilters(
    val sortAlias: String = "relevance",
    val typeAlias: String? = null,
    val statusAlias: String? = null,
    val includedGenreAliases: Set<String> = emptySet(),
    val excludedGenreAliases: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** Values of the filters the source defines itself, see [org.akkirrai.beakokit.model.SourceFilterDef]. */
    val sourceFilterValues: Map<String, String> = emptyMap(),
) {
    fun hasActiveFilters(): Boolean {
        return sortAlias != "relevance" ||
            typeAlias != null ||
            statusAlias != null ||
            includedGenreAliases.isNotEmpty() ||
            excludedGenreAliases.isNotEmpty() ||
            yearFrom != null ||
            yearTo != null ||
            sourceFilterValues.isNotEmpty()
    }
}
