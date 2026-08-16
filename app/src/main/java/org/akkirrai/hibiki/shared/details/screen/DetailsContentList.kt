package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun AppDetailsContentList(
    state: LazyListState,
    bottomContentPadding: Dp,
    additionalBottomPadding: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        overscrollEffect = null,
        contentPadding = PaddingValues(
            bottom = bottomContentPadding + additionalBottomPadding,
        ),
        content = content,
    )
}
