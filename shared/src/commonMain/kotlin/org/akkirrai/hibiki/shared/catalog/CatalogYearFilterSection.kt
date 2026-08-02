package org.akkirrai.hibiki.shared.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.akkirrai.hibiki.shared.home.AppHomeYearFilter

@Composable
fun AppCatalogYearFilterSection(
    selectedRange: IntRange,
    yearRange: IntRange,
    title: String,
    allLabel: String,
    fromLabel: String,
    toLabel: String,
    onRangeChange: (IntRange) -> Unit,
    arrowIcon: Painter,
) {
    AppHomeYearFilter(
        selectedRange = selectedRange,
        yearRange = yearRange,
        title = title,
        allLabel = allLabel,
        fromLabel = fromLabel,
        toLabel = toLabel,
        onRangeChange = onRangeChange,
        arrowContent = { modifier ->
            androidx.compose.material3.Icon(
                painter = arrowIcon,
                contentDescription = null,
                modifier = modifier,
            )
        },
    )
}
