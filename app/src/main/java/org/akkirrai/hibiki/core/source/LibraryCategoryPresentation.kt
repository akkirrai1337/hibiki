package org.akkirrai.hibiki.core.source

import androidx.annotation.StringRes
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.shared.library.ui.localizationKey

@get:StringRes
val LibraryCategory.labelResId: Int
    get() = when (localizationKey()) {
        "library_category_watching" -> R.string.library_category_watching
        "library_category_planned" -> R.string.library_category_planned
        "library_category_completed" -> R.string.library_category_completed
        "library_category_dropped" -> R.string.library_category_dropped
        "library_category_on_hold" -> R.string.library_category_on_hold
        "library_category_favorite" -> R.string.library_category_favorite
        "library_category_saved" -> R.string.library_category_saved
        else -> error("Unknown library category localization key")
    }
