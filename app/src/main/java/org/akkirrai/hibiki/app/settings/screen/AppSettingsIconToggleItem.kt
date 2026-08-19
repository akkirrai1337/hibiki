package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppSettingsIconToggleItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    iconContent: (@Composable (Modifier) -> Unit)? = null,
) {
    AppSettingsToggleItem(
        iconContent = {
            val iconModifier = Modifier.size(SettingsItemIconSize)
            if (iconContent != null) {
                iconContent(iconModifier)
            } else {
                Icon(icon, contentDescription = null, modifier = iconModifier, tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        title = title,
        checked = checked,
        shape = shape,
        onClick = onClick,
        onCheckedChange = onCheckedChange,
    )
}
