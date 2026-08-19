package org.akkirrai.hibiki.library

/** Platform-neutral storage boundary for the entries displayed by the shared library screen. */
interface LibraryRepository {
    suspend fun getEntries(): List<LibraryEntry>

    fun getLibraryCategory(id: String): LibraryCategory? = null

    /** Whether a title currently carries a specific category, independent of its primary one. */
    fun hasCategory(id: String, category: LibraryCategory): Boolean = false

    fun saveToLibrary(anime: org.akkirrai.hibiki.catalog.model.Anime, category: LibraryCategory) = Unit

    /** Removes only the Saved/download category while preserving other categories. */
    fun removeSavedFromLibrary(id: String) = Unit

    fun removeFromLibrary(id: String) = Unit
}

/** Small deterministic store for hosts that have not connected persistent storage yet. */
class InMemoryLibraryRepository(
    private val initialEntries: List<LibraryEntry> = emptyList(),
) : LibraryRepository {
    private val entries = initialEntries.toMutableList()

    override suspend fun getEntries(): List<LibraryEntry> = entries.toList()

    override fun getLibraryCategory(id: String): LibraryCategory? = entries
        .firstOrNull { it.anime.id == id && it.category !in SUPPLEMENTAL_CATEGORIES }
        ?.category
        ?: entries.firstOrNull { it.anime.id == id }?.category

    override fun hasCategory(id: String, category: LibraryCategory): Boolean =
        entries.any { it.anime.id == id && it.category == category }

    override fun saveToLibrary(anime: org.akkirrai.hibiki.catalog.model.Anime, category: LibraryCategory) {
        val existing = entries.filter { it.anime.id == anime.id }
        entries.removeAll { it.anime.id == anime.id && it.category !in SUPPLEMENTAL_CATEGORIES }
        if (existing.none { it.category == category }) {
            entries += LibraryEntry(anime = anime, category = category, addedAt = existing.firstOrNull()?.addedAt)
        }
    }

    override fun removeFromLibrary(id: String) {
        entries.removeAll { it.anime.id == id && it.category !in setOf(LibraryCategory.Saved) }
    }

    private companion object {
        val SUPPLEMENTAL_CATEGORIES = setOf(LibraryCategory.Favorite, LibraryCategory.Saved, LibraryCategory.Recent)
    }
}
