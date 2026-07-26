package org.akkirrai.hibiki.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.appSearchStateVerticalListContent
import org.akkirrai.hibiki.shared.search.AppSearchField
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = UiDimens.ScreenPadding,
            end = UiDimens.ScreenPadding,
            bottom = UiDimens.ScreenPadding
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AppSearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = viewModel::search,
                placeholder = stringResource(R.string.search_placeholder),
                searchContentDescription = stringResource(R.string.cd_search),
                searchIcon = Icons.Outlined.Search,
            )
        }

        appSearchStateVerticalListContent(
            state = state.result,
            onAnimeClick = onAnimeClick,
            metaText = { anime ->
                anime.buildCardMeta(
                    announcementLabel = announcementLabel,
                    movieLabel = movieLabel,
                )
            },
            onLoadMore = viewModel::loadMore,
            onRetrySearch = viewModel::search,
            loadMoreLabel = loadMoreLabel,
            resultsCountLabel = { count ->
                pluralStringResource(R.plurals.search_results_count, count, count)
            },
            idleTitle = idleTitle,
            idleMessage = idleMessage,
            idleIcon = Icons.Outlined.Search,
            idleTopPadding = 64.dp,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            emptyIcon = Icons.Outlined.SearchOff,
            errorModifier = Modifier.padding(top = 24.dp),
            errorRetryLabel = retryLabel,
            loadMoreModifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
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
        )
    }
}
