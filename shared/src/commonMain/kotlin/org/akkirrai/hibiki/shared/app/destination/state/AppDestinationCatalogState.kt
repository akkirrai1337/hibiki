package org.akkirrai.hibiki.shared.app.destination.state

import androidx.compose.foundation.lazy.LazyListState
import org.akkirrai.hibiki.shared.catalog.presentation.AnimeCatalogUiState
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.search.model.AnimeSearchFilters

internal data class AppDestinationCatalogState(
    val query: String,
    val items: List<Anime>,
    val filters: AnimeSearchFilters,
    val filterCatalog: AnimeCatalogFilterCatalog?,
    val ui: AnimeCatalogUiState,
    val listState: LazyListState,
)
