package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

@Composable
fun AppSourceScreenLayout(
    isSearchMode: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    topContentPadding: androidx.compose.ui.unit.Dp = SourceContentListTopPadding,
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
            topContentPadding = topContentPadding,
            searchContent = searchContent,
            sourceContent = sourceContent,
        )
        searchBarContent()
    }
}

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

@Composable
fun SourceScreenHeader(
    title: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SourceScreenHeaderHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = SourceScreenHeaderTitleStartPadding),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun SourceEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(SourceEmptyStateCornerRadius),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = SourceEmptyStateHorizontalPadding,
                vertical = SourceEmptyStateVerticalPadding,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
