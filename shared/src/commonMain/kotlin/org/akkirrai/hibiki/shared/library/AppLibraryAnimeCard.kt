package org.akkirrai.hibiki.shared.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.component.AppVerticalAnimeListItem
import org.akkirrai.hibiki.shared.model.Anime

@Composable
fun AppLibraryAnimeCard(
    anime: Anime,
    metaText: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
    posterContent: @Composable BoxScope.() -> Unit,
    posterFooterContent: @Composable () -> Unit,
    extraMetaContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppVerticalAnimeListItem(
        anime = anime,
        metaText = "",
        onClick = onClick,
        modifier = modifier,
        trailingIcon = trailingIcon,
        posterContent = posterContent,
        posterFooterContent = posterFooterContent,
        metaContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (metaText.isNotBlank()) {
                    Text(metaText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                extraMetaContent()
            }
        },
    )
}
