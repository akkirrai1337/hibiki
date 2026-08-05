package org.akkirrai.hibiki.shared.app.destination.routes

import org.akkirrai.hibiki.shared.app.destination.*

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.source.AppSourceConfigContent
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.SourcesDestinationContent
import org.akkirrai.hibiki.shared.source.SourcesSearchUiState

@Composable
internal fun SourcesDestinationRoute(
    editingSourceConfig: AppSourceDescriptor?,
    sourceConfigContent: AppSourceConfigContent?,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    sourceSearchState: SourcesSearchUiState,
    bottomContentPadding: Dp,
    onSourceSelected: (String) -> Unit,
    onEditSourceConfig: (AppSourceDescriptor) -> Unit,
    onSourceConfigSaved: (AppSourceDescriptor) -> Unit,
    onSourceConfigCancel: () -> Unit,
    onSourceSearchQueryChange: (String) -> Unit,
    onSourceSearchClear: () -> Unit,
    onSourceSearchRetry: () -> Unit,
    onSearchRetryForSource: (String) -> Unit,
    onAnimeClick: (org.akkirrai.hibiki.shared.catalog.model.Anime) -> Unit,
) {
    SourcesDestinationContent(
        editingSourceConfig = editingSourceConfig,
        sourceConfigContent = sourceConfigContent,
        sources = sources,
        selectedSourceId = selectedSourceId,
        sourceSearchState = sourceSearchState,
        bottomContentPadding = bottomContentPadding,
        onSourceSelected = { sourceId ->
            val source = sources.firstOrNull { it.id == sourceId }
            if (sourceConfigContent != null && source?.configSchema?.fields?.isNotEmpty() == true) {
                onEditSourceConfig(source)
            } else {
                onSourceSelected(sourceId)
            }
        },
        onSourceConfigSaved = onSourceConfigSaved,
        onSourceConfigCancel = onSourceConfigCancel,
        onSourceSearchQueryChange = onSourceSearchQueryChange,
        onSourceSearchClear = onSourceSearchClear,
        onSourceSearchRetry = onSourceSearchRetry,
        onSearchRetryForSource = onSearchRetryForSource,
        onAnimeClick = onAnimeClick,
        modifier = Modifier.fillMaxSize(),
    )
}
