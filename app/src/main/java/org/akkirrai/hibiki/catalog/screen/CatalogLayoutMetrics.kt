package org.akkirrai.hibiki.catalog.screen

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akkirrai.hibiki.design.UiDimens

val CatalogHeaderTopPadding = UiDimens.SearchBarTopPadding
val CatalogSearchBarHeight = UiDimens.SearchBarHeight
val CatalogSortVerticalGap = 8.dp
val CatalogSortControlHeight = 28.dp
val CatalogSortMenuTitleTopPadding = 8.dp
val CatalogSortMenuTitleFontSize = 10.sp
val CatalogSortMenuWidth = 196.dp
val CatalogSortMenuOffsetY = 4.dp
val CatalogSortMenuCornerRadius = 26.dp
val CatalogSortPillHorizontalPadding = 10.dp
val CatalogSortPillVerticalPadding = 4.dp
val CatalogSortPillContentGap = 4.dp
val CatalogSortPillIconSize = 11.dp
val CatalogSortPillFontSize = 11.sp
val CatalogSortMenuSelectedIconSize = 16.dp
val CatalogSortMenuItemZeroIconSize = 0.dp
val CatalogSortMenuContentGap = 8.dp
val CatalogSortMenuContentHorizontalPadding = 16.dp
val CatalogSortMenuContentVerticalPadding = 8.dp
val CatalogSortMenuItemPadding = 4.dp
val CatalogSortMenuItemCornerRadius = 24.dp
val CatalogContentItemGap = 8.dp
val CatalogContentTopPadding = CatalogHeaderTopPadding +
    CatalogSearchBarHeight +
    CatalogSortVerticalGap +
    CatalogSortVerticalGap +
    CatalogSortControlHeight +
    CatalogSortVerticalGap

fun catalogContentTopPadding(hasSort: Boolean) = if (hasSort) {
    CatalogContentTopPadding
} else {
    CatalogHeaderTopPadding + CatalogSearchBarHeight + CatalogSortVerticalGap
}
