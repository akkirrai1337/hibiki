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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import kotlinx.coroutines.launch
import org.akkirrai.animeresolver.metadata.YummyProfile
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.account.YummyAccountRepository
import org.akkirrai.hibiki.core.account.YummyAccountSessionState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppCenteredLoading
import org.akkirrai.hibiki.core.design.component.AppFilledIconButton
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.PosterCard
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.SectionHeader
import org.akkirrai.hibiki.core.design.component.searchStateGridContent
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.model.buildCardMeta

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    onShowAllTrendingClick: () -> Unit,
    onProfileClick: () -> Unit,
    onFilterClick: () -> Unit,
    bottomContentPadding: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val featuredAnime = state.featuredAnime
    val continueAnime = state.continueAnime
    val errorMessage = state.errorMessage
    val hasContent = featuredAnime.isNotEmpty() || continueAnime != null || state.trending.isNotEmpty() || state.recentlyUpdated.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isSearchBarExpanded = isSearchFocused && isImeVisible
    val isSearchActive = state.searchQuery.isNotBlank() ||
        state.searchResult !is SearchUiState.Idle
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val searchLoadMoreLabel = stringResource(R.string.search_load_more)
    val searchEmptyTitle = stringResource(R.string.home_search_empty_title)
    val searchEmptyMessage = stringResource(R.string.home_search_empty_message)
    val pullToRefreshState = rememberPullToRefreshState()

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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = UiDimens.ScreenPadding,
                        top = HOME_TOP_BAR_HEIGHT + UiDimens.ScreenPadding,
                        end = UiDimens.ScreenPadding,
                        bottom = bottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    searchStateGridContent(
                        state = state.searchResult,
                        onAnimeClick = onAnimeClick,
                        metaText = { anime -> buildHomeMeta(anime, announcementLabel) },
                        onLoadMore = viewModel::loadMoreSearchResults,
                        loadMoreLabel = searchLoadMoreLabel,
                        emptyTitle = searchEmptyTitle,
                        emptyMessage = searchEmptyMessage,
                        emptyIcon = Icons.Outlined.SearchOff,
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
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = HOME_TOP_BAR_HEIGHT + UiDimens.ScreenPadding,
                            end = 0.dp,
                            bottom = bottomContentPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {

            item {
                if (featuredAnime.isNotEmpty()) {
                    FeaturedCarousel(
                        items = featuredAnime,
                        onAnimeClick = onAnimeClick,
                    )
                }
            }

            if (continueAnime != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding)) {
                        ContinueWatchingCard(
                            anime = continueAnime,
                            onClick = { onAnimeClick(continueAnime) }
                        )
                    }
                }
            }

            item {
                AnimeSection(
                    title = stringResource(R.string.home_trending),
                    actionLabel = stringResource(R.string.home_see_all),
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    items = state.trending,
                    onAnimeClick = onAnimeClick,
                    onActionClick = onShowAllTrendingClick,
                )
            }

            if (state.recentlyUpdated.isNotEmpty()) {
                item {
                    AnimeSection(
                        title = stringResource(R.string.home_recent_updates),
                        actionLabel = stringResource(R.string.home_refresh),
                        icon = Icons.Outlined.Refresh,
                        items = state.recentlyUpdated,
                        onAnimeClick = onAnimeClick
                    )
                }
            }
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
            isSearchActive = isSearchBarExpanded,
            onQueryChange = viewModel::onSearchQueryChange,
            onClear = viewModel::clearSearch,
            onProfileClick = onProfileClick,
            onFilterClick = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                onFilterClick()
            },
            onFocusChange = { isSearchFocused = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = UiDimens.ScreenPadding)
        )
    }
}

