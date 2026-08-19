package org.akkirrai.hibiki.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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

@Composable
fun AppPlayerUnlockButton(
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(PlayerUnlockButtonCornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PlayerUnlockButtonCornerRadius),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PlayerUnlockButtonHorizontalPadding,
                vertical = PlayerUnlockButtonVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(PlayerUnlockButtonContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
