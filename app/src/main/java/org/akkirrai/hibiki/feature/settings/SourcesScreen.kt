package org.akkirrai.hibiki.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.design.component.AppMessageState
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.SourceEmptyState
import org.akkirrai.hibiki.shared.source.AppSourceGridItem
import org.akkirrai.hibiki.shared.source.AppSourceLanguageContent
import org.akkirrai.hibiki.shared.source.AppSourceSearchBar
import org.akkirrai.hibiki.shared.source.AppSourceSearchSection
import org.akkirrai.hibiki.shared.source.AppSourceScreenLayout
import org.akkirrai.hibiki.shared.collection.groupItemsByKeys

@Composable
fun SourcesScreen(
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
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
    val haptic = LocalHapticFeedback.current
    val sourcesByLanguage = groupSourcesByLanguage(AnimeSourceRegistry.sources)
    val searchState by searchViewModel.uiState.collectAsState()
    val hasSourceSearch = true
    val query = searchState.query.trim()
    val isSearchMode = query.length >= 3
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val visibleSourcesByLanguage = sourcesByLanguage

    AppSourceScreenLayout(
        isSearchMode = isSearchMode,
        bottomContentPadding = bottomContentPadding,
        searchContent = {
            val visibleSections = searchState.sections.filter { section ->
                section.isLoading || section.hasError || section.items.isNotEmpty()
            }
            visibleSections.forEach { section ->
                val source = AnimeSourceRegistry.sources.first { it.id.value == section.sourceId }
                item(key = "search_${section.sourceId}") {
                    SourceSearchSection(
                        section = section,
                        source = source,
                        announcementLabel = announcementLabel,
                        movieLabel = movieLabel,
                        onRetry = { searchViewModel.retry(SourceId(section.sourceId)) },
                        onAnimeClick = onAnimeClick,
                    )
                }
            }
            if (!searchState.isSearching && visibleSections.isEmpty()) {
                item(key = "search_empty") {
                    AppMessageState(
                        title = stringResource(R.string.sources_search_empty_title),
                        message = stringResource(R.string.sources_search_empty_message),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        },
        sourceContent = {
            SOURCE_LANGUAGE_SECTIONS.forEach { section ->
                val sectionSources = visibleSourcesByLanguage[section.language]
                    .orEmpty()
                if (sectionSources.isEmpty()) return@forEach
                item(key = "${section.language.tag}_sources") {
                    SourceLanguageSection(
                        section = section,
                        sources = sectionSources,
                        selectedSource = selectedSource,
                        onSourceSelected = { source ->
                            onSourceSelected?.invoke(source.id) ?: preferences.setAnimeSource(source.id)
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                    )
                }
            }
        },
        searchBarContent = {
            if (hasSourceSearch) {
                AppSourceSearchBar(
                    query = query,
                    onQueryChange = searchViewModel::onQueryChange,
                    onClear = searchViewModel::clearQuery,
                    placeholder = stringResource(R.string.search_placeholder),
                    filterContentDescription = stringResource(R.string.search_filters),
                    clearContentDescription = stringResource(R.string.home_search_clear),
                    searchIcon = Icons.Outlined.Search,
                    filterIcon = Icons.Outlined.FilterList,
                    clearIcon = Icons.Outlined.Close,
                    showFilterButton = false,
                    onFilterClick = {},
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SourceLanguageSection(
    section: SourceLanguageSectionConfig,
    sources: List<AnimeSourceDescriptor>,
    selectedSource: SourceId,
    onSourceSelected: (AnimeSourceDescriptor) -> Unit,
) {
    AppSourceLanguageContent(
        stateKey = section.language.tag,
        title = stringResource(section.labelRes),
        items = sources,
        trailingContent = { iconModifier ->
            androidx.compose.material3.Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(16.dp)
                    .then(iconModifier),
            )
        },
        emptyContent = {
            SourceEmptyState(text = stringResource(R.string.settings_sources_empty))
        },
        isSelected = { source -> source.id == selectedSource },
        itemContent = { source, selected, itemModifier ->
                SourceGridItem(
                    source = source,
                    selected = selected,
                    onClick = { onSourceSelected(source) },
                    modifier = itemModifier,
                )
        },
    )
}

@Composable
private fun SourceSearchSection(
    section: SourceSearchSection,
    source: AnimeSourceDescriptor,
    announcementLabel: String,
    movieLabel: String,
    onRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    AppSourceSearchSection(
        sourceName = section.sourceName,
        isLoading = section.isLoading,
        hasError = section.hasError,
        errorLabel = stringResource(R.string.sources_search_failed),
        retryLabel = stringResource(R.string.search_retry),
        onRetry = onRetry,
        items = section.items,
        itemKey = { it.id },
        sourceIconContent = {
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(CircleShape),
            )
        },
        itemContent = { anime ->
                    AppPosterAnimeCard(
                        anime = anime,
                        metaText = anime.buildCardMeta(
                            announcementLabel = announcementLabel,
                            movieLabel = movieLabel,
                        ),
                        onClick = { onAnimeClick(anime) },
                        modifier = Modifier.width(154.dp),
                        posterContent = {
                            PosterImage(
                                primaryUrl = anime.posterUrl,
                                fallbackUrl = anime.posterFallbackUrl,
                                contentDescription = anime.title,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                    )
                                },
                            )
                        },
                    )
        },
    )
}

@Composable
private fun SourceGridItem(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSourceGridItem(
        name = source.name,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        iconContent = {
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        },
    )
}

internal fun groupSourcesByLanguage(
    sources: List<AnimeSourceDescriptor>,
): Map<SourceLanguage, List<AnimeSourceDescriptor>> = groupItemsByKeys(
    items = sources,
    keys = SOURCE_LANGUAGE_SECTIONS.map(SourceLanguageSectionConfig::language),
    keyOf = AnimeSourceDescriptor::language,
)

private data class SourceLanguageSectionConfig(
    val language: SourceLanguage,
    @param:StringRes val labelRes: Int,
)

private val SOURCE_LANGUAGE_SECTIONS = listOf(
    SourceLanguageSectionConfig(SourceLanguage.RUSSIAN, R.string.settings_sources_language_ru),
    SourceLanguageSectionConfig(SourceLanguage.ENGLISH, R.string.settings_sources_language_en),
)
