package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.hibiki.catalog.screen.CatalogScreen
import org.akkirrai.hibiki.catalog.screen.CatalogActions
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.catalog.presentation.AnimeCatalogUiState

@Composable
internal fun ColumnScope.CatalogRoute(
    state: AnimeCatalogUiState,
    listState: LazyListState,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    actions: CatalogActions,
    bottomContentPadding: Dp,
) {
    CatalogScreen(
        state = state,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        actions = actions,
        modifier = Modifier.fillMaxSize(),
    )
}
