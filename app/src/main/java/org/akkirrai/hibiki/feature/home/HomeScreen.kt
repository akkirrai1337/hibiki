package org.akkirrai.hibiki.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
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
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreState
import org.akkirrai.hibiki.shared.design.component.AppCompactPosterCard
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFilledIconButton
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.shared.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.AnimeTitleText
import org.akkirrai.hibiki.shared.design.component.AppPosterCard
import org.akkirrai.hibiki.core.design.component.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.shared.design.component.SectionHeader
import org.akkirrai.hibiki.shared.design.component.AppFeaturedCarousel
import org.akkirrai.hibiki.shared.design.component.AppContinueWatchingCard
import org.akkirrai.hibiki.shared.design.component.appVerticalAnimeListContent
import org.akkirrai.hibiki.shared.design.component.appSearchStateVerticalListContent
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.rememberLibraryStatusByAnimeId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState
import org.akkirrai.hibiki.shared.model.buildCardMeta

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
    val hasContent = continueAnime != null || recentlyWatched.isNotEmpty() || recentlyAddedToLibrary.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showSearchFilters by rememberSaveable { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isSearchActive = state.searchQuery.isNotBlank() ||
        state.searchResult !is SearchUiState.Idle
    val hasSearchFilters = state.searchFilterCatalog?.capabilities?.supportedFilters?.isNotEmpty() == true
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val searchLoadMoreLabel = stringResource(R.string.search_load_more)
    val searchEmptyTitle = stringResource(R.string.home_search_empty_title)
    val searchEmptyMessage = stringResource(R.string.home_search_empty_message)
    val recentlyWatchedTitle = stringResource(R.string.home_recently_watched)
    val recentlyAddedTitle = stringResource(R.string.home_recently_added)
    val pullToRefreshState = rememberPullToRefreshState()
    val libraryStatusByAnimeId = rememberLibraryStatusByAnimeId()
    val homeListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(hasSearchFilters) {
        if (!hasSearchFilters) showSearchFilters = false
    }

    LaunchedEffect(isActive) {
        if (isActive) viewModel.refresh()
    }

    BackHandler(enabled = isImeVisible || isSearchActive) {
        if (isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        } else {
            viewModel.clearSearch()
        }
    }

    if (state.isLoading && !hasContent && !isSearchActive) {
        HomeLoadingState(modifier = modifier)
        return
    }

    if (errorMessage != null && !hasContent && !isSearchActive) {
        HomeErrorState(
            message = errorMessage,
            onRetry = viewModel::load,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = { homeSearchContentTransition(targetState) },
            label = "HomeSearchContent",
        ) { searchActive ->
            if (searchActive) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = HOME_CONTENT_TOP_PADDING,
                        end = UiDimens.ScreenPadding,
                        bottom = bottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    appSearchStateVerticalListContent(
                        state = state.searchResult,
                        onAnimeClick = onAnimeClick,
                        metaText = { anime -> buildHomeMeta(anime, announcementLabel, movieLabel) },
                        onLoadMore = viewModel::loadMoreSearchResults,
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
                        onItemVisible = viewModel::enrichDescription,
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = viewModel::refresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isLoading,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = HOME_PULL_REFRESH_INDICATOR_TOP_OFFSET),
                        )
                    },
                ) {
                    LazyColumn(
                        state = homeListState,
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = HOME_CONTENT_TOP_PADDING,
                            end = 0.dp,
                            bottom = bottomContentPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        homeFeedContent(
                            continueAnime = continueAnime,
                            recentlyWatched = recentlyWatched,
                            recentlyAddedToLibrary = recentlyAddedToLibrary,
                            onAnimeClick = onAnimeClick,
                            recentlyWatchedTitle = recentlyWatchedTitle,
                            recentlyAddedTitle = recentlyAddedTitle,
                            onBrowseCatalog = onBrowseCatalog,
                            onOpenLibrary = onOpenLibrary,
                        )
                    }
                }
            }
        }

        AppTopScrim(
            modifier = Modifier.align(Alignment.TopCenter),
            height = HOME_TOP_SEARCH_SCRIM_HEIGHT,
        )

        AppSearchTopBar(
            query = state.searchQuery,
            onQueryChange = viewModel::onSearchQueryChange,
            onClear = viewModel::clearSearch,
            onFilterClick = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                showSearchFilters = true
            },
            showFilterButton = hasSearchFilters,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = UiDimens.SearchBarTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                )
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
    recentlyWatchedTitle: String,
    recentlyAddedTitle: String,
    onBrowseCatalog: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    continueAnime?.let { anime ->
        item {
            Box(modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding)) {
                ContinueWatchingCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
    homeAnimeSection(
        title = recentlyWatchedTitle,
        items = recentlyWatched,
        onAnimeClick = onAnimeClick,
        icon = Icons.Outlined.History,
    )
    homeAnimeSection(
        title = recentlyAddedTitle,
        items = recentlyAddedToLibrary,
        onAnimeClick = onAnimeClick,
        icon = Icons.Outlined.VideoLibrary,
        onHeaderClick = onOpenLibrary,
    )
    if (continueAnime == null && recentlyWatched.isEmpty() && recentlyAddedToLibrary.isEmpty()) {
        item {
            AppMessageState(
                title = stringResource(R.string.home_personal_empty_title),
                message = stringResource(R.string.home_personal_empty_message),
                actionLabel = stringResource(R.string.home_browse_catalog),
                onActionClick = onBrowseCatalog,
                icon = Icons.Outlined.VideoLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .padding(horizontal = UiDimens.ScreenPadding),
            )
        }
    }
}

private fun LazyListScope.homeAnimeSection(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onHeaderClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    item {
        HomeAnimeSection(
            title = title,
            items = items,
            onAnimeClick = onAnimeClick,
            icon = icon,
            onHeaderClick = onHeaderClick,
        )
    }
}

@Composable
private fun HomeAnimeSection(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onHeaderClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = UiDimens.SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing),
    ) {
        SectionHeader(
            title = title,
            actionLabel = onHeaderClick?.let { "\u203A" },
            icon = icon,
            modifier = Modifier
                .padding(horizontal = UiDimens.ScreenPadding)
                .clickable(enabled = onHeaderClick != null) {
                    onHeaderClick?.invoke()
                },
            titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
        ) {
            items(items, key = Anime::id) { anime ->
                AppCompactPosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    imageContent = {
                        PosterImage(
                            primaryUrl = anime.posterUrl,
                            fallbackUrl = anime.posterFallbackUrl,
                            contentDescription = anime.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            placeholder = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainer),
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun homeSearchContentTransition(searchActive: Boolean): ContentTransform {
    return if (searchActive) {
        slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> fullHeight / 12 },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
            slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> -(fullHeight / 24) },
            ) + fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> -(fullHeight / 24) },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
            slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> fullHeight / 12 },
            ) + fadeOut(animationSpec = tween(durationMillis = 120))
    }
}

