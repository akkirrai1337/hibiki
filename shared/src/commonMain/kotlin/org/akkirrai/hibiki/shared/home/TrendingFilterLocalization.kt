package org.akkirrai.hibiki.shared.home

fun TrendingFilter.localizationKey(): String = when (this) {
    TrendingFilter.All -> "trending_filter_all"
    TrendingFilter.Movies -> "trending_filter_movies"
    TrendingFilter.Ona -> "trending_filter_ona"
}
