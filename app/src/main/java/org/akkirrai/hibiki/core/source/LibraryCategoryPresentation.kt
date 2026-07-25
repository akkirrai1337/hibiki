package org.akkirrai.hibiki.core.source

import androidx.annotation.StringRes
import org.akkirrai.hibiki.R

@get:StringRes
val LibraryCategory.labelResId: Int
    get() = when (this) {
        LibraryCategory.Watching -> R.string.library_category_watching
        LibraryCategory.Planned -> R.string.library_category_planned
        LibraryCategory.Completed -> R.string.library_category_completed
        LibraryCategory.Dropped -> R.string.library_category_dropped
        LibraryCategory.OnHold -> R.string.library_category_on_hold
        LibraryCategory.Favorite -> R.string.library_category_favorite
        LibraryCategory.Saved -> R.string.library_category_saved
    }
