package org.akkirrai.hibiki.shared.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppPlayerSkipSegmentButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (primary) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.58f),
        contentColor = if (primary) Color.Black else Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = PlayerSkipSegmentButtonHorizontalPadding,
                vertical = PlayerSkipSegmentButtonVerticalPadding,
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
