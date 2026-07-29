package org.akkirrai.hibiki.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.shared.home.AppHomeScreen
import org.akkirrai.hibiki.shared.home.AppHomeScreenLabels
import org.akkirrai.hibiki.shared.home.AppHomeLoadingState
import org.akkirrai.hibiki.shared.home.HomeAction
import org.akkirrai.hibiki.shared.home.HomeErrorState
import org.akkirrai.hibiki.shared.home.hasFeedContent
import org.akkirrai.hibiki.shared.home.isSearchActive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedAndroidHomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    isActive: Boolean = true,
    bottomContentPadding: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    BackHandler(enabled = isImeVisible || state.isSearchActive) {
        if (isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            viewModel.dispatch(HomeAction.ClearSearch)
        }
    }

    if (isActive) {
        LaunchedEffect(Unit) {
            viewModel.dispatch(HomeAction.Refresh)
        }
    }

    if (state.isLoading && !state.hasFeedContent && !state.isSearchActive) {
        AppHomeLoadingState(modifier = modifier)
        return
    }

    val errorMessage = state.errorMessage
    if (errorMessage != null && !state.hasFeedContent && !state.isSearchActive) {
        HomeErrorState(
            title = stringResource(R.string.home_error_title),
            message = errorMessage.orEmpty(),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = { viewModel.dispatch(HomeAction.Refresh) },
            modifier = modifier,
        )
        return
    }

    AppHomeScreen(
        state = state,
        listState = listState,
        bottomContentPadding = bottomContentPadding,
        currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        labels = sharedAndroidHomeLabels(),
        onQueryChange = { viewModel.dispatch(HomeAction.SearchQueryChanged(it)) },
        onClearSearch = { viewModel.dispatch(HomeAction.ClearSearch) },
        onFilterApply = { viewModel.dispatch(HomeAction.ApplySearchFilters(it)) },
        onRefresh = { viewModel.dispatch(HomeAction.Refresh) },
        onLoadMoreSearch = { viewModel.dispatch(HomeAction.LoadMoreSearchResults) },
        onAnimeClick = onAnimeClick,
        onBrowseCatalog = onBrowseCatalog,
        onOpenLibrary = onOpenLibrary,
        sourceBadgeContent = { anime -> AnimeSourceBadge(titleId = anime.id) },
        onItemVisible = { viewModel.dispatch(HomeAction.EnrichDescription(it)) },
        modifier = modifier,
    )

}

@Composable
private fun sharedAndroidHomeLabels(): AppHomeScreenLabels = AppHomeScreenLabels(
    searchPlaceholder = stringResource(R.string.search_placeholder),
    searchFilters = stringResource(R.string.search_filters),
    searchClear = stringResource(R.string.home_search_clear),
    searchLoadMore = stringResource(R.string.search_load_more),
    searchEmptyTitle = stringResource(R.string.home_search_empty_title),
    searchEmptyMessage = stringResource(R.string.home_search_empty_message),
    resultsCountLabel = { count -> pluralStringResource(R.plurals.search_results_count, count, count) },
    continueTitle = stringResource(R.string.home_continue_title),
    continueEmptyTitle = stringResource(R.string.home_continue_empty_title),
    continueEmptyMessage = stringResource(R.string.home_continue_empty_message),
    continueOpenHint = stringResource(R.string.home_open_title_hint),
    recentlyWatchedTitle = stringResource(R.string.home_recently_watched),
    recentlyAddedTitle = stringResource(R.string.home_recently_added),
    announcementLabel = stringResource(R.string.anime_meta_announcement),
    movieLabel = stringResource(R.string.anime_meta_movie),
    personalEmptyTitle = stringResource(R.string.home_personal_empty_title),
    personalEmptyMessage = stringResource(R.string.home_personal_empty_message),
    personalEmptyActionLabel = stringResource(R.string.home_browse_catalog),
    filterUnavailable = stringResource(R.string.search_filters_unavailable),
    typeTitle = stringResource(R.string.search_filters_type),
    genresTitle = stringResource(R.string.search_filters_genres),
    yearTitle = stringResource(R.string.search_filters_year),
    yearAllLabel = stringResource(R.string.search_filters_year_all),
    yearFromLabel = stringResource(R.string.search_filters_year_from),
    yearToLabel = stringResource(R.string.search_filters_year_to),
    statusTitle = stringResource(R.string.search_filters_status),
    resetLabel = stringResource(R.string.search_filters_reset),
    applyLabel = stringResource(R.string.search_filters_apply),
    libraryStatusLabel = { category -> stringResource(category.labelResId) },
    optionText = { it.title },
)
