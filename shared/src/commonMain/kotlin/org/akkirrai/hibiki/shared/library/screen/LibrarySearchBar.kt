package org.akkirrai.hibiki.shared.library.screen
import org.akkirrai.hibiki.shared.library.*

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.design.component.search.AppSearchTopBar

@Composable
fun AppLibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterContentDescription: String,
    clearContentDescription: String,
    onFilterClick: () -> Unit,
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
        modifier = modifier.fillMaxWidth(),
    )
}
