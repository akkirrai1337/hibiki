package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Composable
fun AppFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    style: AppFilledIconButtonStyle = AppFilledIconButtonStyle.Surface,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = style.containerColor(),
            contentColor = style.contentColor(),
            disabledContainerColor = style.disabledContainerColor(),
            disabledContentColor = style.disabledContentColor(),
        ),
        content = content,
    )
}

enum class AppFilledIconButtonStyle { Surface, PrimaryContainer, DarkOverlay, HeroOverlay }

@Composable
private fun AppFilledIconButtonStyle.containerColor(): Color = when (this) {
    AppFilledIconButtonStyle.Surface -> MaterialTheme.colorScheme.surfaceContainerHigh
    AppFilledIconButtonStyle.PrimaryContainer -> MaterialTheme.colorScheme.primaryContainer
    AppFilledIconButtonStyle.DarkOverlay -> Color.Black.copy(alpha = 0.58f)
    AppFilledIconButtonStyle.HeroOverlay -> Color.Black.copy(alpha = 0.28f)
}

@Composable
private fun AppFilledIconButtonStyle.contentColor(): Color = when (this) {
    AppFilledIconButtonStyle.Surface -> MaterialTheme.colorScheme.onSurfaceVariant
    AppFilledIconButtonStyle.PrimaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer
    AppFilledIconButtonStyle.DarkOverlay -> Color.White
    AppFilledIconButtonStyle.HeroOverlay -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun AppFilledIconButtonStyle.disabledContainerColor(): Color = when (this) {
    AppFilledIconButtonStyle.Surface -> MaterialTheme.colorScheme.surfaceContainerHigh
    AppFilledIconButtonStyle.PrimaryContainer -> MaterialTheme.colorScheme.primaryContainer
    AppFilledIconButtonStyle.DarkOverlay -> Color.Black.copy(alpha = 0.22f)
    AppFilledIconButtonStyle.HeroOverlay -> Color.Black.copy(alpha = 0.16f)
}

@Composable
private fun AppFilledIconButtonStyle.disabledContentColor(): Color = when (this) {
    AppFilledIconButtonStyle.Surface -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    AppFilledIconButtonStyle.PrimaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    AppFilledIconButtonStyle.DarkOverlay -> Color.White.copy(alpha = 0.38f)
    AppFilledIconButtonStyle.HeroOverlay -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
}
