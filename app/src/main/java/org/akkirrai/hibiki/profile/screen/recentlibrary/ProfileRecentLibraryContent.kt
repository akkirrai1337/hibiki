package org.akkirrai.hibiki.profile

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.akkirrai.hibiki.design.component.poster.AppPosterImage
import org.akkirrai.hibiki.design.component.poster.AppPosterLoadingPlaceholder
import org.akkirrai.hibiki.design.component.poster.AppPosterPlaceholder

@Composable
fun AppProfileRecentLibraryContent(
    items: List<RecentLibraryItem>,
    title: String?,
    emptyText: String,
) {
    ProfileRecentLibraryCard(
        title = title,
        emptyText = emptyText,
        isEmpty = items.isEmpty(),
    ) {
        items.forEach { item ->
            ProfileRecentLibraryRow(
                title = item.title,
                statusLabel = item.statusLabel,
                statusColor = item.color,
                ratingLabel = item.ratingLabel,
                dateLabel = item.dateLabel,
                poster = {
                    ProfileRecentPosterFrame {
                        val posterUrl = normalizePosterUrl(item.posterUrl)
                        if (posterUrl == null) {
                            ProfileRecentPosterMarker(color = item.color)
                        } else {
                            AppPosterImage(
                                primaryUrl = posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = { AppPosterLoadingPlaceholder() },
                                errorContent = {
                                    AppPosterPlaceholder(modifier = Modifier.fillMaxSize()) {
                                        ProfileRecentPosterMarker(color = item.color)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun ProfileRecentLibraryCard(
    title: String?,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ProfileRecentLibraryCardVerticalSpacing)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
        if (isEmpty) {
            Box(Modifier.fillMaxWidth().height(ProfileRecentLibraryEmptyHeight), contentAlignment = Alignment.CenterStart) {
                ProfileEmptyState(emptyText)
            }
        } else {
            content()
        }
    }
}

@Composable
fun ProfileRecentLibraryRow(
    title: String,
    statusLabel: String,
    statusColor: Color,
    ratingLabel: String?,
    dateLabel: String,
    poster: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = ProfileSmallPadding), horizontalArrangement = Arrangement.spacedBy(ProfileRecentLibraryRowPosterTextGap), verticalAlignment = Alignment.CenterVertically) {
        poster()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ProfileRecentLibraryRowTextSpacing)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(ProfileSmallPadding), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(ProfileRecentLibraryStatusMarkerSize).clip(CircleShape).background(statusColor))
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ratingLabel?.let { Text("★ $it", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFB86A)) }
            }
        }
        Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfileRecentPosterFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(ProfileRecentPosterFrameWidth)
            .height(ProfileRecentPosterFrameHeight)
            .clip(RoundedCornerShape(ProfileRecentPosterFrameCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun ProfileRecentPosterMarker(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ProfileRecentPosterMarkerSize)
            .clip(CircleShape)
            .background(color),
    )
}
