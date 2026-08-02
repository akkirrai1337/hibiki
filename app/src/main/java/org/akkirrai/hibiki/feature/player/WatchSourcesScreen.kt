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
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.shared.player.AppWatchSourcesContent

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

    AppWatchSourcesScreen(
        state = state,
        labels = AppWatchSourcesScreenLabels(
            emptyTitle = stringResource(R.string.watch_sources_empty_title),
            emptyMessage = stringResource(R.string.watch_sources_empty_message),
            retry = stringResource(R.string.search_retry),
            loadMore = stringResource(R.string.watch_sources_load_more),
            episodesShort = stringResource(R.string.watch_episodes_short),
        ),
        icons = AppWatchSourcesScreenIcons(
            back = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        blendMode = BlendMode.Difference
                    },
                )
            },
            error = Icons.Outlined.PlayCircleOutline,
            empty = Icons.Outlined.SubtitlesOff,
        ),
        enabled = !navigationLocked,
        onBackClick = {
            if (navigationLocked) return@AppWatchSourcesScreen
            navigationLockedState.value = true
            onBackClick()
        },
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onSourceClick = { source ->
            if (navigationLocked) return@AppWatchSourcesScreen
            navigationLockedState.value = true
            onSourceClick(source)
        },
        modifier = modifier,
    ) { listContentPadding ->
        AppWatchSourcesContent(
            state = state,
            emptyTitle = stringResource(R.string.watch_sources_empty_title),
            emptyMessage = stringResource(R.string.watch_sources_empty_message),
            retryLabel = stringResource(R.string.search_retry),
            episodeLabel = stringResource(R.string.watch_episodes_short),
            loadMoreLabel = stringResource(R.string.watch_sources_load_more),
            enabled = !navigationLocked,
            onRetry = viewModel::retry,
            onSourceClick = { source ->
                if (!navigationLocked) {
                    navigationLockedState.value = true
                    onSourceClick(source)
                }
            },
            onLoadMore = viewModel::loadMore,
            listContentPadding = listContentPadding,
        )
    }
}
