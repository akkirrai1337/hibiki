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

private val PROFILE_LIBRARY_CATEGORIES = listOf(
    LibraryCategory.Watching,
    LibraryCategory.Planned,
    LibraryCategory.Completed,
    LibraryCategory.Dropped,
    LibraryCategory.OnHold,
    LibraryCategory.Favorite,
)

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
