package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

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

@Composable
fun AppSettingsIconVerticalItem(
    icon: ImageVector,
    title: String,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    AppSettingsVerticalItem(
        headerContent = {
            AppSettingsItemHeader(
                iconContent = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(SettingsItemIconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                title = title,
            )
        },
        shape = shape,
        content = content,
    )
}
