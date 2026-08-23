package org.akkirrai.hibiki.home.model

enum class TrendingFilter(
    val typeAlias: String?,
) {
    All(typeAlias = null),
    Movies(typeAlias = "movie"),
    Ona(typeAlias = "ona"),
}
