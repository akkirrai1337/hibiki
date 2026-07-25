package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.AnimeRating

data class DetailsHeroRatingsData(
    val rating: String?,
    val viewCount: String?,
)

fun resolveDetailsHeroRatings(ratings: List<AnimeRating>, viewCount: Long?): DetailsHeroRatingsData? {
    val rating = ratings.firstOrNull()?.value?.let(::formatRating)
    val formattedViewCount = viewCount?.takeIf { it > 0 }?.let(::formatCompactCount)
    return if (rating == null && formattedViewCount == null) null
    else DetailsHeroRatingsData(rating, formattedViewCount)
}
