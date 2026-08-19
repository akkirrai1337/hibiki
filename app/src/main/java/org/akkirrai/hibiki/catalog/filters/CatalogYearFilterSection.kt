package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.catalog.*
import org.akkirrai.hibiki.home.model.AppHomeYearFilter

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

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
