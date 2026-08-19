package org.akkirrai.hibiki.library.screen
import org.akkirrai.hibiki.library.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.design.UiDimens

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
