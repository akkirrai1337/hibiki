package org.akkirrai.hibiki.shared.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppCatalogContentList(
    state: LazyListState,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = UiDimens.ScreenPadding,
            top = topContentPadding,
            end = UiDimens.ScreenPadding,
            bottom = bottomContentPadding + UiDimens.ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(CatalogContentItemGap),
        content = content,
    )
}
