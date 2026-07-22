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
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppLoadMoreBlock
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.shared.player.WatchSourcesList

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
                WatchSourcesList(
                    sources = state.items,
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
                    loadMoreContent = if (state.hasMoreItems) {
                        {
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
                    } else null,
                    loadingContent = if (state.isLoading && state.items.isNotEmpty()) {
                        { AppCenteredLoading() }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun WatchSourceRow(
    source: WatchSource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    org.akkirrai.hibiki.shared.player.WatchSourceRow(
        title = source.title,
        episodeSummary = source.episodeCount?.let { count ->
            "Â· $count ${stringResource(R.string.watch_episodes_short)}"
        },
        enabled = enabled,
        horizontalPadding = UiDimens.ScreenPadding,
        onClick = onClick,
    )
}

@Composable
private fun WatchSourceRowLegacy(
    source: WatchSource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        source.episodeCount?.let { count ->
            Text(
                text = "· $count ${stringResource(R.string.watch_episodes_short)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                maxLines = 1
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