private fun homeSearchContentTransition(searchActive: Boolean): ContentTransform {
    return if (searchActive) {
        (slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> fullHeight / 12 },
        ) + fadeIn(animationSpec = tween(durationMillis = 180))) togetherWith
            (slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> -(fullHeight / 24) },
            ) + fadeOut(animationSpec = tween(durationMillis = 120)))
    } else {
        (slideInVertically(
            animationSpec = tween(durationMillis = 220),
            initialOffsetY = { fullHeight -> -(fullHeight / 24) },
        ) + fadeIn(animationSpec = tween(durationMillis = 180))) togetherWith
            (slideOutVertically(
                animationSpec = tween(durationMillis = 200),
                targetOffsetY = { fullHeight -> fullHeight / 12 },
            ) + fadeOut(animationSpec = tween(durationMillis = 120)))
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
    AppMessageState(
        title = stringResource(R.string.home_error_title),
        message = message,
        modifier = modifier
            .fillMaxSize()
            .padding(UiDimens.ScreenPadding),
        actionLabel = stringResource(R.string.search_retry),
        onActionClick = onRetry,
        iconSlot = {
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
private fun FeaturedCarousel(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size },
    )

    val progress = remember { Animatable(0f) }

    var indicatorPage by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page != indicatorPage) {
                    progress.stop()
                    progress.snapTo(0f)
                    indicatorPage = page
                }
            }
    }

    var timerRestartKey by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) timerRestartKey++
            }
    }

    LaunchedEffect(timerRestartKey, items.size) {
        if (items.size <= 1) return@LaunchedEffect

        if (pagerState.isScrollInProgress) {
            snapshotFlow { pagerState.isScrollInProgress }
                .first { !it }
        }

        progress.snapTo(0f)

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FEATURED_AUTO_ADVANCE_MS,
                easing = LinearEasing,
            ),
        )

        if (!pagerState.isScrollInProgress && pagerState.settledPage == indicatorPage) {
            val nextPage = (indicatorPage + 1) % items.size
            progress.snapTo(0f)
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
        ) { page ->
            val anime = items[page]
            FeaturedAnimeCard(
                anime = anime,
                bannerUrl = anime.posterUrl ?: anime.posterFallbackUrl,
                onClick = { onAnimeClick(anime) },
            )
        }

        if (items.size > 1) {
            FeaturedIndicator(
                totalPages = items.size,
                currentPage = indicatorPage,
                progress = progress.value,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun FeaturedIndicator(
    totalPages: Int,
    currentPage: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalPages) {
            if (i == currentPage) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        ),
                )
            }
            if (i < totalPages - 1) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun FeaturedAnimeCard(
    anime: Anime,
    bannerUrl: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            AnimeBackground(
                imageUrl = bannerUrl,
                fallbackUrl = anime.posterUrl ?: anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.12f),
                                0.35f to Color.Black.copy(alpha = 0.30f),
                                0.70f to Color.Black.copy(alpha = 0.60f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.30f),
                                0.45f to Color.Black.copy(alpha = 0.10f),
                                1f to Color.Black.copy(alpha = 0f),
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_featured_label),
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 8f,
                        )
                    ),
                    color = Color.White
                )

                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 10f,
                        )
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = buildHomeMeta(
                    anime = anime,
                    announcementLabel = stringResource(R.string.anime_meta_announcement),
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.50f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                blurRadius = 6f,
                            )
                        ),
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 1.dp
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 18.dp,
                        vertical = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.details_watch))
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    anime: Anime?,
    onClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.home_continue_title),
            icon = Icons.Outlined.History,
            titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            titleColor = MaterialTheme.colorScheme.onBackground,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            if (anime == null) {
                EmptyContinueContent()
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimePoster(
                        anime = anime,
                        modifier = Modifier
                            .width(72.dp)
                            .aspectRatio(2f / 3f)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val meta = buildHomeMeta(
                            anime = anime,
                            announcementLabel = stringResource(R.string.anime_meta_announcement),
                        )
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = stringResource(R.string.home_open_title_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AppFilledIconButton(
                        onClick = onClick,
                        style = AppFilledIconButtonStyle.PrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContinueContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTonalSurface(
            modifier = Modifier
                .size(56.dp),
            shape = RoundedCornerShape(UiDimens.MediumCorner),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.home_continue_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_continue_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnimeSection(
    title: String,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
            actionLabel = actionLabel,
            icon = icon,
            onActionClick = onActionClick,
            titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            titleColor = MaterialTheme.colorScheme.onBackground,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding)
        ) {
            items(items, key = { it.id }) { anime ->
                PosterCard(
                    anime = anime,
                    metaText = buildHomeMeta(
                        anime = anime,
                        announcementLabel = stringResource(R.string.anime_meta_announcement),
                    ),
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier
                        .width(118.dp)
                        .height(HOME_SECTION_POSTER_CARD_HEIGHT)
                )
            }
        }
    }
}

@Composable
private fun AnimeBackground(
    imageUrl: String?,
    fallbackUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    PosterImage(
        primaryUrl = imageUrl,
        fallbackUrl = fallbackUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = { AnimeImagePlaceholder() }
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

private const val FEATURED_AUTO_ADVANCE_MS = 5000
private val HOME_TOP_BAR_HEIGHT = 50.dp
private val HOME_TOP_SEARCH_SCRIM_HEIGHT = 88.dp
private val HOME_PULL_REFRESH_INDICATOR_TOP_OFFSET = HOME_TOP_BAR_HEIGHT * 0.10f
private val HOME_SECTION_POSTER_CARD_HEIGHT = 244.dp

private fun buildHomeMeta(
    anime: Anime,
    announcementLabel: String,
): String {
    return anime.buildCardMeta(
        announcementLabel = announcementLabel,
    )
}
