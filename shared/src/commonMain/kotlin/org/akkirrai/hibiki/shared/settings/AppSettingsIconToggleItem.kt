package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppSettingsIconToggleItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppSettingsToggleItem(
        iconContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
        },
        title = title,
        checked = checked,
        shape = shape,
        onClick = onClick,
        onCheckedChange = onCheckedChange,
    )
}
