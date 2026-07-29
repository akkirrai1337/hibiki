package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppLocalSourcesScreen(
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    bottomContentPadding: Dp,
    emptyText: String,
    languageLabel: @Composable (String) -> String = { it.uppercase() },
    onSourceSelected: (String) -> Unit,
    searchQuery: String,
    searchItems: List<org.akkirrai.hibiki.shared.model.Anime>,
    isSearchLoading: Boolean,
    searchError: Boolean,
    searchSourceId: String,
    searchSourceName: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    searchPlaceholder: String,
    searchErrorLabel: String,
    searchRetryLabel: String,
    searchEmptyTitle: String,
    announcementLabel: String = "Announcement",
    movieLabel: String = "Movie",
    onSearchRetry: () -> Unit,
    onAnimeClick: (org.akkirrai.hibiki.shared.model.Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = sources
        .groupBy(AppSourceDescriptor::language)
        .toList()
        .map { (language, items) ->
            SourceLanguageSectionContent(
                key = language,
                title = languageLabel(language),
                items = items,
            )
        }
    AppSourceScreenLayout(
        isSearchMode = isSourceSearchActive(searchQuery),
        bottomContentPadding = bottomContentPadding,
        searchContent = {
            appSourceSearchSections(
                sections = listOf(
                    SourceSearchSectionState(
                        sourceId = searchSourceId,
                        sourceName = searchSourceName,
                        items = searchItems,
                        hasError = searchError,
                        isLoading = isSearchLoading,
                    ),
                ),
                isSearching = isSearchLoading,
                errorLabel = searchErrorLabel,
                retryLabel = searchRetryLabel,
                onRetry = { onSearchRetry() },
                emptyContent = {
                    SourceEmptyState(text = searchEmptyTitle)
                },
                sourceIconContent = { _, iconModifier ->
                    AppSourceIconImage(url = null, modifier = iconModifier)
                },
                itemContent = { anime ->
                    AppSourceSearchAnimeCard(
                        anime = anime,
                        announcementLabel = announcementLabel,
                        movieLabel = movieLabel,
                        onClick = { onAnimeClick(anime) },
                        cardWidth = SourceSearchPosterCardWidth,
                    )
                },
                itemKey = { it.id },
            )
        },
        sourceContent = {
            appSourceLanguageSections(
                sections = sections,
                trailingContent = { iconModifier ->
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = iconModifier,
                    )
                },
                emptyContent = { SourceEmptyState(text = emptyText) },
                isSelected = { source -> source.id == selectedSourceId },
                itemContent = { source, selected, itemModifier ->
                    AppSourceGridItem(
                        name = source.name,
                        selected = selected,
                        onClick = { onSourceSelected(source.id) },
                        modifier = itemModifier,
                        iconContent = { iconModifier ->
                            AppSourceIconImage(
                                url = source.iconUrl,
                                placeholder = null,
                                modifier = iconModifier,
                            )
                        },
                    )
                },
            )
        },
        searchBarContent = {
            AppSourceSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClear = onSearchClear,
                placeholder = searchPlaceholder,
                filterContentDescription = searchPlaceholder,
                clearContentDescription = searchPlaceholder,
                showFilterButton = false,
                onFilterClick = {},
                modifier = Modifier,
            )
        },
        modifier = modifier,
    )
}
