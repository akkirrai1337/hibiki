package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppCenteredLoading as SharedCenteredLoading
import org.akkirrai.hibiki.shared.design.component.AppLoadMoreBlock as SharedLoadMoreBlock
import org.akkirrai.hibiki.shared.design.component.AppMessageState as SharedMessageState

@Composable
fun AppCenteredLoading(modifier: Modifier = Modifier) = SharedCenteredLoading(modifier)

@Composable
fun AppMessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    iconTint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    iconSlot: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    messageStyle: TextStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
    messageModifier: Modifier = Modifier.padding(top = 6.dp),
    messageMaxLines: Int = Int.MAX_VALUE,
    messageOverflow: TextOverflow = TextOverflow.Clip,
) = SharedMessageState(
    title = title,
    message = message,
    modifier = modifier,
    actionLabel = actionLabel,
    onActionClick = onActionClick,
    icon = icon,
    iconContentDescription = iconContentDescription,
    iconTint = iconTint,
    iconSlot = iconSlot,
    titleStyle = titleStyle,
    messageStyle = messageStyle,
    messageModifier = messageModifier,
    messageMaxLines = messageMaxLines,
    messageOverflow = messageOverflow,
)

@Composable
fun AppLoadMoreBlock(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    loadingLabel: String? = null,
) = SharedLoadMoreBlock(label, onClick, modifier, isLoading, errorMessage, loadingLabel)
