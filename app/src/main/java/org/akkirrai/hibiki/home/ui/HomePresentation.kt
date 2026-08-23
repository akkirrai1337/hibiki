package org.akkirrai.hibiki.home.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.design.UiDimens

enum class TrendingFilter(
    val typeAlias: String?,
) {
    All(typeAlias = null),
    Movies(typeAlias = "movie"),
    Ona(typeAlias = "ona"),
}

val HomeContentTopPadding = UiDimens.SearchBarTopPadding +
    UiDimens.SearchBarHeight +
    UiDimens.ScreenPadding

val HomeTopSearchScrimHeight = HomeContentTopPadding + 18.dp

val HomeHistoryIcon: ImageVector = Icons.Outlined.History
val HomeRecentlyAddedIcon: ImageVector = Icons.Outlined.VideoLibrary

fun resolveDisplayTypeLabel(rawType: String): String = when (rawType.uppercase()) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MOVIE" -> "Movie"
    "SHORT_MOVIE", "SHORT-MOVIE" -> "Short Movie"
    "SPECIAL" -> "Special"
    else -> rawType.replace("_", "-").replace("-", " ")
        .replaceFirstChar { it.uppercase() }
}

fun TrendingFilter.localizationKey(): String = when (this) {
    TrendingFilter.All -> "trending_filter_all"
    TrendingFilter.Movies -> "trending_filter_movies"
    TrendingFilter.Ona -> "trending_filter_ona"
}
