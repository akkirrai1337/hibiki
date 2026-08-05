package org.akkirrai.hibiki.shared.catalog.sort

import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

enum class CatalogSort {
    Alphabetical,
    Popular,
    Updated,
}

fun CatalogSort.icon(): ImageVector = when (this) {
    CatalogSort.Alphabetical -> Icons.Outlined.SortByAlpha
    CatalogSort.Popular -> Icons.Outlined.Whatshot
    CatalogSort.Updated -> Icons.Outlined.Update
}

fun availableCatalogSorts(
    capabilities: AnimeCatalogCapabilities,
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

fun fallbackCatalogSort(
    supportedSortAlias: String?,
    availableSorts: List<CatalogSort>,
): CatalogSort? {
    val preferred = when (supportedSortAlias?.lowercase()) {
        "relevance" -> CatalogSort.Updated
        "popular", "rating" -> CatalogSort.Popular
        "alphabetical", "title" -> CatalogSort.Alphabetical
        else -> null
    }
    return preferred?.takeIf { it in availableSorts } ?: availableSorts.firstOrNull()
}
