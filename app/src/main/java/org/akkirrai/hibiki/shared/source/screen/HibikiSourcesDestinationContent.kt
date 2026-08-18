package org.akkirrai.hibiki.shared.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.model.Anime
import org.akkirrai.hibiki.shared.source.AppLocalSourcesScreen
import org.akkirrai.hibiki.shared.source.AppSourceConfigContent
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState
import org.akkirrai.hibiki.shared.source.sourceLanguageSectionLabel
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

@Composable
internal fun SourcesDestinationContent(
    editingSourceConfig: AppSourceDescriptor?,
    sourceConfigContent: AppSourceConfigContent?,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    sourceSearchState: SourcesSearchUiState,
    bottomContentPadding: Dp,
    onSourceSelected: (String) -> Unit,
    onSourceConfigSaved: (AppSourceDescriptor) -> Unit,
    onSourceConfigCancel: () -> Unit,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    onSearchRetryForSource: (String) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAddSourceClick: () -> Unit,
    onExtensionsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    editingSourceConfig?.let { source ->
        sourceConfigContent?.invoke(source, { onSourceConfigSaved(source) }, onSourceConfigCancel)
    } ?: AppLocalSourcesScreen(
        sources = sources,
        selectedSourceId = selectedSourceId,
        bottomContentPadding = bottomContentPadding,
        emptyText = appText(AppTextKey.SourcesEmptyTitle),
        languageLabel = { language -> sourceLanguageSectionLabel(language) },
        onSourceSelected = onSourceSelected,
        searchQuery = sourceSearchState.query,
        searchItems = emptyList(),
        isSearchLoading = sourceSearchState.isSearching,
        searchError = sourceSearchState.sections.any { it.hasError },
        searchSourceId = "",
        searchSourceName = "",
        onSearchQueryChange = onSourceSearchQueryChange,
        onSearchClear = onSourceSearchClear,
        searchPlaceholder = appText(AppTextKey.SearchPlaceholder),
        searchErrorLabel = appText(AppTextKey.SourcesSearchFailed),
        searchRetryLabel = appText(AppTextKey.SearchRetry),
        searchEmptyTitle = appText(AppTextKey.SourcesSearchEmptyTitle),
        announcementLabel = appText(AppTextKey.Announcement),
        movieLabel = appText(AppTextKey.Movie),
        onSearchRetry = onSourceSearchRetry,
        onAnimeClick = onAnimeClick,
        searchSections = sourceSearchState.sections,
        onSearchRetryForSource = onSearchRetryForSource,
        addSourceLabel = appText(AppTextKey.SourcesExternalRepositoryAddTitle),
        onAddSourceClick = onAddSourceClick,
        onExtensionsSelected = onExtensionsSelected,
        searchSourceIconContent = { section, iconModifier ->
            AppSourceIconImage(
                url = sources.firstOrNull { it.id == section.sourceId }?.iconUrl,
                modifier = iconModifier,
            )
        },
        modifier = modifier,
    )
}
