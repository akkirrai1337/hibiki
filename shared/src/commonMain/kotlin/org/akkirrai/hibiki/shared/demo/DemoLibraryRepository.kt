package org.akkirrai.hibiki.shared.demo

import org.akkirrai.hibiki.shared.library.InMemoryLibraryRepository
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryRepository

/** In-memory library data used by the development prototype until host storage is wired. */
object DemoLibraryRepository : LibraryRepository {
    private val delegate = InMemoryLibraryRepository(
        initialEntries = DemoCatalog.items.mapIndexed { index, anime ->
            LibraryEntry(
                anime = anime,
                category = if (index == 0) LibraryCategory.Watching else LibraryCategory.Planned,
                addedAt = index.toLong(),
            )
        },
    )

    override suspend fun getEntries(): List<LibraryEntry> = delegate.getEntries()

    override fun getLibraryCategory(id: String): LibraryCategory? = delegate.getLibraryCategory(id)

    override fun saveToLibrary(anime: org.akkirrai.hibiki.shared.catalog.model.Anime, category: LibraryCategory) {
        delegate.saveToLibrary(anime, category)
    }

    override fun removeFromLibrary(id: String) {
        delegate.removeFromLibrary(id)
    }
}
