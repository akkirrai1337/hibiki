package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailsNextEpisodeChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val chipColor = Color(0xFF80DF87)
    Row(
        modifier = modifier.clip(CircleShape).background(chipColor.copy(alpha = 0.2f)).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = chipColor)
        Text(text, color = chipColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
