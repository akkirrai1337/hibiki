package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppSearchTopBar
import org.akkirrai.hibiki.shared.design.component.AppTopScrim

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
    headerTopPadding: Dp,
    sortVerticalGap: Dp,
    sortModifier: Modifier,
    sortContent: @Composable () -> Unit,
    searchIcon: ImageVector,
    filterIcon: ImageVector,
    clearIcon: ImageVector,
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
                    top = headerTopPadding,
                    start = UiDimens.ScreenPadding,
                    end = UiDimens.ScreenPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sortVerticalGap),
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
                modifier = Modifier.zIndex(1f),
            )
            sortContent()
        }
    }
}
