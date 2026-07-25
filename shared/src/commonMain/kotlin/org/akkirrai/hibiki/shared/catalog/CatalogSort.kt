package org.akkirrai.hibiki.shared.catalog

enum class CatalogSort {
    Alphabetical,
    Popular,
    Updated,
}

fun catalogSortFromAlias(alias: String): CatalogSort = when (alias.lowercase()) {
    "alphabetical", "title" -> CatalogSort.Alphabetical
    "updated", "latest", "latest_releases" -> CatalogSort.Updated
    else -> CatalogSort.Popular
}
