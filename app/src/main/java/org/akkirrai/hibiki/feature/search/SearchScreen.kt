package org.akkirrai.hibiki.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.search.AppSearchScreenContent
import org.akkirrai.hibiki.shared.search.SearchScreenEmptyIcon
import org.akkirrai.hibiki.shared.search.SearchScreenIcon
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.shared.model.buildCardMeta

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val loadMoreLabel = stringResource(R.string.action_more)
    val idleTitle = stringResource(R.string.search_start_title)
    val idleMessage = stringResource(R.string.search_idle)
    val emptyTitle = stringResource(R.string.home_search_empty_title)
    val emptyMessage = stringResource(R.string.search_empty)
    val retryLabel = stringResource(R.string.search_retry)
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()

    AppSearchScreenContent(
        query = state.query,
        result = state.result,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onAnimeClick = onAnimeClick,
        placeholder = stringResource(R.string.search_placeholder),
        searchContentDescription = stringResource(R.string.cd_search),
        searchIcon = SearchScreenIcon,
        metaText = { anime ->
            anime.buildCardMeta(
                announcementLabel = announcementLabel,
                movieLabel = movieLabel,
            )
        },
        onLoadMore = viewModel::loadMore,
        loadMoreLabel = loadMoreLabel,
        resultsCountLabel = { count ->
            pluralStringResource(R.plurals.search_results_count, count, count)
        },
        idleTitle = idleTitle,
        idleMessage = idleMessage,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        emptyIcon = SearchScreenEmptyIcon,
        libraryStatusByAnimeId = libraryStatusByAnimeId,
        libraryStatusLabel = { category -> stringResource(category.labelResId) },
        retryLabel = retryLabel,
        modifier = modifier,
    )
}
