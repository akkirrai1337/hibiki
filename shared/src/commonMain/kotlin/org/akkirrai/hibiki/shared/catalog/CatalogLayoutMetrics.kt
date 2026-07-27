package org.akkirrai.hibiki.shared.catalog

import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

val CatalogHeaderTopPadding = UiDimens.SearchBarTopPadding
val CatalogSearchBarHeight = UiDimens.SearchBarHeight
val CatalogSortVerticalGap = 8.dp
val CatalogSortControlHeight = 28.dp
val CatalogContentTopPadding = CatalogHeaderTopPadding +
    CatalogSearchBarHeight +
    CatalogSortVerticalGap +
    CatalogSortVerticalGap +
    CatalogSortControlHeight +
    CatalogSortVerticalGap
const val CatalogSortAnimationDurationMs = 220
