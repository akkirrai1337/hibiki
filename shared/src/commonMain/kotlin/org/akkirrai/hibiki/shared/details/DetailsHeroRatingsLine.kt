package org.akkirrai.hibiki.shared.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DetailsHeroRatingsLine(
    rating: String?,
    viewCount: String?,
    ratingIcon: ImageVector,
    viewCountIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    if (rating == null && viewCount == null) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        rating?.let {
            Icon(ratingIcon, null, Modifier.size(14.dp), tint = Color(0xFFFFC107))
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        viewCount?.let {
            if (rating != null) Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Icon(viewCountIcon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
