package org.akkirrai.hibiki.shared.home.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.content.AppPosterAnimeCard
import org.akkirrai.hibiki.shared.design.component.content.SectionHeader
import org.akkirrai.hibiki.shared.catalog.model.Anime

fun LazyListScope.appHomeAnimeSection(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    icon: ImageVector,
    metaText: (Anime) -> String,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    item {
        AppHomeAnimeSection(
            title = title,
            items = items,
            onAnimeClick = onAnimeClick,
            icon = icon,
            metaText = metaText,
            posterContent = posterContent,
            onHeaderClick = onHeaderClick,
        )
    }
}

@Composable
private fun AppHomeAnimeSection(
    title: String,
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    icon: ImageVector,
    metaText: (Anime) -> String,
    posterContent: @Composable BoxScope.(Anime) -> Unit,
    onHeaderClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = UiDimens.SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(UiDimens.SmallSpacing),
    ) {
        SectionHeader(
            title = title,
            actionLabel = onHeaderClick?.let { "\u203A" },
            icon = icon,
            modifier = Modifier
                .padding(horizontal = UiDimens.ScreenPadding)
                .clickable(enabled = onHeaderClick != null) { onHeaderClick?.invoke() },
            titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth - 32.dp - UiDimens.ItemSpacing) / 2
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(UiDimens.ItemSpacing),
            ) {
                items(items, key = Anime::id) { anime ->
                    AppPosterAnimeCard(
                        anime = anime,
                        metaText = metaText(anime),
                        onClick = { onAnimeClick(anime) },
                        modifier = Modifier.width(cardWidth),
                        posterContent = { posterContent(anime) },
                    )
                }
            }
        }
    }
}
