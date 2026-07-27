package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppSearchTopBar

@Composable
fun AppSourceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterContentDescription: String,
    clearContentDescription: String,
    showFilterButton: Boolean,
    onFilterClick: () -> Unit,
    searchIcon: ImageVector,
    filterIcon: ImageVector,
    clearIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ScreenPadding, vertical = SourceSearchBarVerticalPadding),
    ) {
        AppSearchTopBar(
            query = query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            placeholder = placeholder,
            filterContentDescription = filterContentDescription,
            clearContentDescription = clearContentDescription,
            searchIcon = searchIcon,
            filterIcon = filterIcon,
            clearIcon = clearIcon,
            onFilterClick = onFilterClick,
            showFilterButton = showFilterButton,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
