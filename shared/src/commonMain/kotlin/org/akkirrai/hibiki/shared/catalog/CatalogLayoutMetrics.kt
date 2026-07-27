package org.akkirrai.hibiki.shared.catalog

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.shared.design.UiDimens

val CatalogHeaderTopPadding = UiDimens.SearchBarTopPadding
val CatalogSearchBarHeight = UiDimens.SearchBarHeight
val CatalogSortVerticalGap = 8.dp
val CatalogSortControlHeight = 28.dp
val CatalogSortMenuTitleTopPadding = 8.dp
val CatalogSortMenuTitleFontSize = 10.sp
val CatalogContentTopPadding = CatalogHeaderTopPadding +
    CatalogSearchBarHeight +
    CatalogSortVerticalGap +
    CatalogSortVerticalGap +
    CatalogSortControlHeight +
    CatalogSortVerticalGap
const val CatalogSortAnimationDurationMs = 220
