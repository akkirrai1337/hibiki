package org.akkirrai.hibiki.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading
import org.akkirrai.hibiki.shared.design.component.AppImagePlaceholder
import org.akkirrai.hibiki.core.design.component.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.design.component.appSearchStateVerticalListContent
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.shared.home.HomeAction
import org.akkirrai.hibiki.shared.home.hasFeedContent
import org.akkirrai.hibiki.shared.home.isSearchActive
import org.akkirrai.hibiki.shared.home.appHomeAnimeSection
import org.akkirrai.hibiki.shared.home.AppHomePullToRefresh
import org.akkirrai.hibiki.shared.home.HomeErrorState
import org.akkirrai.hibiki.shared.home.AppHomePoster
import org.akkirrai.hibiki.shared.home.AppHomeSearchOverlay
import org.akkirrai.hibiki.shared.home.AppHomeFeedList
import org.akkirrai.hibiki.shared.home.AppHomeSearchList
import org.akkirrai.hibiki.shared.home.AppHomeContentSwitcher
import org.akkirrai.hibiki.shared.home.appHomeContinueWatchingSection
import org.akkirrai.hibiki.shared.home.appHomePersonalEmptySection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    onBrowseCatalog: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    isActive: Boolean = true,
    bottomContentPadding: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val continueAnime = state.continueAnime
    val recentlyWatched = state.recentlyWatched
    val recentlyAddedToLibrary = state.recentlyAddedToLibrary
    val errorMessage = state.errorMessage
    val hasContent = state.hasFeedContent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showSearchFilters by rememberSaveable { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isSearchActive = state.isSearchActive
    val hasSearchFilters = state.searchFilterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val searchLoadMoreLabel = stringResource(R.string.search_load_more)
    val searchEmptyTitle = stringResource(R.string.home_search_empty_title)
    val searchEmptyMessage = stringResource(R.string.home_search_empty_message)
    val recentlyWatchedTitle = stringResource(R.string.home_recently_watched)
    val recentlyAddedTitle = stringResource(R.string.home_recently_added)
    val continueSectionTitle = stringResource(R.string.home_continue_title)
    val continueEmptyTitle = stringResource(R.string.home_continue_empty_title)
    val continueEmptyMessage = stringResource(R.string.home_continue_empty_message)
    val continueOpenHint = stringResource(R.string.home_open_title_hint)
    val personalEmptyTitle = stringResource(R.string.home_personal_empty_title)
    val personalEmptyMessage = stringResource(R.string.home_personal_empty_message)
    val personalEmptyActionLabel = stringResource(R.string.home_browse_catalog)
    val pullToRefreshState = rememberPullToRefreshState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val homeListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(hasSearchFilters) {
        if (!hasSearchFilters) showSearchFilters = false
    }

    LaunchedEffect(isActive) {
        if (isActive) viewModel.dispatch(HomeAction.Refresh)
    }

    BackHandler(enabled = isImeVisible || isSearchActive) {
        if (isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            viewModel.dispatch(HomeAction.ClearSearch)
        }
    }

    if (state.isLoading && !hasContent && !isSearchActive) {
        HomeLoadingState(modifier = modifier)
        return
    }

    if (errorMessage != null && !hasContent && !isSearchActive) {
        HomeErrorState(
            title = stringResource(R.string.home_error_title),
            message = errorMessage,
            retryLabel = stringResource(R.string.search_retry),
            onRetry = { viewModel.dispatch(HomeAction.Refresh) },
            modifier = modifier,
            iconContent = {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppHomeContentSwitcher(
            isSearchActive = isSearchActive,
            searchContent = {
                AppHomeSearchList(
                    topContentPadding = HOME_CONTENT_TOP_PADDING,
                    bottomContentPadding = bottomContentPadding,
                ) {
                    appSearchStateVerticalListContent(
                        state = state.searchResult,
                        onAnimeClick = onAnimeClick,
                        metaText = { anime -> buildHomeMeta(anime, announcementLabel, movieLabel) },
                        onLoadMore = { viewModel.dispatch(HomeAction.LoadMoreSearchResults) },
                        onRetrySearch = {},
                        loadMoreLabel = searchLoadMoreLabel,
                        resultsCountLabel = { count ->
                            pluralStringResource(R.plurals.search_results_count, count, count)
                        },
                        emptyTitle = searchEmptyTitle,
                        emptyMessage = searchEmptyMessage,
                        emptyIcon = Icons.Outlined.SearchOff,
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
                        onItemVisible = { anime ->
                            viewModel.dispatch(HomeAction.EnrichDescription(anime))
                        },
                    )
                }
            },
            feedContent = {
                AppHomePullToRefresh(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.dispatch(HomeAction.Refresh) },
                    state = pullToRefreshState,
                    indicatorTopPadding = HOME_PULL_REFRESH_INDICATOR_TOP_OFFSET,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AppHomeFeedList(
                        state = homeListState,
                        topContentPadding = HOME_CONTENT_TOP_PADDING,
                        bottomContentPadding = bottomContentPadding,
                    ) {
                        homeFeedContent(
                            continueAnime = continueAnime,
                            recentlyWatched = recentlyWatched,
                            recentlyAddedToLibrary = recentlyAddedToLibrary,
                            onAnimeClick = onAnimeClick,
                            continueSectionTitle = continueSectionTitle,
                            continueEmptyTitle = continueEmptyTitle,
                            continueEmptyMessage = continueEmptyMessage,
                            continueOpenHint = continueOpenHint,
                            recentlyWatchedTitle = recentlyWatchedTitle,
                            recentlyAddedTitle = recentlyAddedTitle,
                            announcementLabel = announcementLabel,
                            movieLabel = movieLabel,
                            personalEmptyTitle = personalEmptyTitle,
                            personalEmptyMessage = personalEmptyMessage,
                            personalEmptyActionLabel = personalEmptyActionLabel,
                            onBrowseCatalog = onBrowseCatalog,
                            onOpenLibrary = onOpenLibrary,
                        )
                    }
                }
            },
        )

        AppHomeSearchOverlay(
            query = state.searchQuery,
            onQueryChange = { viewModel.dispatch(HomeAction.SearchQueryChanged(it)) },
            onClear = { viewModel.dispatch(HomeAction.ClearSearch) },
            placeholder = stringResource(R.string.search_placeholder),
            filterContentDescription = stringResource(R.string.search_filters),
            clearContentDescription = stringResource(R.string.home_search_clear),
            searchIcon = Icons.Outlined.Search,
            filterIcon = Icons.Outlined.FilterList,
            clearIcon = Icons.Outlined.Close,
            onFilterClick = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                showSearchFilters = true
            },
            showFilterButton = hasSearchFilters,
            scrimHeight = HOME_TOP_SEARCH_SCRIM_HEIGHT,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (showSearchFilters) {
            HomeSearchFiltersSheet(
                viewModel = viewModel,
                onDismissRequest = { showSearchFilters = false },
            )
        }
    }
}

private fun LazyListScope.homeFeedContent(
    continueAnime: Anime?,
    recentlyWatched: List<Anime>,
    recentlyAddedToLibrary: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    continueSectionTitle: String,
    continueEmptyTitle: String,
    continueEmptyMessage: String,
    continueOpenHint: String,
    recentlyWatchedTitle: String,
    recentlyAddedTitle: String,
    announcementLabel: String,
    movieLabel: String,
    personalEmptyTitle: String,
    personalEmptyMessage: String,
    personalEmptyActionLabel: String,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    appHomeContinueWatchingSection(
        anime = continueAnime,
        sectionTitle = continueSectionTitle,
        emptyTitle = continueEmptyTitle,
        emptyMessage = continueEmptyMessage,
        openHint = continueOpenHint,
        sectionIcon = Icons.Outlined.History,
        meta = { anime ->
            buildHomeMeta(
                anime = anime,
                announcementLabel = announcementLabel,
                movieLabel = movieLabel,
            )
        },
        onClick = onAnimeClick,
        imageContent = { currentAnime ->
            AppHomePoster(
                modifier = Modifier.fillMaxSize(),
            ) {
                PosterImage(
                    primaryUrl = currentAnime.posterUrl,
                    fallbackUrl = currentAnime.posterFallbackUrl,
                    contentDescription = currentAnime.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        AppImagePlaceholder(icon = Icons.Outlined.Image)
                    },
                )
            }
        },
        trailingContent = { currentAnime ->
            AnimeSourceBadge(titleId = currentAnime.id)
        },
    )
    appHomeAnimeSection(
        title = recentlyWatchedTitle,
        items = recentlyWatched,
        onAnimeClick = onAnimeClick,
        icon = Icons.Outlined.History,
        metaText = { anime -> buildHomeMeta(anime, announcementLabel, movieLabel) },
        posterContent = { anime ->
            PosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                placeholder = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                },
            )
        },
    )
    appHomeAnimeSection(
        title = recentlyAddedTitle,
        items = recentlyAddedToLibrary,
        onAnimeClick = onAnimeClick,
        icon = Icons.Outlined.VideoLibrary,
        metaText = { anime -> buildHomeMeta(anime, announcementLabel, movieLabel) },
        posterContent = { anime ->
            PosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                placeholder = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                },
            )
        },
        onHeaderClick = onOpenLibrary,
    )
    appHomePersonalEmptySection(
        visible = continueAnime == null && recentlyWatched.isEmpty() && recentlyAddedToLibrary.isEmpty(),
        title = personalEmptyTitle,
        message = personalEmptyMessage,
        actionLabel = personalEmptyActionLabel,
        icon = Icons.Outlined.VideoLibrary,
        onActionClick = onBrowseCatalog,
    )
}

@Composable
private fun HomeLoadingState(
    modifier: Modifier = Modifier,
) {
    AppCenteredLoading(modifier = modifier)
}

private val HOME_CONTENT_TOP_PADDING = UiDimens.SearchBarTopPadding +
    UiDimens.SearchBarHeight +
    UiDimens.ScreenPadding
private val HOME_TOP_SEARCH_SCRIM_HEIGHT = HOME_CONTENT_TOP_PADDING + 18.dp
private val HOME_PULL_REFRESH_INDICATOR_TOP_OFFSET =
    UiDimens.SearchBarTopPadding + UiDimens.SearchBarHeight - 8.dp

private fun buildHomeMeta(
    anime: Anime,
    announcementLabel: String,
    movieLabel: String,
): String {
    return anime.buildCardMeta(
        announcementLabel = announcementLabel,
        movieLabel = movieLabel,
    )
}
