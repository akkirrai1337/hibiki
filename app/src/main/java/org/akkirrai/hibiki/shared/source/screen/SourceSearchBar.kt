package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.search.AppSearchTopBar

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
    onAddSource: (() -> Unit)? = null,
    addContentDescription: String = "Add source",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ScreenPadding, vertical = SourceSearchBarVerticalPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.weight(1f),
            )
            onAddSource?.let { onAdd ->
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = addContentDescription)
                }
            }
        }
    }
}
