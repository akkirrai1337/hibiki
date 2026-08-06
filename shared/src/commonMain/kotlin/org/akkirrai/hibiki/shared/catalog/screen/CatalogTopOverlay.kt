package org.akkirrai.hibiki.shared.catalog.screen

import org.akkirrai.hibiki.shared.catalog.sort.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.shared.design.component.floating.AppTopScrim

@Composable
fun AppCatalogTopOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterContentDescription: String,
    clearContentDescription: String,
    onFilterClick: () -> Unit,
    showFilterButton: Boolean,
    sortModifier: Modifier,
    sortContent: @Composable () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        AppTopScrim(modifier = Modifier.align(Alignment.TopStart))
        Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                .padding(
                    top = CatalogHeaderTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CatalogSortVerticalGap),
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
                onFocusChanged = onSearchFocusChanged,
                modifier = Modifier.zIndex(1f),
            )
            Box(modifier = sortModifier) {
                sortContent()
            }
        }
    }
}
