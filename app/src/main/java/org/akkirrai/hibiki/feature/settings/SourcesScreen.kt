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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.app.settings.LocalAppPreferences
import org.akkirrai.hibiki.app.settings.LocalAppPreferencesState
import org.akkirrai.hibiki.core.design.component.AppBackButton
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedSourceOverride: SourceId? = null,
    onSourceSelected: ((SourceId) -> Unit)? = null,
) {
    val preferences = LocalAppPreferences.current
    val selectedSource = selectedSourceOverride ?: LocalAppPreferencesState.current.animeSource
    val haptic = LocalHapticFeedback.current
    val sourcesByLanguage = groupSourcesByLanguage(AnimeSourceRegistry.sources)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                SourceScreenHeader(
                    title = stringResource(R.string.settings_sources),
                )
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

        AppBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp),
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
