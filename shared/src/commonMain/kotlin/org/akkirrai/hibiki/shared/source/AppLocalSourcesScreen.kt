package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppLocalSourcesScreen(
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    bottomContentPadding: Dp,
    emptyText: String,
    onSourceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = sources
        .groupBy(AppSourceDescriptor::language)
        .toList()
        .map { (language, items) ->
            SourceLanguageSectionContent(
                key = language,
                title = language.uppercase(),
                items = items,
            )
        }
    AppSourceScreenLayout(
        isSearchMode = false,
        bottomContentPadding = bottomContentPadding,
        searchContent = {},
        sourceContent = {
            appSourceLanguageSections(
                sections = sections,
                trailingContent = { iconModifier ->
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = iconModifier,
                    )
                },
                emptyContent = { SourceEmptyState(text = emptyText) },
                isSelected = { source -> source.id == selectedSourceId },
                itemContent = { source, selected, itemModifier ->
                    AppSourceGridItem(
                        name = source.name,
                        selected = selected,
                        onClick = { onSourceSelected(source.id) },
                        modifier = itemModifier,
                        iconContent = { iconModifier ->
                            AppSourceIconImage(
                                url = source.iconUrl,
                                placeholder = null,
                                modifier = iconModifier,
                            )
                        },
                    )
                },
            )
        },
        searchBarContent = {},
        modifier = modifier,
    )
}
