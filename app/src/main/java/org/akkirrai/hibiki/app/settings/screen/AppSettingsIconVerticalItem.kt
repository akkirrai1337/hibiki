package org.akkirrai.hibiki.app.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

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
