package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.akkirrai.hibiki.catalog.model.Anime

fun LazyListScope.appHomeContinueWatchingSection(
    anime: Anime?,
    sectionTitle: String,
    emptyTitle: String,
    emptyMessage: String,
    openHint: String,
    meta: (Anime) -> String,
    onClick: (Anime) -> Unit,
    imageContent: @Composable BoxScope.(Anime) -> Unit,
    trailingContent: @Composable RowScope.(Anime) -> Unit,
    sectionIcon: ImageVector,
) {
    anime?.let { currentAnime ->
        item {
            AppHomeContinueWatchingContent(
                anime = currentAnime,
                sectionTitle = sectionTitle,
                emptyTitle = emptyTitle,
                emptyMessage = emptyMessage,
                openHint = openHint,
                meta = meta(currentAnime),
                sectionIcon = sectionIcon,
                onClick = { onClick(currentAnime) },
                imageContent = { imageContent(currentAnime) },
                trailingContent = { trailingContent(currentAnime) },
            )
        }
    }
}
