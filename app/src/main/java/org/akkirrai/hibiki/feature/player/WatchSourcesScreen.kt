package org.akkirrai.hibiki.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import org.akkirrai.hibiki.shared.player.AppWatchSourcesScreen
import org.akkirrai.hibiki.shared.player.AppWatchSourcesScreenIcons
import org.akkirrai.hibiki.shared.player.AppWatchSourcesScreenLabels

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
    )
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
