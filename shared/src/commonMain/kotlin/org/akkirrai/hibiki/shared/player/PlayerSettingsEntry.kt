package org.akkirrai.hibiki.shared.player

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PlayerSettingsEntry(
    val id: String,
    val title: String,
    val value: String,
    val onClick: () -> Unit,
)

@Composable
fun AppPlayerSettingsEntry(
    title: String,
    value: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
) {
    PlayerSettingsEntryRow(
        title = title,
        value = value,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.52f),
            )
        },
    )
}
