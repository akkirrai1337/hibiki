package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.akkirrai.hibiki.catalog.model.AnimeRating
import org.akkirrai.hibiki.details.model.formatCompactCount
import org.akkirrai.hibiki.details.model.formatRating

data class DetailsHeroRatingsData(
    val rating: String?,
    val viewCount: String?,
)

fun resolveDetailsHeroRatings(ratings: List<AnimeRating>, viewCount: Long?): DetailsHeroRatingsData? {
    val rating = ratings.firstOrNull()?.value?.let(::formatRating)
    val formattedViewCount = viewCount?.takeIf { it > 0 }?.let(::formatCompactCount)
    return if (rating == null && formattedViewCount == null) null
    else DetailsHeroRatingsData(rating, formattedViewCount)
}

@Composable
fun DetailsHeroRatingsLine(
    rating: String?,
    viewCount: String?,
    modifier: Modifier = Modifier,
) {
    if (rating == null && viewCount == null) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(DetailsHeroRatingsContentGap), verticalAlignment = Alignment.CenterVertically) {
        rating?.let {
            Icon(Icons.Filled.Star, null, Modifier.size(DetailsHeroRatingsIconSize), tint = Color(0xFFFFC107))
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        viewCount?.let {
            if (rating != null) Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Icon(Icons.Outlined.Visibility, null, Modifier.size(DetailsHeroRatingsIconSize), tint = MaterialTheme.colorScheme.primary)
            Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
