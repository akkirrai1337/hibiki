package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
