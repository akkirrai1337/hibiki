package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.prototype.PrototypeLibraryRepository
import platform.Foundation.NSUserDefaults

internal class IosLibraryRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val delegate: LibraryRepository = PrototypeLibraryRepository,
) : LibraryRepository {
    override suspend fun getEntries(): List<LibraryEntry> = delegate.getEntries().map { entry ->
        copyWithStoredCategory(entry)
    }

    override fun getLibraryCategory(id: String): LibraryCategory? = storedCategory(id)
        ?: delegate.getLibraryCategory(id)

    override fun saveToLibrary(anime: Anime, category: LibraryCategory) {
        delegate.saveToLibrary(anime, category)
        defaults.setObject(category.storageValue, forKey = categoryKey(anime.id))
    }

    override fun removeFromLibrary(id: String) {
        delegate.removeFromLibrary(id)
        defaults.removeObjectForKey(categoryKey(id))
    }

    private fun copyWithStoredCategory(entry: LibraryEntry): LibraryEntry {
        return storedCategory(entry.anime.id)?.let { category ->
            entry.copy(category = category)
        } ?: entry
    }

    private fun storedCategory(id: String): LibraryCategory? = defaults
        .stringForKey(categoryKey(id))
        ?.let(LibraryCategory::fromStorageValue)

    private fun categoryKey(id: String): String = "$CATEGORY_PREFIX$id"

    private companion object {
        const val CATEGORY_PREFIX = "hibiki.library.category."
    }
}
