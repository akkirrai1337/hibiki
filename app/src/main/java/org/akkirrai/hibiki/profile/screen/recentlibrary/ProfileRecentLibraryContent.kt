package org.akkirrai.hibiki.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
