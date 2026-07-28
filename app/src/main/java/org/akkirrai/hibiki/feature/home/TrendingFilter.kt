package org.akkirrai.hibiki.feature.home

import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.home.TrendingFilter
import org.akkirrai.hibiki.shared.home.localizationKey

@get:androidx.annotation.StringRes
val TrendingFilter.titleResId: Int
    get() = when (localizationKey()) {
        "trending_filter_all" -> R.string.trending_filter_all
        "trending_filter_movies" -> R.string.trending_filter_movies
        "trending_filter_ona" -> R.string.trending_filter_ona
        else -> error("Unknown trending filter localization key")
    }
