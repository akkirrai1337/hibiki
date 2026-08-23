package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
