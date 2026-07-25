package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory

fun Set<LibraryCategory>.primaryLibraryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull { it != LibraryCategory.Saved && it in this }
        ?: LibraryCategory.Saved
