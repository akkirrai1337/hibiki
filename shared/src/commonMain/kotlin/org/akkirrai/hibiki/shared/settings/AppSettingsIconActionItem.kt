package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme

@Composable
fun AppSettingsIconActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    shape: Shape,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    AppSettingsActionItem(
        iconContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(SettingsItemIconSize), tint = MaterialTheme.colorScheme.onSurface)
        },
        title = title,
        subtitle = subtitle,
        shape = shape,
        trailing = trailing ?: if (showChevron) {
            {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}
