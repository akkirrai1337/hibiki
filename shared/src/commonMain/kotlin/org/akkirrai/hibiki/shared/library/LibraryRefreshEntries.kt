package org.akkirrai.hibiki.shared.library

fun selectLibraryEntriesForDetailsRefresh(
    entries: List<LibraryEntry>,
    category: LibraryCategory,
    limit: Int,
): List<LibraryEntry> = entries
    .filter { it.category == category }
    .distinctBy { it.anime.id }
    .take(limit)
