package org.akkirrai.hibiki.profile

import org.akkirrai.hibiki.library.LibraryCategory

fun Set<LibraryCategory>.primaryLibraryCategory(): LibraryCategory =
    LibraryCategory.entries.firstOrNull {
        it != LibraryCategory.Saved && it != LibraryCategory.Recent && it in this
    } ?: LibraryCategory.Saved
