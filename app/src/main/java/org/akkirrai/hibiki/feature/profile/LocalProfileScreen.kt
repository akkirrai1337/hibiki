package org.akkirrai.hibiki.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.app.settings.withLanguage
import org.akkirrai.hibiki.core.design.animation.continuousRotation

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
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: LocalProfileViewModel = viewModel(factory = LocalProfileViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    var isEditingProfile by remember { mutableStateOf(false) }
    var editedName by remember(state.data.profileName) { mutableStateOf(state.data.profileName) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.updateProfileAvatar(it.toString()) }
    }
    val context = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    val localizedResources = remember(context, appLanguage) {
        context.withLanguage(appLanguage).resources
    }
    val snapshot = remember(localizedResources, state.data) {
        buildProfileSnapshot(localizedResources, state.data)
    }
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
                        avatarUri = state.data.profileAvatarUri,
                        isEditing = isEditingProfile,
                        onEditClick = { avatarPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            },
            bannerElevatedContent = { ratio ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusInsets.calculateTopPadding() + AnimiteLargePadding * ratio * 0.9f)
                        .padding(end = AnimiteLargePadding),
                    horizontalArrangement = Arrangement.spacedBy(AnimiteSmallPadding),
                ) {
                    ProfileActionButton(
                        icon = if (isEditingProfile) Icons.Rounded.Check else Icons.Rounded.Edit,
                        contentDescription = stringResource(if (isEditingProfile) R.string.action_save else R.string.local_profile_edit),
                        onClick = {
                            if (isEditingProfile) {
                                viewModel.updateProfileName(editedName)
                            } else {
                                editedName = state.data.profileName
                            }
                            isEditingProfile = !isEditingProfile
                        },
                    )
                    RotatingSettingsButton(onClick = onSettingsClick)
                }
            },
            contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            contentPadding = PaddingValues(top = AnimiteLargePadding / 2),
            content = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AnimiteLargePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isEditingProfile) {
                        ProfileNameEditor(
                            name = editedName,
                            onNameChange = { editedName = it },
                        )
                    } else {
                        Text(
                            text = state.data.profileName,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
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
                        LocalProfileTab.Overview -> LocalOverviewTab(snapshot, bottomContentPadding)
                        LocalProfileTab.Activity -> LocalActivityTab(snapshot, bottomContentPadding)
                        LocalProfileTab.Favorites -> LocalFavoritesTab(snapshot.favoriteLibraryItems, bottomContentPadding)
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
private fun LocalAvatar(
    ratio: Float,
    avatarUri: String?,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isEditing) 0.38f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "avatar_scrim",
    )
    Box(
        modifier = modifier.size(70.dp).graphicsLayer { alpha = (1.5f * ratio - 0.5f).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUri.isNullOrBlank()) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
            }
        }
        if (isEditing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .clickable(onClick = onEditClick),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.local_profile_change_avatar),
                    modifier = Modifier.padding(7.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileNameEditor(name: String, onNameChange: (String) -> Unit) {
    val underlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Column(modifier = Modifier.widthIn(min = 150.dp, max = 240.dp)) {
        Text(
            text = stringResource(R.string.local_profile_name),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp)
                .drawBehind {
                    drawLine(
                        color = underlineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        )
    }
}

@Composable
private fun RotatingSettingsButton(onClick: () -> Unit) {
    ProfileActionButton(
        icon = Icons.Rounded.Settings,
        contentDescription = stringResource(R.string.local_profile_settings),
        onClick = onClick,
        iconModifier = Modifier.continuousRotation(
            durationMillis = 10_000,
            label = "settings_icon_rotation",
        ),
    )
}

@Composable
private fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onClick)
                .padding(AnimiteSmallPadding)
                .then(iconModifier),
        )
    }
}

/** Direct port of AboutTab's vertically scrolling content and StatsRow arrangement. */
@Composable
private fun LocalOverviewTab(snapshot: LocalProfileSnapshot, bottomContentPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = AnimiteLargePadding, top = AnimiteLargePadding, end = AnimiteLargePadding)
            .padding(bottom = bottomContentPadding + AnimiteLargePadding),
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
private fun LocalActivityTab(snapshot: LocalProfileSnapshot, bottomContentPadding: Dp) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = AnimiteLargePadding, top = AnimiteLargePadding, end = AnimiteLargePadding)
            .padding(bottom = bottomContentPadding + AnimiteLargePadding),
    ) { AnalyticsCard(snapshot) }
}

@Composable
private fun LocalFavoritesTab(items: List<RecentLibraryItem>, bottomContentPadding: Dp) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxHeight().padding(AnimiteLargePadding), contentAlignment = Alignment.TopCenter) {
            Text(stringResource(R.string.local_profile_empty_favorites), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = AnimiteLargePadding, top = AnimiteLargePadding, end = AnimiteLargePadding)
                .padding(bottom = bottomContentPadding + AnimiteLargePadding),
        ) {
            RecentLibraryCard(items = items, showTitle = false)
        }
    }
}

private val AnimiteBannerHeight = 168.dp
private val AnimiteTinyPadding = 4.dp
private val AnimiteSmallPadding = 8.dp
private val AnimiteMediumPadding = 16.dp
private val AnimiteLargePadding = 24.dp
