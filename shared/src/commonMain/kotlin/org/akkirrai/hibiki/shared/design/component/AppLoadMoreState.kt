package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppLoadMoreState(
    isLoading: Boolean,
    errorMessage: String?,
    errorIcon: ImageVector,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> Box(
            modifier = modifier.fillMaxWidth().padding(vertical = UiDimens.LoadMoreLoadingVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(UiDimens.LoadMoreSpinnerSize),
                strokeWidth = UiDimens.LoadMoreSpinnerStrokeWidth,
            )
        }
        errorMessage != null -> Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onRetry)
                .padding(vertical = UiDimens.LoadMoreErrorVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiDimens.LoadMoreContentGap),
            ) {
                Icon(
                    errorIcon,
                    contentDescription = null,
                    modifier = Modifier.size(UiDimens.LoadMoreErrorIconSize),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