@Composable
private fun HomeLoadingState(
    modifier: Modifier = Modifier,
) {
    AppCenteredLoading(modifier = modifier)
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.design.component.AppErrorState(
        title = stringResource(R.string.home_error_title),
        message = message,
        retryLabel = stringResource(R.string.search_retry),
        onRetry = onRetry,
        modifier = modifier,
        iconContent = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    )
}

@Composable
private fun ContinueWatchingCard(
    anime: Anime?,
    onClick: () -> Unit
) {
    AppContinueWatchingCard(
        anime = anime,
        sectionTitle = stringResource(R.string.home_continue_title),
        emptyTitle = stringResource(R.string.home_continue_empty_title),
        emptyMessage = stringResource(R.string.home_continue_empty_message),
        openHint = stringResource(R.string.home_open_title_hint),
        meta = anime?.let {
            buildHomeMeta(
                anime = it,
                announcementLabel = stringResource(R.string.anime_meta_announcement),
                movieLabel = stringResource(R.string.anime_meta_movie),
            )
        }.orEmpty(),
        sectionIcon = Icons.Outlined.History,
        onClick = onClick,
        imageContent = {
            anime?.let { currentAnime ->
                AnimePoster(
                    anime = currentAnime,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        trailingContent = {
            anime?.let { AnimeSourceBadge(titleId = it.id) }
        },
    )
}

@Composable
private fun AnimePoster(
    anime: Anime,
    modifier: Modifier = Modifier
) {
    AppTonalSurface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    ) {
        PosterImage(
            primaryUrl = anime.posterUrl,
            fallbackUrl = anime.posterFallbackUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            placeholder = { AnimeImagePlaceholder() }
        )
    }
}

@Composable
private fun AnimeImagePlaceholder(
    modifier: Modifier = Modifier
) {
    AppTonalSurface(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val HOME_CONTENT_TOP_PADDING = UiDimens.SearchBarTopPadding +
    UiDimens.SearchBarHeight +
    UiDimens.ScreenPadding +
    32.dp +
    UiDimens.SmallSpacing
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
