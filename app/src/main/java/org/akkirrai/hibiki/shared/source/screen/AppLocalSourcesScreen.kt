package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

fun sourceLanguageSectionLabel(language: String): String = when (language.lowercase()) {
    "ru", "russian" -> "RU"
    "en", "english" -> "EN"
    else -> language.uppercase()
}

@Composable
fun AppLocalSourcesScreen(
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    bottomContentPadding: Dp,
    emptyText: String,
    languageLabel: @Composable (String) -> String = { it.uppercase() },
    onSourceSelected: (String) -> Unit,
    searchQuery: String,
    searchItems: List<org.akkirrai.hibiki.shared.catalog.model.Anime>,
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
    onAnimeClick: (org.akkirrai.hibiki.shared.catalog.model.Anime) -> Unit,
    searchSections: List<SourceSearchSectionState<org.akkirrai.hibiki.shared.catalog.model.Anime>>? = null,
    onSearchRetryForSource: ((String) -> Unit)? = null,
    sourceIconContent: (@Composable (AppSourceDescriptor, Modifier) -> Unit)? = null,
    searchSourceIconContent: (@Composable (SourceSearchSectionState<org.akkirrai.hibiki.shared.catalog.model.Anime>, Modifier) -> Unit)? = null,
    addSourceLabel: String = "Add source",
    onAddSourceClick: () -> Unit = {},
    onExtensionsSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchOpen by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var languageFilterOpen by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
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
    val availableLanguages = sections.map { it.key }.distinct().sorted()
    val visibleSections = sections.filter { selectedLanguages.isEmpty() || it.key in selectedLanguages }
    AppSourceScreenLayout(
        isSearchMode = isSourceSearchActive(searchQuery),
        bottomContentPadding = bottomContentPadding,
        topContentPadding = if (searchOpen) SourceContentListTopPadding else SourceContentListTopPadding + 48.dp,
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
                        ?: AppSourceIconImage(
                            url = null,
                            installedPackageName = sources
                                .firstOrNull { it.id == section.sourceId }
                                ?.installedPackageName,
                            modifier = iconModifier,
                        )
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
            if (visibleSections.isEmpty()) {
                item(key = "sources_empty") { SourceEmptyState(text = emptyText) }
            } else {
                visibleSections.forEach { section ->
                    item(key = "${section.key}_header") {
                        androidx.compose.material3.Text(
                            text = section.title,
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(section.items, key = { source -> "source-${source.id}" }) { source ->
                        AppMihonSourceListItem(
                            source = source,
                            onClick = { onSourceSelected(source.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        searchBarContent = {
            AppMihonSourcesToolbar(
                title = appText(AppTextKey.Sources),
                searchOpen = searchOpen,
                query = searchQuery,
                onQueryChange = {
                    searchOpen = true
                    onSearchQueryChange(it)
                },
                onClearSearch = onSearchClear,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    onSearchClear()
                },
                onFilterClick = { languageFilterOpen = true },
                searchPlaceholder = searchPlaceholder,
                filterContentDescription = searchPlaceholder,
                onAddClick = onAddSourceClick,
                tabContent = {
                    AppSourcesTabs(
                        selectedTab = 0,
                        sourcesLabel = appText(AppTextKey.Sources),
                        extensionsLabel = appText(AppTextKey.SourcesExtensions),
                        onSourcesSelected = {},
                        onExtensionsSelected = onExtensionsSelected,
                    )
                },
            )
        },
        modifier = modifier,
    )
    if (languageFilterOpen) {
        AppLanguageFilterDialog(
            languages = availableLanguages,
            selectedLanguages = selectedLanguages,
            title = appText(AppTextKey.SourcesExternalRepositoryLanguages),
            cancelLabel = appText(AppTextKey.Cancel),
            onLanguageToggle = { language ->
                selectedLanguages = if (language in selectedLanguages) {
                    selectedLanguages - language
                } else {
                    selectedLanguages + language
                }
            },
            onDismiss = { languageFilterOpen = false },
        )
    }
}
