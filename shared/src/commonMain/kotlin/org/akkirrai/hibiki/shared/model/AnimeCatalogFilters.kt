package org.akkirrai.hibiki.shared.model

data class AnimeCatalogFilterOption(
    val id: String,
    val title: String,
)

enum class AnimeCatalogFilter {
    TYPE,
    STATUS,
    INCLUDED_GENRES,
    EXCLUDED_GENRES,
    YEAR_RANGE,
}

data class AnimeCatalogCapabilities(
    val supportedSorts: Set<String> = setOf("relevance"),
    val supportedFilters: Set<AnimeCatalogFilter> = emptySet(),
)

data class AnimeCatalogFilterCatalog(
    val sortOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val typeOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val statusOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val genreOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val capabilities: AnimeCatalogCapabilities = AnimeCatalogCapabilities(),
)
