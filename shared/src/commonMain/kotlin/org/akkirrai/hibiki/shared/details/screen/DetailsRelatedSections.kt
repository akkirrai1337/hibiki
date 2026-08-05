package org.akkirrai.hibiki.shared.details.screen

import org.akkirrai.hibiki.shared.details.state.*

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.akkirrai.hibiki.shared.catalog.model.RelatedAnime

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
