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
