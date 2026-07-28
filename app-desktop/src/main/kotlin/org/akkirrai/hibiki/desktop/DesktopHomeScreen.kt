package org.akkirrai.hibiki.desktop

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.shared.design.component.AppBottomBarContentExtraPadding
import org.akkirrai.hibiki.shared.design.component.AppBottomBarHeight
import org.akkirrai.hibiki.shared.home.AppHomeFeedZone
import org.akkirrai.hibiki.shared.home.HomePresenter
import org.akkirrai.hibiki.shared.home.HomePullRefreshIndicatorTopOffset
import org.akkirrai.hibiki.shared.home.HomeContentTopPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopHomeScreen(
    repository: DesktopHomeRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) {
        HomePresenter(initialState = repository.fallbackHomeState().copy(isLoading = true))
    }
    val state by presenter.state.collectAsState()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    fun refresh() {
        scope.launch {
            runCatching { repository.refreshHomeState() }
                .onSuccess(presenter::setState)
                .onFailure { error ->
                    presenter.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Home loading failed")
                    }
                }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    AppHomeFeedZone(
        state = state,
        listState = listState,
        pullToRefreshState = pullToRefreshState,
        topContentPadding = HomeContentTopPadding,
        bottomContentPadding = AppBottomBarHeight + AppBottomBarContentExtraPadding,
        indicatorTopPadding = HomePullRefreshIndicatorTopOffset,
        continueSectionTitle = "Continue watching",
        continueEmptyTitle = "Nothing to continue",
        continueEmptyMessage = "Start watching anime to see it here.",
        continueOpenHint = "Open",
        recentlyWatchedTitle = "Recently watched",
        recentlyAddedTitle = "Recently added",
        announcementLabel = "Announcement",
        movieLabel = "Movie",
        personalEmptyTitle = "Your home is empty",
        personalEmptyMessage = "Browse the catalog to start building your library.",
        personalEmptyActionLabel = "Browse catalog",
        onRefresh = ::refresh,
        onAnimeClick = {},
        onBrowseCatalog = {},
        onOpenLibrary = {},
        sourceBadgeContent = {},
        modifier = modifier,
    )
}
