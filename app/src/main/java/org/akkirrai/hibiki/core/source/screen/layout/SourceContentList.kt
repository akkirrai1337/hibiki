package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppSourceContentList(
    isSearchMode: Boolean,
    bottomContentPadding: Dp,
    topContentPadding: Dp = SourceContentListTopPadding,
    searchContent: LazyListScope.() -> Unit,
    sourceContent: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SourceContentListHorizontalPadding,
            top = topContentPadding,
            end = SourceContentListHorizontalPadding,
            bottom = bottomContentPadding + SourceContentListBottomExtraPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SourceContentListVerticalSpacing),
    ) {
        if (isSearchMode) searchContent() else sourceContent()
    }
}
