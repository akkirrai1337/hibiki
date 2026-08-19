package org.akkirrai.hibiki.home.ui

import org.akkirrai.hibiki.home.model.TrendingFilter

fun TrendingFilter.localizationKey(): String = when (this) {
    TrendingFilter.All -> "trending_filter_all"
    TrendingFilter.Movies -> "trending_filter_movies"
    TrendingFilter.Ona -> "trending_filter_ona"
}
