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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.design.UiDimens
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppTonalSurface
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.core.design.component.SectionHeader
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.buildLibraryMeta
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryEntry

@OptIn(ExperimentalLayoutApi::class)
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
    val visibleEntries = state.visibleEntries
    val lifecycleOwner = LocalLifecycleOwner.current
    var isSearchFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isSearchActive = isSearchFocused && isImeVisible
    var isFilterDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.syncFromStorage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncFromStorage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        LibrarySearchFiltersDialog(
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
private fun LibrarySearchFiltersDialog(
    catalog: LibraryFilterCatalog,
    currentFilters: LibrarySearchFilters,
    onDismiss: () -> Unit,
    onApply: (LibrarySearchFilters) -> Unit,
) {
    var pendingFilters by remember(currentFilters) { mutableStateOf(currentFilters) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_filters_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_filters_type),
                            titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            titleColor = MaterialTheme.colorScheme.onBackground,
                        )
                        LibrarySingleChoiceOptions(
                            options = catalog.typeOptions,
                            selected = pendingFilters.type,
                            onSelect = { pendingFilters = pendingFilters.copy(type = it) },
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_filters_status),
                            titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            titleColor = MaterialTheme.colorScheme.onBackground,
                        )
                        LibrarySingleChoiceOptions(
                            options = catalog.statusOptions,
                            selected = pendingFilters.status,
                            onSelect = { pendingFilters = pendingFilters.copy(status = it) },
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_filters_genres),
                            titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            titleColor = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    items(catalog.genreOptions, key = { it }) { genre ->
                        LibraryGenreFilterRow(
                            label = genre,
                            isIncluded = genre in pendingFilters.includedGenres,
                            isExcluded = genre in pendingFilters.excludedGenres,
                            onIncludeClick = {
                                val included = pendingFilters.includedGenres.toMutableSet()
                                val excluded = pendingFilters.excludedGenres.toMutableSet()
                                if (!included.add(genre)) included.remove(genre)
                                excluded.remove(genre)
                                pendingFilters = pendingFilters.copy(
                                    includedGenres = included,
                                    excludedGenres = excluded,
                                )
                            },
                            onExcludeClick = {
                                val included = pendingFilters.includedGenres.toMutableSet()
                                val excluded = pendingFilters.excludedGenres.toMutableSet()
                                if (!excluded.add(genre)) excluded.remove(genre)
                                included.remove(genre)
                                pendingFilters = pendingFilters.copy(
                                    includedGenres = included,
                                    excludedGenres = excluded,
                                )
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { pendingFilters = LibrarySearchFilters() },
                    ) {
                        Text(text = stringResource(R.string.search_filters_reset))
                    }
                    FilledTonalButton(
                        onClick = { onApply(pendingFilters) },
                    ) {
                        Text(text = stringResource(R.string.search_filters_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySingleChoiceOptions(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LibrarySingleChoiceRow(
            title = stringResource(R.string.search_filters_any),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        options.forEach { option ->
            LibrarySingleChoiceRow(
                title = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun LibrarySingleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    LibraryFilterRow(
        label = title,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        labelStyle = MaterialTheme.typography.bodyLarge,
    ) {
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun LibraryGenreFilterRow(
    label: String,
    isIncluded: Boolean,
    isExcluded: Boolean,
    onIncludeClick: () -> Unit,
    onExcludeClick: () -> Unit,
) {
    LibraryFilterRow(
        label = label,
        modifier = Modifier
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        labelStyle = MaterialTheme.typography.bodyMedium,
    ) {
        LibraryGenreModeChip(
            label = stringResource(R.string.search_filters_include),
            selected = isIncluded,
            onClick = onIncludeClick,
        )
        LibraryGenreModeChip(
            label = stringResource(R.string.search_filters_exclude),
            selected = isExcluded,
            onClick = onExcludeClick,
        )
    }
}

@Composable
private fun LibraryFilterRow(
    label: String,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    labelStyle: androidx.compose.ui.text.TextStyle,
    trailingContent: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailingContent()
    }
}

@Composable
private fun LibraryGenreModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LibraryAnimeCard(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val anime = entry.anime
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimePoster(
                anime = anime,
                modifier = Modifier
                    .width(76.dp)
                    .aspectRatio(2f / 3f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = anime.buildLibraryMeta()
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = entry.category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = stringResource(entry.category.labelResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
