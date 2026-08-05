package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

@Composable
fun AppSourceScreenLayout(
    isSearchMode: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    searchContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    sourceContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    searchBarContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppSourceContentList(
            isSearchMode = isSearchMode,
            bottomContentPadding = bottomContentPadding,
            searchContent = searchContent,
            sourceContent = sourceContent,
        )
        searchBarContent()
    }
}
