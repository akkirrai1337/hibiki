package org.akkirrai.hibiki.feature.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.download.OfflineDownloadRepository
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppFilledIconButton
import org.akkirrai.hibiki.core.design.component.AppFilledIconButtonStyle
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppShimmerBlock
import org.akkirrai.hibiki.core.design.component.AnimeQuickAction
import org.akkirrai.hibiki.core.design.component.AnimeQuickActionsSheet
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AppTopScrim
import org.akkirrai.hibiki.core.design.component.anime.AnimeTitleText
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedCardModifier
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedPosterModifier
import org.akkirrai.hibiki.core.design.component.anime.AnimePosterCardItem
import org.akkirrai.hibiki.core.design.component.anime.BROWSE_GRID_COLUMNS
import org.akkirrai.hibiki.core.design.component.anime.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.component.anime.PosterImage
import org.akkirrai.hibiki.core.design.component.SectionHeader
import org.akkirrai.hibiki.core.design.component.anime.PortraitGridSpacing
import org.akkirrai.hibiki.core.design.component.anime.searchStatePosterGridContent
import org.akkirrai.hibiki.core.design.component.anime.VerticalAnimeListItem
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.SearchUiState
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
    onAnimeClick: (Anime) -> Unit,
    onOpenSources: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    isActive: Boolean = true,
    bottomContentPadding: Dp = 96.dp,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val featuredAnime = state.featuredAnime
    val continueAnime = state.continueAnime
    val recentlyWatched = state.recentlyWatched
    val errorMessage = state.errorMessage
    val hasContent = featuredAnime.isNotEmpty() || continueAnime != null || recentlyWatched.isNotEmpty() ||
        state.trending.isNotEmpty() || state.recentlyUpdated.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    // The title a long press on a watched card is offering to forget. Confirmed before anything is
    // removed: a long press is easy to trigger by accident while scrolling a row.
    var pendingProgressRemoval by remember { mutableStateOf<Anime?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    val sharedCardModifier: @Composable (Anime) -> Modifier = { anime ->
        animeDetailsSharedCardModifier(anime.id, sharedTransitionScope, animatedVisibilityScope, origin = "home")
    }
    val sharedPosterModifier: @Composable (Anime) -> Modifier = { anime ->
        animeDetailsSharedPosterModifier(anime.id, sharedTransitionScope, animatedVisibilityScope, origin = "home")
    }
    val selectedSourceId = LocalAppPreferencesState.current.animeSource
    val noSourcesInstalled = AnimeSourceRegistry.sources.isEmpty()
    val context = LocalContext.current
    val isOffline = errorMessage != null && errorMessage == stringResource(R.string.home_error_no_internet)
    val hasOfflineDownloads = remember(isOffline) {
        isOffline && OfflineDownloadRepository(context.applicationContext).getOfflineTitleIds().isNotEmpty()
    }
    // A plain remember() here would lose scroll position across configuration changes and
    // whenever this composable is disposed and recomposed (e.g. switching to another bottom-nav
    // tab and back) -- rememberSaveable keeps it, while still resetting to the top when the
    // active source (and so the whole feed) changes.
    val homeListState = rememberSaveable(selectedSourceId, saver = LazyListState.Saver) { LazyListState() }
    // New cards are source-owned and open directly. The opener only retains support for a legacy
    // in-memory aggregator entry while a screen is being recreated.

    Box(modifier = modifier.fillMaxSize()) {
        // Loading/error render as content inside the same Box (instead of an early return) so
        // the search bar overlay below stays visible even before the feed has loaded or while
        // offline -- matching CatalogScreen, where search never disappears under those states.
        when {
            state.isLoading && !hasContent -> {
                HomeLoadingState(
                    hasContinueHistory = state.hasContinueHistory == true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            errorMessage != null && !hasContent -> {
                HomeErrorState(
                    message = errorMessage,
                    actionLabel = stringResource(
                        if (noSourcesInstalled) R.string.action_open_sources else R.string.search_retry,
                    ),
                    onActionClick = if (noSourcesInstalled) onOpenSources else viewModel::load,
                    secondaryActionLabel = if (hasOfflineDownloads) {
                        stringResource(R.string.action_open_downloads)
                    } else {
                        null
                    },
                    onSecondaryActionClick = onOpenDownloads.takeIf { hasOfflineDownloads },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
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
                                featuredAnime = featuredAnime,
                                continueAnime = continueAnime,
                                recentlyWatched = recentlyWatched,
                                trending = state.trending,
                                isActive = isActive,
                                onAnimeClick = onAnimeClick,
                                onWatchedAnimeLongClick = { anime ->
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingProgressRemoval = anime
                                },
                                onEntryClick = onAnimeClick,
                                sharedCardModifier = sharedCardModifier,
                                sharedPosterModifier = sharedPosterModifier,
                            )
                        }
                    }
            }
        }

        AppTopScrim(
            modifier = Modifier.align(Alignment.TopCenter),
            height = HOME_TOP_SEARCH_SCRIM_HEIGHT,
        )

        // Home only launches search: the first character typed opens the search screen.
        AppSearchTopBar(
            query = "",
            onQueryChange = { query -> if (query.isNotBlank()) onSearch(query) },
            onClear = {},
            showFilter = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = UiDimens.SearchBarTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                )
        )

        pendingProgressRemoval?.let { anime ->
            AnimeQuickActionsSheet(
                anime = anime,
                actions = listOf(
                    AnimeQuickAction(
                        titleRes = R.string.home_forget_progress_action,
                        descriptionRes = R.string.home_forget_progress_action_description,
                        icon = Icons.Outlined.DeleteOutline,
                        isDestructive = true,
                        onClick = {
                            pendingProgressRemoval = null
                            viewModel.forgetWatchProgress(anime)
                        },
                    ),
                ),
                onDismissRequest = { pendingProgressRemoval = null },
            )
        }
    }
}

