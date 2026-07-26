package org.akkirrai.hibiki.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.search.AppSearchScreen

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()

    AppSearchScreen(
        state = state.result,
        query = state.query,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onAnimeClick = onAnimeClick,
        onLoadMore = viewModel::loadMore,
        onRetrySearch = viewModel::search,
        placeholder = stringResource(R.string.search_placeholder),
        searchContentDescription = stringResource(R.string.cd_search),
        searchIcon = Icons.Outlined.Search,
        announcementLabel = stringResource(R.string.anime_meta_announcement),
        movieLabel = stringResource(R.string.anime_meta_movie),
        loadMoreLabel = stringResource(R.string.action_more),
        resultsCountLabel = { count ->
            pluralStringResource(R.plurals.search_results_count, count, count)
        },
        idleTitle = stringResource(R.string.search_start_title),
        idleMessage = stringResource(R.string.search_idle),
        idleIcon = Icons.Outlined.Search,
        emptyTitle = stringResource(R.string.home_search_empty_title),
        emptyMessage = stringResource(R.string.search_empty),
        emptyIcon = Icons.Outlined.SearchOff,
        retryLabel = stringResource(R.string.search_retry),
        posterContent = { anime ->
            PosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        posterFooterContent = { anime ->
            libraryStatusByAnimeId[anime.id]?.let { category ->
                LibraryStatusPosterFooter(category)
            }
        },
        modifier = modifier,
    )
}
