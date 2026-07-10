package org.akkirrai.hibiki.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFloatingHeader
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.verticalAnimeListContent
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildCardMeta

@Composable
fun RecentUpdatesScreen(
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        if (state.recentlyUpdated.isEmpty()) viewModel.refresh()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.recentlyUpdated.isEmpty() -> AppCenteredLoading(Modifier.fillMaxSize())
            state.errorMessage != null && state.recentlyUpdated.isEmpty() -> AppMessageState(
                title = stringResource(R.string.home_error_title),
                message = state.errorMessage.orEmpty(),
                modifier = Modifier.fillMaxSize().padding(UiDimens.ScreenPadding),
                actionLabel = stringResource(R.string.search_retry),
                onActionClick = viewModel::refresh,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 84.dp, bottom = UiDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                verticalAnimeListContent(
                    items = state.recentlyUpdated,
                    metaText = { anime ->
                        anime.buildCardMeta(
                            announcementLabel = stringResource(R.string.anime_meta_announcement),
                            movieLabel = stringResource(R.string.anime_meta_movie),
                        )
                    },
                    onAnimeClick = onAnimeClick,
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                )
            }
        }
        AppFloatingHeader(
            title = stringResource(R.string.home_recent_updates),
            onBackClick = onBackClick,
            modifier = Modifier,
            includeStatusBarsPadding = false,
        )
    }
}
