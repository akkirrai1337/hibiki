package org.akkirrai.hibiki.core.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.state.AppMessageState

fun <T> LazyListScope.appSourceSearchSections(
    sections: List<SourceSearchSectionState<T>>,
    isSearching: Boolean,
    errorLabel: String,
    retryLabel: String,
    onRetry: (String) -> Unit,
    emptyContent: @Composable () -> Unit,
    sourceIconContent: @Composable (SourceSearchSectionState<T>, Modifier) -> Unit,
    itemContent: @Composable (T) -> Unit,
    itemKey: (T) -> Any,
) {
    sections.forEach { section ->
        item(key = "search_${section.sourceId}") {
            AppSourceSearchSection(
                sourceName = section.sourceName,
                isLoading = section.isLoading,
                hasError = section.hasError,
                errorLabel = errorLabel,
                retryLabel = retryLabel,
                onRetry = { onRetry(section.sourceId) },
                items = section.items,
                itemKey = itemKey,
                sourceIconContent = { modifier -> sourceIconContent(section, modifier) },
                itemContent = itemContent,
            )
        }
    }
    if (!isSearching && sections.isEmpty()) {
        item(key = "search_empty") {
            emptyContent()
        }
    }
}

@Composable
fun <T> AppSourceSearchSection(
    sourceName: String,
    isLoading: Boolean,
    hasError: Boolean,
    errorLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    items: List<T>,
    itemKey: (T) -> Any,
    sourceIconContent: @Composable (Modifier) -> Unit,
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SourceSearchSectionVerticalSpacing),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SourceSearchSectionHeaderHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sourceIconContent(
                Modifier
                    .size(SourceSearchSectionIconSize)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(SourceSearchSectionIconTextGap))
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.padding(horizontal = SourceSearchSectionLoadingHorizontalPadding)
                    .size(SourceSearchSectionLoadingIndicatorSize),
                strokeWidth = SourceSearchSectionLoadingStrokeWidth,
            )
            hasError -> Row(
                modifier = Modifier.padding(horizontal = SourceSearchSectionErrorHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = errorLabel,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text(retryLabel)
                }
            }
            items.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(horizontal = SourceSearchSectionResultsHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
            ) {
                items(items = items, key = itemKey) { item ->
                    itemContent(item)
                }
            }
        }
    }
}

@Composable
fun AppSourceSearchEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    AppMessageState(
        title = title,
        message = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SourceSearchEmptyHorizontalPadding),
    )
}
