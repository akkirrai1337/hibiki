package org.akkirrai.hibiki.profile

import org.akkirrai.hibiki.library.LibraryCategory

fun Set<LibraryCategory>.primaryLibraryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull { it != LibraryCategory.Saved && it in this }
        ?: LibraryCategory.Saved
