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

fun CatalogSort.toAlias(): String = when (this) {
    CatalogSort.Alphabetical -> "alphabetical"
    CatalogSort.Popular -> "popular"
    CatalogSort.Updated -> "updated"
}
