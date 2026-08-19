package org.akkirrai.hibiki.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun AppPlayerSettingsChoice(
    label: String,
    description: String?,
    selected: Boolean,
    selectedIcon: ImageVector = Icons.Outlined.Check,
    onClick: () -> Unit,
) {
    PlayerSettingsChoiceRow(
        label = label,
        description = description,
        selected = selected,
        onClick = onClick,
        selectedIndicator = {
            Icon(
                imageVector = selectedIcon,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
            )
        },
    )
}
