package org.akkirrai.hibiki.core.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R

@Composable
fun AppSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFilterClick: () -> Unit = {},
    showFilterButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    org.akkirrai.hibiki.shared.design.component.AppSearchTopBar(
        query = query,
        onQueryChange = onQueryChange,
        onClear = onClear,
        placeholder = stringResource(R.string.search_placeholder),
        filterContentDescription = stringResource(R.string.search_filters),
        clearContentDescription = stringResource(R.string.home_search_clear),
        searchIcon = Icons.Outlined.Search,
        filterIcon = Icons.Outlined.FilterList,
        clearIcon = Icons.Outlined.Close,
        onFilterClick = onFilterClick,
        showFilterButton = showFilterButton,
        modifier = modifier,
    )
}
