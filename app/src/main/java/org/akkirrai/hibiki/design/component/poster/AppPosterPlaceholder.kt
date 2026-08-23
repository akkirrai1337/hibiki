package org.akkirrai.hibiki.design.component.poster

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.design.UiDimens
import org.akkirrai.hibiki.design.component.surface.AppTonalSurface

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

@Composable
fun AppPosterPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun AppImagePlaceholder(
    icon: ImageVector = Icons.Outlined.Image,
    modifier: Modifier = Modifier,
) {
    AppTonalSurface(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiDimens.PlaceholderIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AppPosterLoadingPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)),
    )
}
