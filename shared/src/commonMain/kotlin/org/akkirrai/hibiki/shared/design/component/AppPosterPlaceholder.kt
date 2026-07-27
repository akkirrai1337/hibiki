package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.UiDimens

@Composable
fun AppPosterPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    AppTonalSurface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiDimens.PlaceholderIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
