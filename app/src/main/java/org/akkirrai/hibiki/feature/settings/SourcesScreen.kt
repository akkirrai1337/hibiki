package org.akkirrai.hibiki.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.model.buildCardMeta
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.SourceEmptyState
import org.akkirrai.hibiki.shared.source.SourceLanguageSection as SharedSourceLanguageSection
import org.akkirrai.hibiki.shared.source.AppSourcesScreen
import org.akkirrai.hibiki.shared.source.AppSourceOption
import org.akkirrai.hibiki.shared.source.AppSourceSection
import org.akkirrai.hibiki.shared.source.AppSourceSearchSection
import org.akkirrai.hibiki.shared.source.AppSourcesSearchScreen
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

    if (!isSearchMode) {
        val staticSections = SOURCE_LANGUAGE_SECTIONS.map { section ->
            AppSourceSection(
                key = section.language.tag,
                title = stringResource(section.labelRes),
                sources = visibleSourcesByLanguage[section.language].orEmpty().map { source ->
                    AppSourceOption(id = source.id.value, name = source.name)
                },
            )
        }
        AppSourcesScreen(
            sections = staticSections,
            selectedSourceId = selectedSource.value,
            sectionChevron = ImageVector.vectorResource(R.drawable.animite_drop_down),
            emptyLabel = stringResource(R.string.settings_sources_empty),
            sourceIconContent = { option ->
                AnimeSourceRegistry.sources.firstOrNull { it.id.value == option.id }?.let { source ->
                    AsyncImage(
                        model = source.iconUrl,
                        placeholder = painterResource(source.iconRes),
                        error = painterResource(source.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                }
            },
            searchBarContent = {
                SourcesSearchBar(
                    query = query,
                    onQueryChange = searchViewModel::onQueryChange,
                    onClear = searchViewModel::clearQuery,
                    showFilterButton = false,
                    onFilterClick = {},
                    modifier = Modifier,
                )
            },
            onSourceSelected = { option ->
                val sourceId = SourceId(option.id)
                onSourceSelected?.invoke(sourceId) ?: preferences.setAnimeSource(sourceId)
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            modifier = modifier,
        )
        return
    }

    AppSourcesSearchScreen(
        sections = searchState.sections.map { section ->
            AppSourceSearchSection(
                id = section.source.id.value,
                name = section.source.name,
                items = section.items,
                isLoading = section.isLoading,
                hasError = section.error != null,
            )
        },
        isSearching = searchState.isSearching,
        searchBarContent = {
            SourcesSearchBar(
                query = query,
                onQueryChange = searchViewModel::onQueryChange,
                onClear = searchViewModel::clearQuery,
                showFilterButton = false,
                onFilterClick = {},
                modifier = Modifier,
            )
        },
        sourceIconContent = { section ->
            AnimeSourceRegistry.sources.firstOrNull { it.id.value == section.id }?.let { source ->
                AsyncImage(
                    model = source.iconUrl,
                    placeholder = painterResource(source.iconRes),
                    error = painterResource(source.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                )
            }
        },
        metaText = { anime -> anime.buildCardMeta(announcementLabel, movieLabel) },
        posterContent = { anime ->
            PosterImage(
                primaryUrl = anime.posterUrl,
                fallbackUrl = anime.posterFallbackUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                },
            )
        },
        onRetry = { sourceId -> searchViewModel.retry(SourceId(sourceId)) },
        onAnimeClick = onAnimeClick,
        loadingLabel = stringResource(R.string.search_loading),
        errorLabel = stringResource(R.string.sources_search_failed),
        retryLabel = stringResource(R.string.search_retry),
        emptyTitle = stringResource(R.string.sources_search_empty_title),
        emptyMessage = stringResource(R.string.sources_search_empty_message),
        emptyIcon = Icons.Outlined.SearchOff,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
    )
}

@Composable
private fun SourcesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    showFilterButton: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ScreenPadding, vertical = 12.dp),
    ) {
        AppSearchTopBar(
            query = query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            onFilterClick = onFilterClick,
            showFilterButton = showFilterButton,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SourceLanguageSection(
    section: SourceLanguageSectionConfig,
    sources: List<AnimeSourceDescriptor>,
    selectedSource: SourceId,
    onSourceSelected: (AnimeSourceDescriptor) -> Unit,
) {
    var expanded by rememberSaveable(section.language.tag) { mutableStateOf(true) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "${section.language.tag}_sources_arrow",
    )

    SharedSourceLanguageSection(
        title = stringResource(section.labelRes),
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        trailingContent = {
            androidx.compose.material3.Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        },
    ) {
        if (sources.isEmpty()) {
            SourceEmptyState(text = stringResource(R.string.settings_sources_empty))
        } else {
            sources.chunked(2).forEach { rowSources ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSources.forEach { source ->
                        SourceGridItem(
                            source = source,
                            selected = source.id == selectedSource,
                            onClick = { onSourceSelected(source) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowSources.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourceGridItem(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .height(76.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
