package org.akkirrai.hibiki.feature.profile

import org.akkirrai.hibiki.shared.profile.normalizePosterUrl
import org.akkirrai.hibiki.shared.profile.ProfileRecentPosterMarker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
        content = {
            items.forEach { item ->
                org.akkirrai.hibiki.shared.profile.ProfileRecentLibraryRow(
                    title = item.title,
                    statusLabel = item.statusLabel,
                    statusColor = item.color,
                    ratingLabel = item.ratingLabel,
                    dateLabel = item.dateLabel,
                    poster = { RecentPoster(item) },
                )
            }
        },
    )
}

@Composable
private fun RecentPoster(
    item: RecentLibraryItem,
) {
    org.akkirrai.hibiki.shared.profile.ProfileRecentPosterFrame {
        val posterUrl = normalizePosterUrl(item.posterUrl)
        if (posterUrl == null) {
            ProfileRecentPosterMarker(color = item.color)
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
                        ProfileRecentPosterMarker(color = item.color)
                    }
                },
            )
        }
    }
}
