package org.akkirrai.hibiki.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppLanguage
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppFilterBottomSheet
import org.akkirrai.hibiki.core.design.component.AppConnectedToggleFilter
import org.akkirrai.hibiki.core.design.component.AppThreeStateChipFilter
import org.akkirrai.hibiki.core.design.component.appFilterOptionText
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.AnimeTitleText
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.SectionHeader
import org.akkirrai.hibiki.core.design.component.VerticalAnimeListItem
import org.akkirrai.hibiki.core.design.component.LibraryStatusPosterFooter
import org.akkirrai.hibiki.core.log.PerfLogger
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildLibraryMeta
import org.akkirrai.hibiki.core.model.buildCardMeta
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAnimeClick: (Anime) -> Unit,
    onProfileClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = UiDimens.ScreenPadding,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(LocalContext.current)),
) {
    val state by viewModel.uiState.collectAsState()
    val languageMode = LocalAppLanguage.current
    val visibleEntries = state.visibleEntries
    var isSearchFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isSearchActive = isSearchFocused && isImeVisible
    var isFilterDialogVisible by remember { mutableStateOf(false) }

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
                    isSearchActive = isSearchActive,
                    profileAvatarUrl = null,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::clearSearch,
                    onProfileClick = onProfileClick,
                    onFilterClick = { isFilterDialogVisible = true },
                    onFocusChange = { isSearchFocused = it },
                    modifier = Modifier.padding(horizontal = UiDimens.ScreenPadding),
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
                    onClick = { onAnimeClick(entry.anime) }
                )
            }
        }
    }

    if (isFilterDialogVisible) {
        LibrarySearchFiltersSheet(
            catalog = state.filterCatalog,
            currentFilters = state.searchFilters,
            onDismiss = { isFilterDialogVisible = false },
            onApply = { filters ->
                viewModel.applySearchFilters(filters)
                isFilterDialogVisible = false
            },
        )
    }
}

private const val LIBRARY_DEFERRED_SYNC_DELAY_MS = 420L

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySearchFiltersSheet(
    catalog: LibraryFilterCatalog,
    currentFilters: LibrarySearchFilters,
    onDismiss: () -> Unit,
    onApply: (LibrarySearchFilters) -> Unit,
) {
    var pendingFilters by remember(currentFilters) { mutableStateOf(currentFilters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    AppFilterBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) { sheetContentModifier ->
        Column(
            modifier = sheetContentModifier
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerHighest,
                        1f to MaterialTheme.colorScheme.background,
                    )
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            AppConnectedToggleFilter(
                title = stringResource(R.string.search_filters_type),
                entries = catalog.typeOptions,
                selected = pendingFilters.type,
                onSelected = { pendingFilters = pendingFilters.copy(type = it) },
                icon = { ImageVector.vectorResource(libraryTypeIcon(it)) },
                text = { appFilterOptionText(it).uppercase() },
            )

            AppConnectedToggleFilter(
                title = stringResource(R.string.search_filters_status),
                entries = catalog.statusOptions,
                selected = pendingFilters.status,
                onSelected = { pendingFilters = pendingFilters.copy(status = it) },
                icon = { ImageVector.vectorResource(libraryStatusIcon(it)) },
                text = { appFilterOptionText(it) },
            )

            AppThreeStateChipFilter(
                title = stringResource(R.string.search_filters_genres),
                options = catalog.genreOptions,
                included = pendingFilters.includedGenres,
                excluded = pendingFilters.excludedGenres,
                onChange = { included, excluded ->
                    pendingFilters = pendingFilters.copy(
                        includedGenres = included,
                        excludedGenres = excluded,
                    )
                },
                id = { it },
                text = { appFilterOptionText(it) },
                maxCollapsedItems = 15,
            )

            FlowRow(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { pendingFilters = LibrarySearchFilters() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(ImageVector.vectorResource(R.drawable.animite_reset), null, Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.search_filters_reset), fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.size(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onApply(pendingFilters)
                        }
                    },
                ) {
                    Icon(ImageVector.vectorResource(R.drawable.animite_done), null, Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.search_filters_apply), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun libraryTypeIcon(type: String): Int = when (type.trim().lowercase()) {
    "tv" -> R.drawable.animite_tv
    "ona" -> R.drawable.animite_ona
    "ova" -> R.drawable.animite_ova
    "movie", "film" -> R.drawable.animite_movie
    else -> R.drawable.animite_tv
}

private fun libraryStatusIcon(status: String): Int = when (status.trim().lowercase()) {
    "released", "finished", "completed" -> R.drawable.animite_finished
    "ongoing", "releasing", "airing" -> R.drawable.animite_releasing
    "announced", "not_yet_released", "not-yet-released" -> R.drawable.animite_not_yet_released
    "cancelled", "canceled" -> R.drawable.animite_cancelled
    "hiatus", "paused" -> R.drawable.animite_hiatus
    else -> R.drawable.animite_finished
}

@Composable
private fun LibraryAnimeCard(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val anime = entry.anime
    VerticalAnimeListItem(
        anime = anime,
        metaText = anime.buildCardMeta(
            announcementLabel = stringResource(R.string.anime_meta_announcement),
            movieLabel = stringResource(R.string.anime_meta_movie),
        ),
        onClick = onClick,
        modifier = modifier,
        posterFooterContent = { LibraryStatusPosterFooter(entry.category) },
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

private fun buildLibraryMeta(anime: Anime): String {
    val subtitleParts = anime.subtitle
        .split(Regex("\\s*[•·|]\\s*"))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "Unknown" }
        .take(2)

    val episodes = anime.episodesLabel
        .takeIf { it.isNotBlank() && it != "Unknown" }
        .orEmpty()

    return (subtitleParts + episodes)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}
