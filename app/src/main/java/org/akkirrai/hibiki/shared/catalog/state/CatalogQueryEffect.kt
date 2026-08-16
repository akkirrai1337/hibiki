package org.akkirrai.hibiki.shared.catalog.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun AppCatalogQueryEffect(
    query: String,
    onQuerySettled: () -> Unit,
) {
    LaunchedEffect(query) {
        delay(CATALOG_QUERY_DEBOUNCE_MS)
        onQuerySettled()
    }
}

private const val CATALOG_QUERY_DEBOUNCE_MS = 350L
