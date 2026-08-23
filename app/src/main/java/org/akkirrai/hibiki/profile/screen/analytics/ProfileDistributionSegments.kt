package org.akkirrai.hibiki.profile

import androidx.compose.ui.graphics.Color
import org.akkirrai.hibiki.library.LibraryCategory

fun LocalProfileData.trackedProfileLibraryItems(): List<LocalLibraryItem> = library.filter { item ->
    item.categories.any(PROFILE_LIBRARY_CATEGORIES::contains)
}

fun LocalProfileData.buildLibraryStatusSegments(
    label: (LibraryCategory) -> String,
): List<DistributionSegment> {
    val trackedLibrary = trackedProfileLibraryItems()
    return PROFILE_LIBRARY_CATEGORIES.map { category ->
        DistributionSegment(
            label = label(category),
            count = trackedLibrary.count { category in it.categories },
            color = category.profileColor(),
        )
    }
}

fun LocalProfileData.profileGenreTrackedTitlesCount(): Int =
    trackedProfileLibraryItems()
        .map { it.anime }
        .distinctBy { it.id }
        .count { it.genres.isNotEmpty() }

fun LocalProfileData.buildGenreSegments(): List<DistributionSegment> =
    trackedProfileLibraryItems()
        .map { it.anime }
        .distinctBy { it.id }
        .flatMap { it.genres }
        .groupingBy { it.trim() }
        .eachCount()
        .filterKeys(String::isNotBlank)
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(PROFILE_GENRE_SEGMENT_LIMIT)
        .mapIndexed { index, entry ->
            DistributionSegment(
                label = entry.key,
                count = entry.value,
                color = PROFILE_GENRE_PALETTE[index % PROFILE_GENRE_PALETTE.size],
            )
        }

fun LibraryCategory.profileColor(): Color = when (this) {
    LibraryCategory.Watching -> Color(0xFF3DDC84)
    LibraryCategory.Planned -> Color(0xFF5DA9FF)
    LibraryCategory.Completed -> Color(0xFFFFB84D)
    LibraryCategory.Dropped -> Color(0xFFFF6B6B)
    LibraryCategory.OnHold -> Color(0xFFC593FF)
    LibraryCategory.Favorite -> Color(0xFFFFB86A)
    LibraryCategory.Saved -> Color(0xFF9EA4B2)
    LibraryCategory.Recent -> Color(0xFF9EA4B2)
}

private val PROFILE_LIBRARY_CATEGORIES = listOf(
    LibraryCategory.Watching,
    LibraryCategory.Planned,
    LibraryCategory.Completed,
    LibraryCategory.Dropped,
    LibraryCategory.OnHold,
    LibraryCategory.Favorite,
)

private const val PROFILE_GENRE_SEGMENT_LIMIT = 6
private val PROFILE_GENRE_PALETTE = listOf(
    Color(0xFF48D67B),
    Color(0xFFF7BC16),
    Color(0xFFA56CE3),
    Color(0xFFFF646B),
    Color(0xFFC24ED3),
    Color(0xFF737373),
)
