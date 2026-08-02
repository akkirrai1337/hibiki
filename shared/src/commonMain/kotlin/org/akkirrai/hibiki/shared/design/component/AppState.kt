package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppCenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun AppMessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSlot: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    messageStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    messageModifier: Modifier = Modifier.padding(top = UiDimens.MessageStateMessageTopPadding),
    messageMaxLines: Int = Int.MAX_VALUE,
    messageOverflow: TextOverflow = TextOverflow.Clip,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            iconSlot != null -> iconSlot()
            icon != null -> Icon(icon, iconContentDescription, tint = iconTint)
        }
        Text(title, modifier = Modifier.padding(top = UiDimens.MessageStateTitleTopPadding), style = titleStyle, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(message, modifier = messageModifier, style = messageStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = messageMaxLines, overflow = messageOverflow)
        if (actionLabel != null && onActionClick != null) {
            FilledTonalButton(onClick = onActionClick, modifier = Modifier.padding(top = UiDimens.MessageStateActionTopPadding)) { Text(actionLabel) }
        }
    }
}

@Composable
fun AppLoadMoreBlock(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    loadingLabel: String? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing)) {
        errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        FilledTonalButton(onClick = onClick, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(UiDimens.LoadMoreBlockSpinnerSize), strokeWidth = UiDimens.LoadMoreSpinnerStrokeWidth)
                loadingLabel?.let { Text(it, modifier = Modifier.padding(start = UiDimens.LoadMoreBlockLabelStartPadding)) }
            } else Text(label)
        }
    }
}
