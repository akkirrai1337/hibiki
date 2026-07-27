package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlayerSettingsEntryRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = PlayerSettingsEntryOuterHorizontalPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlayerSettingsEntryCornerRadius))
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PlayerSettingsEntryContentHorizontalPadding,
                    vertical = PlayerSettingsEntryContentVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(PlayerSettingsEntryGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Clip)
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailingContent()
        }
    }
}
