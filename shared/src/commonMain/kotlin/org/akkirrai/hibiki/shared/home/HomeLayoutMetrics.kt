package org.akkirrai.hibiki.shared.home

import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

val HomeContentTopPadding = UiDimens.SearchBarTopPadding +
    UiDimens.SearchBarHeight +
    UiDimens.ScreenPadding

val HomeTopSearchScrimHeight = HomeContentTopPadding + 18.dp

val HomePullRefreshIndicatorTopOffset =
    UiDimens.SearchBarTopPadding + UiDimens.SearchBarHeight - 8.dp
