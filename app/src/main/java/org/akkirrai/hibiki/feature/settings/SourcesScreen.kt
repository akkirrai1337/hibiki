package org.akkirrai.hibiki.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.SourceEmptyState
import org.akkirrai.hibiki.shared.source.AppSourceGridItem
import org.akkirrai.hibiki.shared.source.AppSourceLanguageContent
import org.akkirrai.hibiki.shared.source.SourceLanguageSectionContent
import org.akkirrai.hibiki.shared.source.appSourceLanguageSections
import org.akkirrai.hibiki.shared.source.AppSourceSearchBar
import org.akkirrai.hibiki.shared.source.AppSourceSearchSection
import org.akkirrai.hibiki.shared.source.AppSourceSearchPosterPlaceholder
import org.akkirrai.hibiki.shared.source.AppSourceIconImage
import org.akkirrai.hibiki.shared.source.SourceSearchPosterCardWidth
import org.akkirrai.hibiki.shared.source.AppSourceSearchEmptyState
import org.akkirrai.hibiki.shared.source.AppSourceSearchAnimeCard
import org.akkirrai.hibiki.shared.source.SourceScreenDefaultBottomContentPadding
import org.akkirrai.hibiki.shared.source.AppSourceScreenLayout
import org.akkirrai.hibiki.shared.source.isSourceSearchActive
import org.akkirrai.hibiki.shared.source.visibleSourceSearchSections
import org.akkirrai.hibiki.shared.collection.groupItemsByKeys

@Composable
fun SourcesScreen(
    bottomContentPadding: androidx.compose.ui.unit.Dp = SourceScreenDefaultBottomContentPadding,
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
    val isSearchMode = isSourceSearchActive(query)
    val announcementLabel = stringResource(R.string.anime_meta_announcement)
    val movieLabel = stringResource(R.string.anime_meta_movie)
    val visibleSourcesByLanguage = sourcesByLanguage
    val visibleSourceSections = SOURCE_LANGUAGE_SECTIONS.map { section ->
        SourceLanguageSectionContent(
            key = section.language.tag,
            title = stringResource(section.labelRes),
            items = visibleSourcesByLanguage[section.language].orEmpty(),
        )
    }

    AppSourceScreenLayout(
        isSearchMode = isSearchMode,
        bottomContentPadding = bottomContentPadding,
        searchContent = {
            val visibleSections = searchState.sections.visibleSourceSearchSections()
            visibleSections.forEach { section ->
                val source = AnimeSourceRegistry.sources.first { it.id.value == section.sourceId }
                item(key = "search_${section.sourceId}") {
                    AppSourceSearchSection(
                        sourceName = section.sourceName,
                        isLoading = section.isLoading,
                        hasError = section.hasError,
                        errorLabel = stringResource(R.string.sources_search_failed),
                        retryLabel = stringResource(R.string.search_retry),
                        onRetry = { searchViewModel.retry(SourceId(section.sourceId)) },
                        items = section.items,
                        itemKey = { it.id },
                        sourceIconContent = { iconModifier ->
                            AppSourceIconImage(
                                url = source.iconUrl,
                                placeholder = painterResource(source.iconRes),
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
                    )
                }
            }
            if (!searchState.isSearching && visibleSections.isEmpty()) {
                item(key = "search_empty") {
                    AppSourceSearchEmptyState(
                        title = stringResource(R.string.sources_search_empty_title),
                        message = stringResource(R.string.sources_search_empty_message),
                    )
                }
            }
        },
        sourceContent = {
            appSourceLanguageSections(
                sections = visibleSourceSections,
                trailingContent = { iconModifier ->
                    androidx.compose.material3.Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = iconModifier,
                    )
                },
                emptyContent = {
                    SourceEmptyState(text = stringResource(R.string.settings_sources_empty))
                },
                isSelected = { source -> source.id == selectedSource },
                itemContent = { source, selected, itemModifier ->
                    AppSourceGridItem(
                        name = source.name,
                        selected = selected,
                        onClick = {
                            onSourceSelected?.invoke(source.id)
                                ?: preferences.setAnimeSource(source.id)
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        },
                        modifier = itemModifier,
                        iconContent = { iconModifier ->
                            AppSourceIconImage(
                                url = source.iconUrl,
                                placeholder = painterResource(source.iconRes),
                                modifier = iconModifier,
                            )
                        },
                    )
                },
            )
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
                    showFilterButton = false,
                    onFilterClick = {},
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
        modifier = modifier,
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
