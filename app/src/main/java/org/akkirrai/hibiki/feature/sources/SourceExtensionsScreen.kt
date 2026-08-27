package org.akkirrai.hibiki.feature.sources

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry

/**
 * Sources tab: browsing/installing extensions from a repository isn't built yet, so this only
 * lists the sources actually available today -- the ones compiled into the app -- and lets you
 * pick which one is active, the same job the old Settings > Anime source picker did. The Sources
 * (repository) tab shows where installable sources will come from once that exists, with no
 * action buttons since there's nothing behind them to act on yet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceExtensionsScreen(
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val preferences = LocalAppPreferences.current
    val selectedSource = LocalAppPreferencesState.current.animeSource
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabScope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { selectedTab = it }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.nav_sources),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(0) } },
                text = {
                    Text(
                        text = stringResource(R.string.source_extensions_tab_extensions),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { tabScope.launch { pagerState.animateScrollToPage(1) } },
                text = {
                    Text(
                        text = stringResource(R.string.source_extensions_tab_sources),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            if (page == 0) {
                BuiltInSourcesList(
                    sources = AnimeSourceRegistry.sources,
                    selectedSource = selectedSource,
                    bottomContentPadding = bottomContentPadding,
                    onSourceSelected = { source ->
                        preferences.setAnimeSource(source.id)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                )
            } else {
                SourceRepositoryPlaceholder(bottomContentPadding = bottomContentPadding)
            }
        }
    }
}

@Composable
private fun BuiltInSourcesList(
    sources: List<AnimeSourceDescriptor>,
    selectedSource: SourceId,
    bottomContentPadding: Dp,
    onSourceSelected: (AnimeSourceDescriptor) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = bottomContentPadding + 16.dp,
        ),
    ) {
        items(sources, key = { it.id.value }) { source ->
            BuiltInSourceRow(
                source = source,
                selected = source.id == selectedSource,
                onClick = { onSourceSelected(source) },
            )
        }
    }
}

@Composable
private fun BuiltInSourceRow(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = source.iconUrl,
            placeholder = painterResource(source.iconRes),
            error = painterResource(source.iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.language.tag.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SourceRepositoryPlaceholder(bottomContentPadding: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = bottomContentPadding + 16.dp),
    ) {
        AppTonalSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = SOURCE_REPOSITORY_DISPLAY_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.source_extensions_repository_coming_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val SOURCE_REPOSITORY_DISPLAY_NAME = "akkirrai1337/hibiki-sources"
