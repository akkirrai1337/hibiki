package org.akkirrai.hibiki.shared.catalog

enum class CatalogSort {
    Alphabetical,
    Popular,
    Updated,
}

fun availableCatalogSorts(
    capabilities: org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities,
): List<CatalogSort> = CatalogSort.entries.filter { sort ->
    when (sort) {
        CatalogSort.Alphabetical -> capabilities.supportedSorts.any {
            it.equals("alphabetical", ignoreCase = true) || it.equals("title", ignoreCase = true)
        }
        CatalogSort.Popular -> capabilities.supportedSorts.any {
            it.equals("popular", ignoreCase = true) || it.equals("rating", ignoreCase = true)
        }
        CatalogSort.Updated -> capabilities.supportedSorts.any {
            it.equals("updated", ignoreCase = true) ||
                it.equals("latest", ignoreCase = true) ||
                it.equals("latest_releases", ignoreCase = true)
        }
    }
}

fun catalogSortFromAlias(alias: String): CatalogSort = when (alias.lowercase()) {
    "alphabetical", "title" -> CatalogSort.Alphabetical
    "updated", "latest", "latest_releases" -> CatalogSort.Updated
    else -> CatalogSort.Popular
}

fun CatalogSort.toAlias(): String = when (this) {
    CatalogSort.Alphabetical -> "alphabetical"
    CatalogSort.Popular -> "popular"
    CatalogSort.Updated -> "updated"
}
