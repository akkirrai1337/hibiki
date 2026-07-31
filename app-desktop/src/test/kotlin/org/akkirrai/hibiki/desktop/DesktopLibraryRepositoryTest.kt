package org.akkirrai.hibiki.desktop

import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DesktopLibraryRepositoryTest {
    private lateinit var preferences: Preferences
    private lateinit var repository: DesktopLibraryRepository

    @Before
    fun setUp() {
        preferences = Preferences.userRoot().node("hibiki-tests/${UUID.randomUUID()}")
        repository = DesktopLibraryRepository(preferences)
    }

    @After
    fun tearDown() {
        preferences.removeNode()
    }

    @Test
    fun persistsEntriesAndPreservesSupplementalCategories() = runBlocking {
        val anime = Anime(id = "title-1", title = "Title 1", subtitle = "", episodesLabel = "12", status = "Ongoing")

        repository.saveToLibrary(anime, LibraryCategory.Planned)
        repository.saveToLibrary(anime, LibraryCategory.Favorite)

        val restored = DesktopLibraryRepository(preferences)
        assertEquals(
            listOf(LibraryCategory.Planned, LibraryCategory.Favorite),
            restored.getEntries().map { it.category },
        )
        assertNotNull(restored.getEntries().first().addedAt)
    }

    @Test
    fun replacingPrimaryCategoryKeepsSavedAndFavorite() = runBlocking {
        val anime = Anime(id = "title-2", title = "Title 2", subtitle = "", episodesLabel = "12", status = "Ongoing")

        repository.saveToLibrary(anime, LibraryCategory.Planned)
        repository.saveToLibrary(anime, LibraryCategory.Favorite)
        repository.saveToLibrary(anime, LibraryCategory.Saved)
        repository.saveToLibrary(anime, LibraryCategory.Completed)

        assertEquals(
            listOf(LibraryCategory.Completed, LibraryCategory.Favorite, LibraryCategory.Saved),
            repository.getEntries().map { it.category },
        )
        repository.removeFromLibrary(anime.id)
        assertEquals(
            listOf(LibraryCategory.Saved),
            repository.getEntries().map { it.category },
        )
        assertEquals(LibraryCategory.Saved, repository.getLibraryCategory(anime.id))
    }
}
