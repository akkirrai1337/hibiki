package org.akkirrai.hibiki.library.ui

import org.akkirrai.hibiki.library.LibraryCategory

fun LibraryCategory.localizationKey(): String = when (this) {
    LibraryCategory.Watching -> "library_category_watching"
    LibraryCategory.Planned -> "library_category_planned"
    LibraryCategory.Completed -> "library_category_completed"
    LibraryCategory.Dropped -> "library_category_dropped"
    LibraryCategory.OnHold -> "library_category_on_hold"
    LibraryCategory.Favorite -> "library_category_favorite"
    LibraryCategory.Saved -> "library_category_saved"
}
