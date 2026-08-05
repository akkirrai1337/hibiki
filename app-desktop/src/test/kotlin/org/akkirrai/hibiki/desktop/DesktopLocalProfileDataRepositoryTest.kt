package org.akkirrai.hibiki.desktop
import org.akkirrai.hibiki.desktop.data.*

import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.library.LibraryEntry
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.catalog.model.Anime

class DesktopLocalProfileDataRepositoryTest {
    private lateinit var preferences: Preferences
    private lateinit var repository: DesktopLocalProfileDataRepository

    @Before
    fun setUp() {
        preferences = Preferences.userRoot().node("hibiki-profile-tests/${UUID.randomUUID()}")
        repository = DesktopLocalProfileDataRepository(
            progressRepository = DesktopPlaybackProgressRepository(),
            libraryRepository = object : LibraryRepository {
                override suspend fun getEntries(): List<LibraryEntry> = listOf(
                    LibraryEntry(
                        anime = Anime("title", "Title", "", "12", "Ongoing"),
                        category = LibraryCategory.Planned,
                        addedAt = 10L,
                    ),
                )
            },
            preferences = preferences,
        )
    }

    @After
    fun tearDown() {
        preferences.removeNode()
    }

    @Test
    fun profileIdentityPersistsAcrossRepositoryInstances() = runBlocking {
        repository.updateProfileName("  Vadim  ")
        repository.updateProfileAvatar("file:///avatar.png")

        val restored = DesktopLocalProfileDataRepository(
            progressRepository = DesktopPlaybackProgressRepository(),
            libraryRepository = object : LibraryRepository {
                override suspend fun getEntries(): List<LibraryEntry> = emptyList()
            },
            preferences = preferences,
        ).load()

        assertEquals("Vadim", restored.profileName)
        assertEquals("file:///avatar.png", restored.profileAvatarUri)
    }
}
