package org.akkirrai.hibiki.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PlayerSettingsChoiceRow(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
    selectedIndicator: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = PlayerSettingsChoiceOuterHorizontalPadding)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PlayerSettingsChoiceCornerRadius),
        color = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PlayerSettingsChoiceContentHorizontalPadding,
                    vertical = PlayerSettingsChoiceContentVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(PlayerSettingsChoiceGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = if (selected) 1f else 0.86f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.54f), maxLines = 1) }
            if (selected) selectedIndicator?.invoke()
        }
    }
}

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
                tint = Color.White,
            )
        },
    )
}
