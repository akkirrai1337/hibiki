package org.akkirrai.hibiki.shared.catalog.filters

import org.akkirrai.hibiki.shared.catalog.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AppCatalogFilterVisibilityEffect(
    hasFilters: Boolean,
    onFiltersUnavailable: () -> Unit,
) {
    LaunchedEffect(hasFilters) {
        if (!hasFilters) onFiltersUnavailable()
    }
}
