package org.akkirrai.hibiki.shared.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class AppProfileScreenLabels(
    val overview: String,
    val activity: String,
    val favorites: String,
    val edit: String,
    val save: String,
    val settings: String,
    val changeAvatar: String,
    val profileName: String,
)

data class AppProfileScreenIcons(
    val edit: ImageVector,
    val save: ImageVector,
    val settings: ImageVector,
    val changeAvatar: ImageVector,
)

/** Shared production shell for the local profile banner, tabs and profile actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfileScreen(
    state: LocalProfileUiState,
    isEditing: Boolean,
    editedName: String,
    labels: AppProfileScreenLabels,
    icons: AppProfileScreenIcons,
    onEditedNameChange: (String) -> Unit,
    onEditAction: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarEditClick: () -> Unit,
    avatarContent: @Composable () -> Unit,
    overviewContent: @Composable (bottomContentPadding: Dp) -> Unit,
    activityContent: @Composable (bottomContentPadding: Dp) -> Unit,
    favoritesContent: @Composable (bottomContentPadding: Dp) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PROFILE_TAB_COUNT })
    val scope = rememberCoroutineScope()
    val statusInsets = WindowInsets.statusBars.asPaddingValues()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            ProfileBannerLayout(
                banner = { ratio, bannerModifier ->
                    Box(
                        modifier = bannerModifier.background(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        ProfileAvatar(
                            ratio = ratio,
                            isEditing = isEditing,
                            editContentDescription = labels.changeAvatar,
                            onEditClick = onAvatarEditClick,
                            avatarContent = { avatarModifier ->
                                Box(avatarModifier) { avatarContent() }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                },
                bannerElevatedContent = { ratio ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = statusInsets.calculateTopPadding() + PROFILE_LARGE_PADDING * ratio * 0.9f)
                            .padding(end = PROFILE_LARGE_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(PROFILE_SMALL_PADDING),
                    ) {
                        ProfileActionButton(
                            icon = if (isEditing) icons.save else icons.edit,
                            contentDescription = if (isEditing) labels.save else labels.edit,
                            onClick = onEditAction,
                        )
                        ProfileActionButton(
                            icon = icons.settings,
                            contentDescription = labels.settings,
                            onClick = onSettingsClick,
                        )
                    }
                },
                contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                contentPadding = PaddingValues(top = PROFILE_LARGE_PADDING / 2),
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PROFILE_LARGE_PADDING),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isEditing) {
                                ProfileNameEditor(
                                    label = labels.profileName,
                                    name = editedName,
                                    onNameChange = onEditedNameChange,
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
                            PROFILE_TAB_LABELS.forEachIndexed { index, title ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = {
                                        Text(
                                            text = title(index, labels),
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(
                                                alpha = if (pagerState.currentPage == index) 1f else 0.5f,
                                            ),
                                            maxLines = 1,
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 1.dp, vertical = PROFILE_SMALL_PADDING).clip(CircleShape),
                                )
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        ) { page ->
                            when (page) {
                                PROFILE_TAB_OVERVIEW -> overviewContent(bottomContentPadding)
                                PROFILE_TAB_ACTIVITY -> activityContent(bottomContentPadding)
                                else -> favoritesContent(bottomContentPadding)
                            }
                        }
                    }
                },
            )
        }
    }
}

private const val PROFILE_TAB_COUNT = 3
private const val PROFILE_TAB_OVERVIEW = 0
private const val PROFILE_TAB_ACTIVITY = 1
private const val PROFILE_TAB_FAVORITES = 2
private val PROFILE_TAB_LABELS: List<(Int, AppProfileScreenLabels) -> String> = listOf(
    { _, labels -> labels.overview },
    { _, labels -> labels.activity },
    { _, labels -> labels.favorites },
)
private val PROFILE_BANNER_HEIGHT = 168.dp
private val PROFILE_SMALL_PADDING = 8.dp
private val PROFILE_LARGE_PADDING = 24.dp

@Composable
private fun ProfileBannerLayout(
    banner: @Composable BoxScope.(Float, Modifier) -> Unit,
    bannerElevatedContent: @Composable BoxScope.(Float) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    maxBannerHeight: Dp = PROFILE_BANNER_HEIGHT,
    contentPadding: PaddingValues = PaddingValues(),
    contentBackgroundColor: Color = MaterialTheme.colorScheme.background,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var bannerHeightPx by remember { mutableFloatStateOf(with(density) { maxBannerHeight.toPx() }) }
    var ratio by remember { mutableFloatStateOf(1f) }
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val minBannerHeightPx = with(density) { (statusBarHeight + PROFILE_LARGE_PADDING).toPx() }
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
        banner(ratio, Modifier.height(with(density) { bannerHeightPx.toDp() }).fillMaxWidth())
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
