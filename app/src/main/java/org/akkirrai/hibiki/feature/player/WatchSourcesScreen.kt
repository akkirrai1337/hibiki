package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.shared.player.WatchSourcesList
import org.akkirrai.hibiki.shared.player.AppWatchSourcesStateContent

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
            errorIcon = Icons.Outlined.PlayCircleOutline,
            emptyIcon = Icons.Outlined.SubtitlesOff,
            retryLabel = stringResource(R.string.search_retry),
            onRetry = viewModel::retry,
        ) { sources ->
                WatchSourcesList(
                    sources = sources,
                    enabled = !navigationLocked,
                    horizontalPadding = UiDimens.ScreenPadding,
                    episodeSummary = { source ->
                        source.episodeCount?.let { count ->
                            "· $count ${stringResource(R.string.watch_episodes_short)}"
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
                    isRefreshing = state.isLoading && state.items.isNotEmpty(),
                )
            }
        }
    }
