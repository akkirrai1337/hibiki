package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppSourceContentList(
    isSearchMode: Boolean,
    bottomContentPadding: Dp,
    searchContent: LazyListScope.() -> Unit,
    sourceContent: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = SourceContentListTopPadding,
            end = 12.dp,
            bottom = bottomContentPadding + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(SourceContentListVerticalSpacing),
    ) {
        if (isSearchMode) searchContent() else sourceContent()
    }
}
