package org.akkirrai.hibiki.home.screen

import org.akkirrai.hibiki.app.defaultHomeScreenLabels

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.home.state.HomeErrorState
import org.akkirrai.hibiki.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.home.state.HomeUiState
import org.akkirrai.hibiki.home.state.hasFeedContent
import org.akkirrai.hibiki.home.state.isSearchActive
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryEntry
import org.akkirrai.hibiki.design.component.source.AppSourceBadge
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.AppSourceIconImage
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

@Composable
internal fun ColumnScope.HomeScreen(
    baseHomeState: HomeUiState,
    listState: LazyListState,
    sourcesById: Map<String, AppSourceDescriptor>,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryEntries: List<LibraryEntry>,
    onQueryChange: (String) -> Unit,
    homeSearchState: HomeSearchUiState,
    onFilterApply: (AnimeSearchFilters) -> Unit,
    onSearchClear: () -> Unit,
    onSearchLoadMore: () -> Unit,
    onSearchRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
    onItemVisible: (Anime) -> Unit = {},
    onHomeRefresh: () -> Unit,
    bottomContentPadding: Dp,
) {
    val homeState = baseHomeState.copy(
        recentlyAddedToLibrary = if (baseHomeState.recentlyAddedToLibrary.isEmpty()) {
            libraryEntries
                .filter { it.category != LibraryCategory.Saved && it.category != LibraryCategory.Recent }
                .map { it.anime }
        } else {
            baseHomeState.recentlyAddedToLibrary
        },
        searchQuery = homeSearchState.query,
        searchResult = homeSearchState.result,
        searchFilterCatalog = homeSearchState.filterCatalog,
        isSearchFilterCatalogLoading = homeSearchState.isFilterCatalogLoading,
        searchFilters = homeSearchState.filters,
    )
    if (homeState.isLoading && !homeState.hasFeedContent && !homeState.isSearchActive) {
        AppCenteredLoading(modifier = Modifier.fillMaxSize())
        return
    }
    homeState.errorMessage?.let { errorMessage ->
        if (!homeState.hasFeedContent && !homeState.isSearchActive) {
            HomeErrorState(
                title = appText(AppTextKey.HomeErrorTitle),
                message = errorMessage,
                retryLabel = appText(AppTextKey.SearchRetry),
                onRetry = onHomeRefresh,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
    }
    AppHomeScreen(
        state = homeState,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = defaultHomeScreenLabels(),
        onQueryChange = onQueryChange,
        onClearSearch = onSearchClear,
        onFilterApply = onFilterApply,
        onLoadMoreSearch = onSearchLoadMore,
        onRetrySearch = onSearchRetry,
        onAnimeClick = onAnimeClick,
        onBrowseCatalog = onBrowseCatalog,
        onOpenLibrary = onOpenLibrary,
        sourceBadgeContent = { anime ->
            AnimeKey.parse(anime.id)?.sourceId?.value
                ?.let(sourcesById::get)
                ?.let { source ->
                    AppSourceBadge(
                        title = source.name,
                        iconContent = { iconModifier ->
                            AppSourceIconImage(
                                url = source.iconUrl,
                                modifier = iconModifier,
                                debugTag = "home-badge",
                            )
                        },
                    )
                }
        },
        onItemVisible = onItemVisible,
        modifier = Modifier.fillMaxSize(),
    )
}
