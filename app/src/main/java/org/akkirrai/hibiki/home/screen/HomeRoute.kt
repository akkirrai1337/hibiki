package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.akkirrai.beakokit.api.AnimeKey
import org.akkirrai.hibiki.design.component.state.AppCenteredLoading
import org.akkirrai.hibiki.home.state.HomeContentState
import org.akkirrai.hibiki.home.state.HomeErrorState
import org.akkirrai.hibiki.home.presentation.HomeSearchUiState
import org.akkirrai.hibiki.home.state.HomeUiState
import org.akkirrai.hibiki.home.state.resolveHomeContentState
import org.akkirrai.hibiki.home.state.resolveHomeUiState
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.library.LibraryEntry
import org.akkirrai.hibiki.design.component.source.AppSourceBadge
import org.akkirrai.hibiki.core.source.AppSourceDescriptor
import org.akkirrai.hibiki.core.source.AppSourceIconImage
import org.akkirrai.hibiki.text.AppTextKey
import org.akkirrai.hibiki.text.appText

@Composable
internal fun ColumnScope.HomeRoute(
    baseHomeState: HomeUiState,
    listState: LazyListState,
    sourcesById: Map<String, AppSourceDescriptor>,
    libraryStatusByAnimeId: Map<String, LibraryCategory>,
    libraryEntries: List<LibraryEntry>,
    homeSearchState: HomeSearchUiState,
    actions: HomeActions,
    onHomeRefresh: () -> Unit,
    bottomContentPadding: Dp,
) {
    val homeState = resolveHomeUiState(baseHomeState, libraryEntries, homeSearchState)
    when (val contentState = resolveHomeContentState(homeState)) {
        is HomeContentState.Loading -> AppCenteredLoading(modifier = Modifier.fillMaxSize())
        is HomeContentState.Error -> HomeErrorState(
            title = appText(AppTextKey.HomeErrorTitle),
            message = contentState.message,
            retryLabel = appText(AppTextKey.SearchRetry),
            onRetry = onHomeRefresh,
            modifier = Modifier.fillMaxSize(),
        )
        is HomeContentState.Content -> HomeScreen(
            state = contentState.state,
            actions = actions,
            listState = listState,
            bottomContentPadding = bottomContentPadding,
            currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
            libraryStatusByAnimeId = libraryStatusByAnimeId,
            sourceBadgeContent = { anime ->
                AnimeKey.parse(anime.id)?.sourceId?.value
                    ?.let(sourcesById::get)
                    ?.let { source ->
                        AppSourceBadge(
                            title = source.name,
                            iconContent = { iconModifier ->
                                AppSourceIconImage(
                                    url = source.iconUrl,
                                    modifier = iconModifier,
                                    debugTag = "home-badge",
                                )
                            },
                        )
                    }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
