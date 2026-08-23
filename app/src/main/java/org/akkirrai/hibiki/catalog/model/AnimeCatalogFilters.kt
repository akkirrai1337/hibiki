package org.akkirrai.hibiki.catalog.model

data class AnimeCatalogFilterOption(
    val id: String,
    val title: String,
)

enum class AnimeTypeAlias(val alias: String) {
    Tv("tv"),
    Ona("ona"),
    Ova("ova"),
    Movie("movie"),
;

    companion object {
        fun fromAlias(alias: String?): AnimeTypeAlias? = entries.firstOrNull {
            it.alias == alias?.trim()?.lowercase()
        }
    }
}

enum class AnimeStatus {
    Finished,
    Releasing,
    NotYetReleased,
    Cancelled,
    Hiatus,
;

    companion object {
        fun fromAlias(alias: String): AnimeStatus = when (alias.trim().lowercase()) {
            "released", "finished", "completed" -> Finished
            "ongoing", "releasing", "airing" -> Releasing
            "announced", "not_yet_released", "not-yet-released" -> NotYetReleased
            "cancelled", "canceled" -> Cancelled
            "hiatus", "paused" -> Hiatus
            else -> Finished
        }
    }
}

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
) {
    fun supports(filter: AnimeCatalogFilter): Boolean = filter in supportedFilters
}

data class AnimeCatalogFilterCatalog(
    val sortOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val typeOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val statusOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val genreOptions: List<AnimeCatalogFilterOption> = emptyList(),
    val capabilities: AnimeCatalogCapabilities = AnimeCatalogCapabilities(),
)
