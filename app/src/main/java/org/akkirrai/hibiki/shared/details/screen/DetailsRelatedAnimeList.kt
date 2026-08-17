package org.akkirrai.hibiki.shared.details.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.model.RelatedAnime

@Composable
fun AppDetailsRelatedAnimeList(
    items: List<RelatedAnime>,
    title: String,
    announcementLabel: String,
    horizontalPadding: Dp,
    onItemClick: (RelatedAnime) -> Unit,
    poster: @Composable (RelatedAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = remember(items) { items.distinctBy(RelatedAnime::id) }
    val relatedItems = remember(displayItems, announcementLabel) {
        buildDetailsRelatedAnimeItems(
            items = displayItems,
            announcementLabel = announcementLabel,
        )
    }
    val relatedById = remember(displayItems) { displayItems.associateBy(RelatedAnime::id) }

    DetailsRelatedAnimeSection(
        items = relatedItems,
        title = title,
        horizontalPadding = horizontalPadding,
        onItemClick = { item -> relatedById[item.id]?.let(onItemClick) },
        poster = { item ->
            val related = relatedById[item.id]
            if (related != null) {
                poster(related)
            }
        },
        modifier = modifier,
    )
}
