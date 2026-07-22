package org.akkirrai.hibiki.feature.home

import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.home.TrendingFilter

@get:androidx.annotation.StringRes
val TrendingFilter.titleResId: Int
    get() = when (this) {
        TrendingFilter.All -> R.string.trending_filter_all
        TrendingFilter.Movies -> R.string.trending_filter_movies
        TrendingFilter.Ona -> R.string.trending_filter_ona
    }
