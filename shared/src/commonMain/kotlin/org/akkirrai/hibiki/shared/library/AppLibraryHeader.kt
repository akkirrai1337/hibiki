package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun <T> AppLibraryHeader(
    searchContent: @Composable () -> Unit,
    selected: T,
    categories: List<T>,
    counts: Map<T, Int>,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        searchContent()
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
