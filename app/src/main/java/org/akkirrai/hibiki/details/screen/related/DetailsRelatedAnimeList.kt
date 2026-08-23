package org.akkirrai.hibiki.details.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.catalog.model.RelatedAnime
import org.akkirrai.hibiki.details.model.*
import org.akkirrai.hibiki.details.state.*

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

fun buildDetailsRelatedAnimeItems(
    items: List<RelatedAnime>,
    announcementLabel: String,
): List<DetailsRelatedAnimeItem> = items
    .distinctBy(RelatedAnime::id)
    .map { related ->
        DetailsRelatedAnimeItem(
            id = related.id,
            title = related.title,
            metadata = formatRelatedAnimeMetadata(
                year = related.year,
                type = related.type,
                status = related.status,
                announcementLabel = announcementLabel,
            ),
        )
    }

fun LazyListScope.appDetailsRelatedSections(
    sections: List<DetailsSection>,
    relatedTitle: String,
    similarTitle: String,
    announcementLabel: String,
    horizontalPadding: Dp,
    onItemClick: (RelatedAnime) -> Unit,
    poster: @Composable (RelatedAnime) -> Unit,
) {
    items(
        items = sections,
        key = DetailsSection::key,
    ) { section ->
        when (section) {
            is RelatedSection -> AppDetailsRelatedAnimeList(
                items = section.items,
                title = relatedTitle,
                announcementLabel = announcementLabel,
                horizontalPadding = horizontalPadding,
                onItemClick = onItemClick,
                poster = poster,
            )
            is SimilarSection -> AppDetailsRelatedAnimeList(
                items = section.items,
                title = similarTitle,
                announcementLabel = announcementLabel,
                horizontalPadding = horizontalPadding,
                onItemClick = onItemClick,
                poster = poster,
            )
        }
    }
}
