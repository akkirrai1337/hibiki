package org.akkirrai.hibiki.core.source

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp

data class AppSourceOption(
    val id: String,
    val name: String,
)

data class AppSourceSection(
    val key: String,
    val title: String,
    val sources: List<AppSourceOption>,
)

/** Shared static source-selection screen; search results remain a host slot. */
@Composable
fun AppSourcesScreen(
    sections: List<AppSourceSection>,
    selectedSourceId: String,
    sectionChevron: ImageVector,
    emptyLabel: String,
    sourceIconContent: @Composable (AppSourceOption) -> Unit,
    searchBarContent: @Composable () -> Unit,
    onSourceSelected: (AppSourceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, top = 84.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            sections.forEach { section ->
                item(key = "${section.key}_sources") {
                    AppSourceSectionContent(
                        section = section,
                        selectedSourceId = selectedSourceId,
                        sectionChevron = sectionChevron,
                        emptyLabel = emptyLabel,
                        sourceIconContent = sourceIconContent,
                        onSourceSelected = onSourceSelected,
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) { searchBarContent() }
    }
}

@Composable
private fun AppSourceSectionContent(
    section: AppSourceSection,
    selectedSourceId: String,
    sectionChevron: ImageVector,
    emptyLabel: String,
    sourceIconContent: @Composable (AppSourceOption) -> Unit,
    onSourceSelected: (AppSourceOption) -> Unit,
) {
    var expanded by rememberSaveable(section.key) { mutableStateOf(true) }
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "${section.key}_sources_arrow")
    SourceLanguageSection(
        title = section.title,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        trailingContent = {
            Icon(sectionChevron, null, Modifier.size(16.dp).then(Modifier.graphicsLayer { rotationZ = rotation }), tint = MaterialTheme.colorScheme.onBackground)
        },
    ) {
        if (section.sources.isEmpty()) {
            SourceEmptyState(text = emptyLabel)
        } else {
            section.sources.chunked(2).forEach { rowSources ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSources.forEach { source ->
                        AppSourceGridCard(
                            source = source,
                            selected = source.id == selectedSourceId,
                            iconContent = { sourceIconContent(source) },
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
private fun AppSourceGridCard(
    source: AppSourceOption,
    selected: Boolean,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.height(76.dp).clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { iconContent() }
            Spacer(Modifier.size(10.dp))
            Text(source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, modifier = Modifier.weight(1f))
        }
    }
}
