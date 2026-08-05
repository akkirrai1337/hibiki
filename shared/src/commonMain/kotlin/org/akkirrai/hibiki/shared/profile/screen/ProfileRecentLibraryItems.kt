package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory

fun LocalProfileData.buildRecentLibraryItems(
    statusLabel: (LibraryCategory) -> String,
    dateLabel: (Long) -> String,
): List<RecentLibraryItem> = library
    .asSequence()
    .filter { it.addedAt != null && it.anime.title.isNotBlank() }
    .sortedByDescending { it.addedAt }
    .map { item ->
        val category = item.categories.primaryLibraryCategory()
        RecentLibraryItem(
            title = item.anime.title,
            posterUrl = item.anime.posterUrl,
            ratingLabel = item.anime.ratings.firstOrNull()?.value?.let(::formatProfileRating),
            statusLabel = statusLabel(category),
            dateLabel = dateLabel(requireNotNull(item.addedAt)),
            color = category.profileColor(),
        )
    }
    .distinctBy(RecentLibraryItem::title)
    .take(5)
    .toList()
