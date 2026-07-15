package org.akkirrai.hibiki.feature.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R

private enum class LocalProfileTab(val titleRes: Int) {
    Overview(R.string.local_profile_tab_overview),
    Activity(R.string.local_profile_tab_activity),
    Favorites(R.string.local_profile_tab_favorites),
}

/**
 * Direct Android port of Animite's ProfileScreen layout: NestedScrollBannerLayout,
 * UserTabs, AboutTab's StatsRow, and its genre distribution arrangement.
 * Only AniList models are replaced with the local Hibiki profile snapshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalProfileScreen(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalProfileViewModel = viewModel(factory = LocalProfileViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snapshot = remember(context.resources, state.data) { buildProfileSnapshot(context.resources, state.data) }
    val pagerState = rememberPagerState(pageCount = { LocalProfileTab.entries.size })
    val scope = rememberCoroutineScope()
    val statusInsets = WindowInsets.statusBars.asPaddingValues()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
            return@Box
        }

        NestedProfileBannerLayout(
            banner = { ratio, bannerModifier ->
                Box(
                    modifier = bannerModifier.background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    LocalAvatar(
                        ratio = ratio,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = AnimiteLargePadding),
                    )
                }
            },
            bannerElevatedContent = { ratio ->
                RotatingSettingsButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusInsets.calculateTopPadding() + AnimiteLargePadding * ratio)
                        .padding(end = AnimiteLargePadding),
                )
            },
            contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(top = AnimiteLargePadding / 2),
            content = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Column(Modifier.padding(horizontal = AnimiteLargePadding)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    divider = {},
                ) {
                    LocalProfileTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = if (pagerState.currentPage == index) 1f else 0.5f,
                                    ),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier
                                .padding(horizontal = 1.dp, vertical = AnimiteSmallPadding)
                                .clip(CircleShape),
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) { page ->
                    when (LocalProfileTab.entries[page]) {
                        LocalProfileTab.Overview -> LocalOverviewTab(snapshot)
                        LocalProfileTab.Activity -> LocalActivityTab(snapshot)
                        LocalProfileTab.Favorites -> LocalFavoritesTab(snapshot.favoriteLibraryItems)
                    }
                }
            }
            },
        )
    }
}

/** Direct port of Animite's NestedScrollBannerLayout with its 168dp banner. */
@Composable
private fun NestedProfileBannerLayout(
    banner: @Composable BoxScope.(Float, Modifier) -> Unit,
    bannerElevatedContent: @Composable BoxScope.(Float) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    maxBannerHeight: Dp = AnimiteBannerHeight,
    contentPadding: PaddingValues = PaddingValues(),
    contentBackgroundColor: Color = MaterialTheme.colorScheme.background,
) {
    val density = LocalDensity.current
    var bannerHeightPx by remember { mutableFloatStateOf(with(density) { maxBannerHeight.toPx() }) }
    var ratio by remember { mutableFloatStateOf(1f) }
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val minBannerHeightPx = with(density) { (statusBarHeight + AnimiteLargePadding).toPx() }
    val maxBannerHeightPx = with(density) { maxBannerHeight.toPx() }

    val nestedScrollConnection = remember(density, maxBannerHeightPx, minBannerHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) return Offset.Zero
                val previous = bannerHeightPx
                bannerHeightPx = (bannerHeightPx + available.y).coerceIn(minBannerHeightPx, maxBannerHeightPx)
                ratio = (bannerHeightPx - minBannerHeightPx) / (maxBannerHeightPx - minBannerHeightPx)
                return if (previous != bannerHeightPx) available.copy(x = 0f) else Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = when {
                    available.y < 0f || consumed.y < 0f -> consumed.y
                    available.y > 0f -> available.y
                    else -> return Offset.Zero
                }
                val previous = bannerHeightPx
                bannerHeightPx = (bannerHeightPx + delta).coerceIn(minBannerHeightPx, maxBannerHeightPx)
                ratio = (bannerHeightPx - minBannerHeightPx) / (maxBannerHeightPx - minBannerHeightPx)
                return Offset(0f, bannerHeightPx - previous)
            }
        }
    }

    Box(modifier.nestedScroll(nestedScrollConnection)) {
        banner(
            ratio,
            Modifier.height(with(density) { bannerHeightPx.toDp() }).fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(density) { bannerHeightPx.toDp() })
                .background(contentBackgroundColor)
                .padding(contentPadding),
        ) { content() }
        bannerElevatedContent(ratio)
    }
}

@Composable
private fun LocalAvatar(ratio: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(64.dp).graphicsLayer { alpha = (1.5f * ratio - 0.5f).coerceIn(0f, 1f) },
        shape = RoundedCornerShape(topStart = AnimiteSmallPadding, topEnd = AnimiteSmallPadding),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun RotatingSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "settings_icon")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.local_profile_settings),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onClick)
                .padding(AnimiteSmallPadding)
                .graphicsLayer { rotationZ = angle },
        )
    }
}

/** Direct port of AboutTab's vertically scrolling content and StatsRow arrangement. */
@Composable
private fun LocalOverviewTab(snapshot: LocalProfileSnapshot) {
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(AnimiteLargePadding),
        verticalArrangement = Arrangement.spacedBy(AnimiteMediumPadding),
    ) {
        LocalStatsRow(snapshot)
        GenreBars(snapshot.genreSegments)
        RecentLibraryCard(snapshot.recentLibraryItems)
    }
}

@Composable
private fun LocalStatsRow(snapshot: LocalProfileSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LocalStat(stringResource(R.string.local_profile_stat_total), snapshot.libraryTotal.toString())
        LocalStat(stringResource(R.string.local_profile_stat_days), snapshot.activeDaysCount.toString())
        LocalStat(stringResource(R.string.local_profile_stat_time), snapshot.watchTimeLabel)
    }
}

@Composable
private fun LocalStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(value, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    }
}

/** Direct port of AboutTab.Genres: labels column alongside proportional rounded bars. */
@Composable
private fun GenreBars(items: List<DistributionSegment>) {
    if (items.isEmpty()) return
    Row(Modifier.height(IntrinsicSize.Max)) {
        Column(horizontalAlignment = Alignment.End) {
            items.forEach { item ->
                Text(item.label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = AnimiteSmallPadding, end = AnimiteLargePadding)
                .widthIn(max = 250.dp),
            verticalArrangement = Arrangement.spacedBy(AnimiteTinyPadding),
        ) {
            val highest = items.maxOf { it.count }.coerceAtLeast(1)
            items.forEach { item ->
                val weight = item.count / highest.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(weight)
                        .weight(1f)
                        .drawBehind {
                            drawRoundRect(
                                color = item.color,
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(size.height),
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun LocalActivityTab(snapshot: LocalProfileSnapshot) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(AnimiteLargePadding)) { AnalyticsCard(snapshot) }
}

@Composable
private fun LocalFavoritesTab(items: List<RecentLibraryItem>) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxHeight().padding(AnimiteLargePadding), contentAlignment = Alignment.TopCenter) {
            Text(stringResource(R.string.local_profile_empty_favorites), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(AnimiteLargePadding)) { RecentLibraryCard(items) }
    }
}

private val AnimiteBannerHeight = 168.dp
private val AnimiteTinyPadding = 4.dp
private val AnimiteSmallPadding = 8.dp
private val AnimiteMediumPadding = 16.dp
private val AnimiteLargePadding = 24.dp
