package org.akkirrai.hibiki.shared.home.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.search.AppSearchTopBar
import org.akkirrai.hibiki.shared.design.component.floating.AppTopScrim

@Composable
fun AppHomeSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterContentDescription: String,
    clearContentDescription: String,
    onFilterClick: () -> Unit,
    showFilterButton: Boolean,
    scrimHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        AppTopScrim(
            modifier = Modifier.align(Alignment.TopCenter),
            height = scrimHeight,
        )
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = UiDimens.SearchBarTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                ),
        )
    }
}
