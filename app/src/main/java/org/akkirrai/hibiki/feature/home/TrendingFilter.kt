package org.akkirrai.hibiki.feature.home

import androidx.annotation.StringRes
import org.akkirrai.hibiki.R

enum class TrendingFilter(
    @param:StringRes val titleResId: Int,
    val typeAlias: String?,
) {
    All(
        titleResId = R.string.trending_filter_all,
        typeAlias = null,
    ),
    Movies(
        titleResId = R.string.trending_filter_movies,
        typeAlias = "movie",
    ),
    Ona(
        titleResId = R.string.trending_filter_ona,
        typeAlias = "ona",
    ),
}
