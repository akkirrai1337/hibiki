package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import hibiki.shared.generated.resources.Res
import hibiki.shared.generated.resources.source_ani_liberty
import hibiki.shared.generated.resources.source_yummy_anime
import org.jetbrains.compose.resources.painterResource

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
    searchSections: List<SourceSearchSectionState<org.akkirrai.hibiki.shared.model.Anime>>? = null,
    onSearchRetryForSource: ((String) -> Unit)? = null,
    sourceIconContent: (@Composable (AppSourceDescriptor, Modifier) -> Unit)? = null,
    searchSourceIconContent: (@Composable (SourceSearchSectionState<org.akkirrai.hibiki.shared.model.Anime>, Modifier) -> Unit)? = null,
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
            val effectiveSearchSections = searchSections ?: listOf(
                    SourceSearchSectionState(
                        sourceId = searchSourceId,
                        sourceName = searchSourceName,
                        items = searchItems,
                        hasError = searchError,
                        isLoading = isSearchLoading,
                    )
                )
            appSourceSearchSections(
                sections = effectiveSearchSections,
                isSearching = isSearchLoading,
                errorLabel = searchErrorLabel,
                retryLabel = searchRetryLabel,
                onRetry = { sourceId -> onSearchRetryForSource?.invoke(sourceId) ?: onSearchRetry() },
                emptyContent = {
                    SourceEmptyState(text = searchEmptyTitle)
                },
                sourceIconContent = { section, iconModifier ->
                    searchSourceIconContent?.invoke(section, iconModifier)
                        ?: AppSourceIconImage(url = null, modifier = iconModifier)
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
                            sourceIconContent?.invoke(source, iconModifier)
                            ?: AppSourceIconImage(
                                url = source.iconUrl,
                                placeholder = when (source.id) {
                                    "yummy-anime" -> painterResource(Res.drawable.source_yummy_anime)
                                    "ani-liberty" -> painterResource(Res.drawable.source_ani_liberty)
                                    else -> null
                                },
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
