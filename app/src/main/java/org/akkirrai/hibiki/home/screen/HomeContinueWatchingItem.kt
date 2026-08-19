package org.akkirrai.hibiki.home.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.component.content.AppContinueWatchingCard
import org.akkirrai.hibiki.catalog.model.Anime

@Composable
fun AppHomeContinueWatchingContent(
    anime: Anime,
    sectionTitle: String,
    emptyTitle: String,
    emptyMessage: String,
    openHint: String,
    meta: String,
    sectionIcon: ImageVector,
    onClick: () -> Unit,
    imageContent: @Composable BoxScope.(Anime) -> Unit,
    trailingContent: @Composable RowScope.(Anime) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        AppContinueWatchingCard(
            anime = anime,
            sectionTitle = sectionTitle,
            emptyTitle = emptyTitle,
            emptyMessage = emptyMessage,
            openHint = openHint,
            meta = meta,
            sectionIcon = sectionIcon,
            onClick = onClick,
            imageContent = { imageContent(anime) },
            trailingContent = { trailingContent(anime) },
        )
    }
}
