package org.akkirrai.hibiki.shared.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppSettingsIconSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    AppSettingsSwitchItem(
        iconContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(SettingsItemIconSize), tint = MaterialTheme.colorScheme.onSurface)
        },
        title = title,
        checked = checked,
        shape = shape,
        onCheckedChange = onCheckedChange,
    )
}
