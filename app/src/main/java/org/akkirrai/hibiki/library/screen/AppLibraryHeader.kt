package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.search.AppSearchTopBar

@Composable
fun <T> AppLibraryHeader(
    searchContent: @Composable (Modifier) -> Unit,
    selected: T,
    categories: List<T>,
    counts: Map<T, Int>,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LibraryHeaderContentGap)) {
        searchContent(
            Modifier.padding(
                top = UiDimens.SearchBarTopPadding,
                start = UiDimens.ScreenPadding,
                end = UiDimens.ScreenPadding,
            ),
        )
        AppLibraryCategoryChips(
            selected = selected,
            categories = categories,
            counts = counts,
            label = label,
            icon = icon,
            onSelected = onSelected,
        )
    }
}

@Composable
fun AppLibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterContentDescription: String,
    clearContentDescription: String,
    onFilterClick: () -> Unit,
    showFilterButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AppSearchTopBar(
        query = query,
        onQueryChange = onQueryChange,
        onClear = onClear,
        placeholder = placeholder,
        filterContentDescription = filterContentDescription,
        clearContentDescription = clearContentDescription,
        searchIcon = Icons.Outlined.Search,
        filterIcon = Icons.Outlined.FilterList,
        clearIcon = Icons.Outlined.Close,
        onFilterClick = onFilterClick,
        showFilterButton = showFilterButton,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> AppLibraryCategoryChips(
    selected: T,
    categories: List<T>,
    counts: Map<T, Int>,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LibraryCategoryChipsItemGap),
        contentPadding = PaddingValues(horizontal = LibraryCategoryChipsHorizontalPadding),
    ) {
        items(categories) { category ->
            val isSelected = category == selected
            val count = counts[category] ?: 0
            Surface(
                modifier = Modifier,
                onClick = { onSelected(category) },
                shape = RoundedCornerShape(LibraryCategoryChipCornerRadius),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(LibraryCategoryChipBorderWidth, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
            ) {
                Row(Modifier.padding(horizontal = LibraryCategoryChipHorizontalPadding, vertical = LibraryCategoryChipVerticalPadding), horizontalArrangement = Arrangement.spacedBy(LibraryCategoryChipContentGap), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon(category), null, Modifier.size(LibraryCategoryChipIconSize))
                    Text(if (count > 0) "${label(category)} $count" else label(category), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
