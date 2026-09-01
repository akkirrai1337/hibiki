package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppLoadMoreBlock
import org.akkirrai.hibiki.core.model.WatchSource

@Composable
fun WatchSourcesScreen(
    animeId: String,
    animeTitle: String,
    onBackClick: () -> Unit,
    onSourceClick: (WatchSource) -> Unit,
    onAutoSelectSingleSource: (WatchSource) -> Unit = onSourceClick,
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
    val singleSource = state.allItems.singleOrNull()

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLastWatchedSource()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(singleSource, state.isLoading, navigationLocked) {
        if (singleSource != null && !state.isLoading && !navigationLocked) {
            navigationLockedState.value = true
            onAutoSelectSingleSource(singleSource)
        }
    }

    WatchScreenScaffold(
        onBackClick = {
            if (navigationLocked) return@WatchScreenScaffold
            navigationLockedState.value = true
            onBackClick()
        },
        navigationLocked = navigationLocked,
        title = animeTitle,
        modifier = modifier,
    ) { contentPadding ->
        when {
            state.errorMessage != null -> {
                WatchEmptyState(
                    title = stringResource(R.string.watch_sources_empty_title),
                    message = state.errorMessage.orEmpty(),
                    icon = Icons.Outlined.PlayCircleOutline,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::retry,
                )
            }

            state.items.isEmpty() && state.isLoading -> {
                AppCenteredLoading(modifier = Modifier.fillMaxSize())
            }

            state.items.isEmpty() -> {
                WatchEmptyState(
                    title = stringResource(R.string.watch_sources_empty_title),
                    message = stringResource(R.string.watch_sources_empty_message),
                    icon = Icons.Outlined.SubtitlesOff,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::retry,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.items, key = { _, source -> source.sourceId }) { index, source ->
                        WatchSourceRow(
                            source = source,
                            isLastWatched = source.sourceId == state.lastWatchedSourceId,
                            enabled = !navigationLocked,
                            shape = watchSourceRowShape(index, state.items.size),
                            onClick = {
                                if (navigationLocked) return@WatchSourceRow
                                navigationLockedState.value = true
                                onSourceClick(source)
                            }
                        )
                    }
                    if (state.hasMoreItems) {
                        item {
                            AppLoadMoreBlock(
                                label = stringResource(R.string.watch_sources_load_more),
                                onClick = viewModel::loadMore,
                                isLoading = state.isLoadingMore,
                                modifier = Modifier.padding(
                                    horizontal = UiDimens.ScreenPadding,
                                    vertical = 18.dp,
                                ),
                            )
                        }
                    }
                    if (state.isLoading && state.items.isNotEmpty()) {
                        item {
                            AppCenteredLoading(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchSourceRow(
    source: WatchSource,
    isLastWatched: Boolean,
    enabled: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            source.episodeCount?.let { count ->
                Text(
                    text = "· $count ${stringResource(R.string.watch_episodes_short)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (isLastWatched) {
                Text(
                    text = "· ${stringResource(R.string.watch_source_last_watched)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun watchSourceRowShape(index: Int, count: Int): RoundedCornerShape = when {
    count == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(8.dp)
}

