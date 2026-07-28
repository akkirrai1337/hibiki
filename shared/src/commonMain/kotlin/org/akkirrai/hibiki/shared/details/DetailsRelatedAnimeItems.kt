package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.RelatedAnime

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
