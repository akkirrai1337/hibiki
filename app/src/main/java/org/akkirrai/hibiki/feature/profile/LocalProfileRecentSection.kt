package org.akkirrai.hibiki.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.PosterPlaceholder

@Composable
internal fun RecentLibraryCard(
    items: List<RecentLibraryItem>,
    showTitle: Boolean = true,
) {
    org.akkirrai.hibiki.shared.profile.ProfileRecentLibraryCard(
        title = if (showTitle) stringResource(R.string.yummy_account_recent_additions_title) else null,
        emptyText = stringResource(R.string.yummy_account_recent_library_empty),
        isEmpty = items.isEmpty(),
        content = { items.forEach { RecentLibraryRow(it) } },
    )
}

@Composable
private fun RecentLibraryRow(item: RecentLibraryItem) {
    org.akkirrai.hibiki.shared.profile.ProfileRecentLibraryRow(
        title = item.title,
        statusLabel = item.statusLabel,
        statusColor = item.color,
        ratingLabel = item.ratingLabel,
        dateLabel = item.dateLabel,
        poster = { RecentPoster(item) },
    )
}

@Composable
private fun RecentLibraryRowLegacy(
    item: RecentLibraryItem,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            RecentPoster(item = item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(item.color),
                    )
                    Text(
                        text = item.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.ratingLabel?.let { rating ->
                        Text(
                            text = "★ $rating",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccountWarmAccent,
                        )
                    }
                }
            }
            Text(
                text = item.dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun RecentPoster(
    item: RecentLibraryItem,
) {
    org.akkirrai.hibiki.shared.profile.ProfileRecentPosterFrame {
        val posterUrl = normalizePosterUrl(item.posterUrl)
        if (posterUrl == null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.color),
            )
        } else {
            SubcomposeAsyncImage(
                model = posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)),
                    )
                },
                error = {
                    PosterPlaceholder(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(item.color),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
) {
    org.akkirrai.hibiki.shared.profile.ProfileEmptyState(text)
}
