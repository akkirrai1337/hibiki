package org.akkirrai.hibiki.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.AppLocalSourcesScreen
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.source.SourceScreenDefaultBottomContentPadding

@Composable
fun SharedAndroidSourcesScreen(
    bottomContentPadding: Dp = SourceScreenDefaultBottomContentPadding,
    modifier: Modifier = Modifier,
    selectedSourceOverride: SourceId? = null,
    onSourceSelected: ((SourceId) -> Unit)? = null,
    onAnimeClick: (Anime) -> Unit = {},
    searchViewModel: SourcesSearchViewModel = viewModel(
        factory = SourcesSearchViewModel.Factory(LocalContext.current),
    ),
) {
    val preferences = LocalAppPreferences.current
    val selectedSource = selectedSourceOverride ?: LocalAppPreferencesState.current.animeSource
    val searchState by searchViewModel.uiState.collectAsState()
    val sources = AnimeSourceRegistry.sources.map { source ->
        AppSourceDescriptor(
            id = source.id.value,
            name = source.name,
            language = when (source.language) {
                SourceLanguage.RUSSIAN -> "RUSSIAN"
                SourceLanguage.ENGLISH -> "ENGLISH"
                else -> source.language.toString()
            },
        )
    }

    AppLocalSourcesScreen(
        sources = sources,
        selectedSourceId = selectedSource.value,
        bottomContentPadding = bottomContentPadding,
        emptyText = stringResource(R.string.settings_sources_empty),
        languageLabel = { language ->
            when (language) {
                "RUSSIAN" -> stringResource(R.string.settings_sources_language_ru)
                "ENGLISH" -> stringResource(R.string.settings_sources_language_en)
                else -> language
            }
        },
        onSourceSelected = { sourceId ->
            val id = SourceId(sourceId)
            onSourceSelected?.invoke(id) ?: preferences.setAnimeSource(id)
        },
        searchQuery = searchState.query,
        searchItems = emptyList(),
        isSearchLoading = searchState.isSearching,
        searchError = false,
        searchSourceId = "",
        searchSourceName = "",
        onSearchQueryChange = searchViewModel::onQueryChange,
        onSearchClear = searchViewModel::clearQuery,
        searchPlaceholder = stringResource(R.string.search_placeholder),
        searchErrorLabel = stringResource(R.string.sources_search_failed),
        searchRetryLabel = stringResource(R.string.search_retry),
        searchEmptyTitle = stringResource(R.string.sources_search_empty_title),
        announcementLabel = stringResource(R.string.anime_meta_announcement),
        movieLabel = stringResource(R.string.anime_meta_movie),
        onSearchRetry = {},
        onSearchRetryForSource = { sourceId -> searchViewModel.retry(SourceId(sourceId)) },
        onAnimeClick = onAnimeClick,
        searchSections = searchState.sections,
        sourceIconContent = { source, iconModifier ->
            val descriptor = AnimeSourceRegistry.sources.first { it.id.value == source.id }
            AppSourceIconImage(
                url = descriptor.iconUrl,
                placeholder = painterResource(descriptor.iconRes),
                modifier = iconModifier,
            )
        },
        searchSourceIconContent = { section, iconModifier ->
            val descriptor = AnimeSourceRegistry.sources.firstOrNull { it.id.value == section.sourceId }
            if (descriptor == null) {
                AppSourceIconImage(url = null, modifier = iconModifier)
            } else {
                AppSourceIconImage(
                    url = descriptor.iconUrl,
                    placeholder = painterResource(descriptor.iconRes),
                    modifier = iconModifier,
                )
            }
        },
        modifier = modifier,
    )
}
