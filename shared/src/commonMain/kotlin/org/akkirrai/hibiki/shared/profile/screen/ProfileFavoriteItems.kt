package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory

fun LocalProfileData.buildFavoriteLibraryItems(
    statusLabel: String,
    dateLabel: (Long) -> String,
): List<RecentLibraryItem> = library
    .asSequence()
    .filter { LibraryCategory.Favorite in it.categories && it.anime.title.isNotBlank() }
    .sortedByDescending { it.addedAt ?: 0L }
    .take(6)
    .map { item ->
        RecentLibraryItem(
            title = item.anime.title,
            posterUrl = item.anime.posterUrl,
            ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatProfileRating),
            statusLabel = statusLabel,
            dateLabel = item.addedAt?.let(dateLabel).orEmpty(),
            color = LibraryCategory.Favorite.profileColor(),
        )
    }
    .toList()
