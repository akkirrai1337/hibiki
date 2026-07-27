package org.akkirrai.hibiki.shared.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

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
                strokeWidth = 2.dp,
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
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
            ) {
                items(items = items, key = itemKey) { item ->
                    itemContent(item)
                }
            }
        }
    }
}
