package org.akkirrai.hibiki.shared.design.component.poster

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.surface.AppTonalSurface

@Composable
fun AppImagePlaceholder(
    icon: ImageVector = Icons.Outlined.Image,
    modifier: Modifier = Modifier,
) {
    AppTonalSurface(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiDimens.PlaceholderIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
