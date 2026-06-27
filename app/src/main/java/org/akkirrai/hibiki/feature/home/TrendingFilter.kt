package org.akkirrai.hibiki.feature.home

import androidx.annotation.StringRes
import org.akkirrai.hibiki.R

enum class TrendingFilter(
    @param:StringRes val titleResId: Int,
    val yummyType: String?,
) {
    All(
        titleResId = R.string.trending_filter_all,
        yummyType = null,
    ),
    Movies(
        titleResId = R.string.trending_filter_movies,
        yummyType = "movie",
    ),
    Ona(
        titleResId = R.string.trending_filter_ona,
        yummyType = "ona",
    ),
}
