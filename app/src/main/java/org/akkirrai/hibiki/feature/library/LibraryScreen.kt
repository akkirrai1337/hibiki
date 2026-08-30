package org.akkirrai.hibiki.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.anime.AnimeTitleText
import org.akkirrai.hibiki.core.design.component.anime.AnimeSourceBadge
import org.akkirrai.hibiki.core.design.component.anime.PosterImage
import org.akkirrai.hibiki.core.design.component.SectionHeader
import org.akkirrai.hibiki.core.design.component.anime.VerticalAnimeListItem
import org.akkirrai.hibiki.core.design.component.anime.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedCardModifier
import org.akkirrai.hibiki.core.design.component.anime.animeDetailsSharedPosterModifier
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildLibraryMeta
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Anime) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(LocalContext.current)),
) {
    val uiState = viewModel.uiState.collectAsState()
    val state = uiState.value
    val languageMode = LocalAppLanguage.current
    val allVisibleEntries by remember { derivedStateOf { uiState.value.visibleEntries } }
    var visibleCount by rememberSaveable(state.selectedCategory, state.searchQuery) {
        mutableIntStateOf(LIBRARY_PAGE_SIZE)
    }
    val visibleEntries = allVisibleEntries.take(visibleCount)
    val hasMoreEntries = visibleCount < allVisibleEntries.size

    LaunchedEffect(Unit) {
        PerfLogger.mark("LibraryScreen composed")
    }

    LaunchedEffect(languageMode) {
        viewModel.onLanguageChanged()
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            PerfLogger.mark("LibraryScreen active", "defer=${LIBRARY_DEFERRED_SYNC_DELAY_MS}ms")
            delay(LIBRARY_DEFERRED_SYNC_DELAY_MS)
            PerfLogger.mark("LibraryScreen deferred sync trigger")
            viewModel.syncFromStorage()
        } else {
            PerfLogger.mark("LibraryScreen inactive")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppSearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::clearSearch,
                    showFilter = false,
                    modifier = Modifier.padding(
                        top = UiDimens.SearchBarTopPadding,
                        start = UiDimens.ScreenPadding,
                        end = UiDimens.ScreenPadding,
                    ),
                )
                LibraryCategoryChips(
                    selectedCategory = state.selectedCategory,
                    categories = state.orderedCategories,
                    counts = state.categoryCounts,
                    onCategoryClick = viewModel::selectCategory,
                )
            }
        }

        if (state.isRefreshing && state.entries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (state.entries.isEmpty()) {
            item {
                EmptyLibraryState(
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body)
                )
            }
        } else if (visibleEntries.isEmpty()) {
            item {
                EmptyLibraryState(
                    title = if (state.searchQuery.isBlank()) {
                        stringResource(R.string.library_section_empty_title)
                    } else {
                        stringResource(R.string.home_search_empty_title)
                    },
                    body = if (state.searchQuery.isBlank()) {
                        emptyLibraryCategoryMessage(state.selectedCategory)
                    } else {
                        stringResource(R.string.home_search_empty_message)
                    }
                )
            }
        } else {
            items(
                items = visibleEntries,
                key = { it.anime.id }
            ) { entry ->
                LibraryAnimeCard(
                    entry = entry,
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                    onClick = { onAnimeClick(entry.anime) },
                    sharedCardModifier = animeDetailsSharedCardModifier(
                        entry.anime.id,
                        sharedTransitionScope,
                        animatedVisibilityScope,
                    ),
                    sharedPosterModifier = animeDetailsSharedPosterModifier(
                        entry.anime.id,
                        sharedTransitionScope,
                        animatedVisibilityScope,
                    ),
                )
            }
            if (hasMoreEntries) {
                item(key = "show_more_library_entries") {
                    ShowMoreLibraryEntriesRow(
                        modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
                        onClick = {
                            visibleCount = (visibleCount + LIBRARY_PAGE_SIZE).coerceAtMost(allVisibleEntries.size)
                        },
                    )
                }
            }
        }
    }

}

private const val LIBRARY_DEFERRED_SYNC_DELAY_MS = 420L
private const val LIBRARY_PAGE_SIZE = 30

@Composable
private fun ShowMoreLibraryEntriesRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = stringResource(R.string.library_show_more),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LibraryCategoryChips(
    selectedCategory: LibraryCategory,
    categories: List<LibraryCategory>,
    counts: Map<LibraryCategory, Int>,
    onCategoryClick: (LibraryCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            end = UiDimens.ScreenPadding,
        )
    ) {
        items(categories) { category ->
            val selected = category == selectedCategory
            val count = counts[category] ?: 0
            Surface(
                modifier = Modifier.heightIn(min = 38.dp),
                onClick = { onCategoryClick(category) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (count > 0) "${stringResource(category.labelResId)} $count" else stringResource(category.labelResId),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryAnimeCard(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    sharedCardModifier: Modifier = Modifier,
    sharedPosterModifier: Modifier = Modifier,
) {
    val anime = entry.anime
    val meta = anime.buildLibraryMeta()
    VerticalAnimeListItem(
        anime = anime,
        metaText = "",
        onClick = onClick,
        modifier = modifier,
        posterFooterContent = { LibraryStatusPosterFooter(entry.category) },
        metaContent = {
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
        },
        sharedCardModifier = sharedCardModifier,
        sharedPosterModifier = sharedPosterModifier,
    )
}

@Composable
private fun EmptyLibraryState(
    title: String,
    body: String,
) {
    AppMessageState(
        title = title,
        message = body,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 42.dp, start = UiDimens.ScreenPadding, end = UiDimens.ScreenPadding),
        titleStyle = MaterialTheme.typography.titleLarge,
        messageModifier = Modifier.padding(top = 6.dp, start = 28.dp, end = 28.dp),
        messageMaxLines = 2,
        messageOverflow = TextOverflow.Ellipsis,
        iconSlot = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun AnimePoster(
    anime: Anime,
    modifier: Modifier = Modifier
) {
    AppTonalSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
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

@Composable
private fun emptyLibraryCategoryMessage(category: LibraryCategory): String {
    return when (category) {
        LibraryCategory.Watching -> stringResource(R.string.library_empty_watching)
        LibraryCategory.Planned -> stringResource(R.string.library_empty_planned)
        LibraryCategory.Completed -> stringResource(R.string.library_empty_completed)
        LibraryCategory.Dropped -> stringResource(R.string.library_empty_dropped)
        LibraryCategory.OnHold -> stringResource(R.string.library_empty_on_hold)
        LibraryCategory.Favorite -> stringResource(R.string.library_empty_favorite)
        LibraryCategory.Saved -> stringResource(R.string.library_empty_saved)
    }
}
