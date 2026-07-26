package org.akkirrai.hibiki.feature.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.component.AppBackButton
import org.akkirrai.hibiki.core.design.component.AppMessageState
import org.akkirrai.hibiki.core.design.component.AppSearchTopBar
import org.akkirrai.hibiki.core.design.component.PosterImage
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppCompactPosterCard
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import org.akkirrai.hibiki.shared.source.sourceItemShape
import org.akkirrai.hibiki.shared.source.SourceSelectionIndicator
import org.akkirrai.hibiki.shared.source.SourceEmptyState
import org.akkirrai.hibiki.shared.source.SourceItemCard
import org.akkirrai.hibiki.shared.source.SourceLanguageSection as SharedSourceLanguageSection
import org.akkirrai.hibiki.shared.source.SourceScreenHeader
import org.akkirrai.hibiki.shared.collection.groupItemsByKeys

@Composable
fun SourcesScreen(
    onBackClick: (() -> Unit)? = null,
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
    val hasSourceSearch = onBackClick == null
    val isSearchMode = hasSourceSearch && searchState.query.trim().length >= 3

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = if (isSearchMode || onBackClick == null) 84.dp else 12.dp,
                end = 12.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isSearchMode) {
                val visibleSections = searchState.sections.filter { section ->
                    section.isLoading || section.error != null || section.items.isNotEmpty()
                }
                visibleSections.forEach { section ->
                    item(key = "search_${section.source.id.value}") {
                        SourceSearchSection(
                            section = section,
                            onRetry = { searchViewModel.retry(section.source.id) },
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
            } else {
                if (onBackClick == null) {
                    item(key = "top_level_header") {
                        Text(
                            text = stringResource(R.string.sources_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    item(key = "header") {
                        SourceScreenHeader(title = stringResource(R.string.settings_sources))
                    }
                }
                SOURCE_LANGUAGE_SECTIONS.forEach { section ->
                    item(key = "${section.language.tag}_sources") {
                        SourceLanguageSection(
                            section = section,
                            sources = sourcesByLanguage[section.language].orEmpty(),
                            selectedSource = selectedSource,
                            onSourceSelected = { source ->
                                onSourceSelected?.invoke(source.id) ?: preferences.setAnimeSource(source.id)
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                        )
                    }
                }
            }
        }

        if (hasSourceSearch) {
            AppSearchTopBar(
                query = searchState.query,
                onQueryChange = searchViewModel::onQueryChange,
                onClear = searchViewModel::clearQuery,
                showFilterButton = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = UiDimens.ScreenPadding, vertical = 12.dp),
            )
        }

        if (onBackClick != null) {
            AppBackButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceSearchSection(
    section: SourceSearchSection,
    onRetry: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = section.source.iconUrl,
                placeholder = painterResource(section.source.iconRes),
                error = painterResource(section.source.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = section.source.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        when {
            section.isLoading -> CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = 8.dp).size(24.dp),
                strokeWidth = 2.dp,
            )
            section.error != null -> Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sources_search_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.search_retry))
                }
            }
            section.items.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
            ) {
                items(section.items, key = { it.id }) { anime ->
                    AppCompactPosterCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                    ) {
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
                    }
                }
            }
        }
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
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.animite_drop_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .requiredSize(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        },
    ) {
                if (sources.isEmpty()) {
                    SourceEmptyState(text = stringResource(R.string.settings_sources_empty))
                } else {
                    sources.forEachIndexed { index, source ->
                        SourceItem(
                            source = source,
                            selected = source.id == selectedSource,
                            shape = sourceItemShape(index = index, count = sources.size),
                            onClick = { onSourceSelected(source) },
                        )
                    }
                }
    }
}

@Composable
private fun SourceItem(
    source: AnimeSourceDescriptor,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    SourceItemCard(
        name = source.name,
        selected = selected,
        shape = shape,
        onClick = onClick,
        iconContent = {
            AsyncImage(
                model = source.iconUrl,
                placeholder = painterResource(source.iconRes),
                error = painterResource(source.iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
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
