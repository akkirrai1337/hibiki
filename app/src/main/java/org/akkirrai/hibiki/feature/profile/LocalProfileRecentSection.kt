package org.akkirrai.hibiki.feature.profile

import org.akkirrai.hibiki.shared.profile.normalizePosterUrl
import org.akkirrai.hibiki.shared.profile.ProfileRecentPosterMarker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akkirrai.hibiki.R
import androidx.compose.ui.layout.ContentScale
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.AppPosterLoadingPlaceholder
import org.akkirrai.hibiki.shared.design.component.AppPosterPlaceholder

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
            AppPosterImage(
                primaryUrl = posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = {
                    AppPosterLoadingPlaceholder()
                },
                errorContent = {
                    AppPosterPlaceholder(modifier = Modifier.fillMaxSize()) {
                        ProfileRecentPosterMarker(color = item.color)
                    }
                },
            )
        }
    }
}
