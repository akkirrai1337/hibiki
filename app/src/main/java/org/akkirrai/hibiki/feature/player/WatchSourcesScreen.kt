package org.akkirrai.hibiki.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.shared.player.WatchSourcesList
import org.akkirrai.hibiki.shared.player.AppWatchSourcesStateContent
import org.akkirrai.hibiki.shared.player.formatWatchSourceEpisodeSummary
import org.akkirrai.hibiki.shared.player.isRefreshing

@Composable
fun WatchSourcesScreen(
    animeId: String,
    onBackClick: () -> Unit,
    onSourceClick: (WatchSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchSourcesViewModel = viewModel(
        factory = WatchSourcesViewModel.Factory(
            animeId = animeId,
            context = LocalContext.current,
        )
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigationLockedState = rememberWatchNavigationLockState(lifecycleOwner)
    val navigationLocked = navigationLockedState.value

    WatchScreenScaffold(
        onBackClick = {
            if (navigationLocked) return@WatchScreenScaffold
            navigationLockedState.value = true
            onBackClick()
        },
        navigationLocked = navigationLocked,
        modifier = modifier,
    ) {
        AppWatchSourcesStateContent(
            state = state,
            emptyTitle = stringResource(R.string.watch_sources_empty_title),
            emptyMessage = stringResource(R.string.watch_sources_empty_message),
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::retry,
        ) { sources ->
                WatchSourcesList(
                    sources = sources,
                    enabled = !navigationLocked,
                    horizontalPadding = UiDimens.ScreenPadding,
                    episodeSummary = { source ->
                        source.episodeCount?.let { count ->
                            formatWatchSourceEpisodeSummary(
                                episodeCount = count,
                                episodeLabel = stringResource(R.string.watch_episodes_short),
                            )
                        }
                    },
                    onSourceClick = { source ->
                        if (navigationLocked) return@WatchSourcesList
                        navigationLockedState.value = true
                        onSourceClick(source)
                    },
                    hasMoreItems = state.hasMoreItems,
                    loadMoreLabel = stringResource(R.string.watch_sources_load_more),
                    isLoadingMore = state.isLoadingMore,
                    onLoadMore = viewModel::loadMore,
                    isRefreshing = state.isRefreshing(),
                )
            }
        }
    }