private fun LazyListScope.homeFeedContent(
    featuredAnime: List<Anime>,
    continueAnime: Anime?,
    recentlyWatched: List<Anime>,
    trending: List<Anime>,
    isActive: Boolean,
    onAnimeClick: (Anime) -> Unit,
    onWatchedAnimeLongClick: (Anime) -> Unit,
    onEntryClick: (Anime) -> Unit,
    sharedCardModifier: @Composable (Anime) -> Modifier,
    sharedPosterModifier: @Composable (Anime) -> Modifier,
) {
    item {
        if (featuredAnime.isNotEmpty()) {
            FeaturedCarousel(
                items = featuredAnime,
                onAnimeClick = onEntryClick,
                autoAdvanceEnabled = isActive,
            )
        }
    }
    continueAnime?.let { anime ->
        item {
            Box(modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding)) {
                ContinueWatchingCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onWatchedAnimeLongClick(anime) },
                )
            }
        }
    }
    if (recentlyWatched.isNotEmpty()) {
        item {
            AnimeSection(
                title = stringResource(R.string.home_recently_watched),
                icon = Icons.Outlined.History,
                items = recentlyWatched,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onWatchedAnimeLongClick,
                sharedCardModifier = sharedCardModifier,
                sharedPosterModifier = sharedPosterModifier,
            )
        }
    }
    if (trending.isNotEmpty()) {
        item {
            AnimeSection(
                title = stringResource(R.string.home_trending),
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                items = trending,
                onAnimeClick = onEntryClick,
                sharedCardModifier = sharedCardModifier,
                sharedPosterModifier = sharedPosterModifier,
            )
        }
    }
}

