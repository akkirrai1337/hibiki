package org.akkirrai.hibiki.shared.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun AppHomeSearchFilterVisibilityEffect(
    hasSearchFilters: Boolean,
    onSearchFiltersUnavailable: () -> Unit,
) {
    LaunchedEffect(hasSearchFilters) {
        if (!hasSearchFilters) onSearchFiltersUnavailable()
    }
}
