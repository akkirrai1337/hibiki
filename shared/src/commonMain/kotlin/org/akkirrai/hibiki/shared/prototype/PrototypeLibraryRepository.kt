package org.akkirrai.hibiki.shared.prototype

import org.akkirrai.hibiki.shared.library.InMemoryLibraryRepository
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryRepository

/** Read-only library data used by the development prototype until host storage is wired. */
object PrototypeLibraryRepository : LibraryRepository {
    private val delegate = InMemoryLibraryRepository(
        initialEntries = PrototypeCatalog.items.mapIndexed { index, anime ->
            LibraryEntry(
                anime = anime,
                category = if (index == 0) LibraryCategory.Watching else LibraryCategory.Planned,
                addedAt = index.toLong(),
            )
        },
    )

    override suspend fun getEntries(): List<LibraryEntry> = delegate.getEntries()
}