@Composable
private fun HomeLoadingState(
    hasContinueHistory: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = UiDimens.ScreenPadding,
                top = HOME_CONTENT_TOP_PADDING,
                end = UiDimens.ScreenPadding,
            ),
        // Same spacing as the Home LazyColumn between its feed items.
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Keep the placeholders in the same order and geometry as the current Home feed:
        // featured carousel, continue watching card, then the two-column poster sections.
        AppShimmerBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(28.dp)),
        )

        if (hasContinueHistory) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeSectionHeader(
                    title = stringResource(R.string.home_continue_title),
                    icon = Icons.Outlined.History,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppShimmerBlock(
                            Modifier.width(72.dp).aspectRatio(2f / 3f).clip(
                                RoundedCornerShape(UiDimens.CardCorner),
                            ),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            AppShimmerBlock(Modifier.fillMaxWidth(0.82f).height(20.dp).clip(CircleShape))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppShimmerBlock(Modifier.fillMaxWidth(0.42f).height(16.dp).clip(CircleShape))
                                AppShimmerBlock(Modifier.width(44.dp).height(16.dp).clip(CircleShape))
                            }
                            AppShimmerBlock(Modifier.fillMaxWidth(0.56f).height(14.dp).clip(CircleShape))
                        }
                    }
                }
            }
        }

        Column {
            HomeSectionHeader(
                title = stringResource(
                    if (hasContinueHistory) R.string.home_recently_watched else R.string.home_trending,
                ),
                icon = if (hasContinueHistory) Icons.Outlined.History else Icons.AutoMirrored.Outlined.TrendingUp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(PortraitGridSpacing)) {
                repeat(BROWSE_GRID_COLUMNS) {
                    AppShimmerBlock(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(UiDimens.CardCorner)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryActionClick: (() -> Unit)? = null,
) {
    AppMessageState(
        title = stringResource(R.string.home_error_title),
        message = message,
        modifier = modifier
            .fillMaxSize()
            .padding(UiDimens.ScreenPadding),
        actionLabel = actionLabel,
        onActionClick = onActionClick,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryActionClick = onSecondaryActionClick,
        icon = Icons.Outlined.WarningAmber,
        iconTint = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun FeaturedCarousel(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    autoAdvanceEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size },
    )

    val progress = remember { Animatable(0f) }

    var indicatorPage by remember { mutableStateOf(0) }
    val featuredHeight = 240.dp

    LaunchedEffect(autoAdvanceEnabled) {
        PerfLogger.mark("Home featured carousel active changed", "active=$autoAdvanceEnabled")
    }

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

    LaunchedEffect(timerRestartKey, items.size, autoAdvanceEnabled) {
        if (!autoAdvanceEnabled || items.size <= 1) {
            progress.stop()
            PerfLogger.mark(
                event = "Home featured carousel timer stopped",
                details = "active=$autoAdvanceEnabled, items=${items.size}",
            )
            return@LaunchedEffect
        }

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
                .height(featuredHeight),
            contentPadding = PaddingValues(horizontal = UiDimens.ScreenPadding),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
        ) { page ->
            val anime = items[page]
            FeaturedAnimeCard(
                anime = anime,
                bannerUrl = anime.posterUrl ?: anime.posterFallbackUrl,
                height = featuredHeight,
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
    height: Dp,
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
                .height(height)
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
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        top = 18.dp,
                        bottom = 42.dp,
                    ),
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

                AnimeTitleText(
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
                    baseMaxLines = 3,
                    extraLongTitleLines = 0,
                    overflow = TextOverflow.Ellipsis,
                )

                val meta = buildHomeMeta(
                    anime = anime,
                    announcementLabel = stringResource(R.string.anime_meta_announcement),
                    movieLabel = stringResource(R.string.anime_meta_movie),
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
            }

        }
    }
}

@Composable
private fun ContinueWatchingCard(
    anime: Anime?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeSectionHeader(
            title = stringResource(R.string.home_continue_title),
            icon = Icons.Outlined.History,
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
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        AnimeTitleText(
                            text = anime.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            baseMaxLines = 3,
                            extraLongTitleLines = 0,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val meta = buildHomeMeta(
                            anime = anime,
                            announcementLabel = stringResource(R.string.anime_meta_announcement),
                            movieLabel = stringResource(R.string.anime_meta_movie),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            AnimeSourceBadge(titleId = anime.id)
                        }

                        Text(
                            text = stringResource(R.string.home_open_title_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    actionLabel: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: ((Anime) -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    sharedCardModifier: @Composable (Anime) -> Modifier = { Modifier },
    sharedPosterModifier: @Composable (Anime) -> Modifier = { Modifier },
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeSectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
            actionLabel = actionLabel,
            icon = icon,
            onActionClick = onActionClick,
        )

        Column(
            modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(PortraitGridSpacing),
        ) {
            items.chunked(BROWSE_GRID_COLUMNS).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(PortraitGridSpacing)) {
                    rowItems.forEach { anime ->
                        AnimePosterCardItem(
                            anime = anime,
                            metaText = "",
                            onClick = { onAnimeClick(anime) },
                            onLongClick = onAnimeLongClick?.let { { it(anime) } },
                            modifier = Modifier.weight(1f),
                            titleBaseMaxLines = 2,
                            titleExtraLongTitleLines = 0,
                            titleOverflow = TextOverflow.Ellipsis,
                            reservedTitleLines = 2,
                            showRating = true,
                            titleOverlay = true,
                            sharedCardModifier = sharedCardModifier(anime),
                            sharedPosterModifier = sharedPosterModifier(anime),
                        )
                    }
                    repeat(BROWSE_GRID_COLUMNS - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    SectionHeader(
        title = title,
        modifier = modifier,
        actionLabel = actionLabel,
        icon = icon,
        onActionClick = onActionClick,
        titleStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold
        ),
        titleColor = MaterialTheme.colorScheme.onBackground,
    )
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
    AppShimmerBlock(modifier = modifier.fillMaxSize())
}

private const val FEATURED_AUTO_ADVANCE_MS = 5000
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
    includeRating: Boolean = true,
): String {
    return anime.buildCardMeta(
        announcementLabel = announcementLabel,
        movieLabel = movieLabel,
        includeRating = includeRating,
    )
}
