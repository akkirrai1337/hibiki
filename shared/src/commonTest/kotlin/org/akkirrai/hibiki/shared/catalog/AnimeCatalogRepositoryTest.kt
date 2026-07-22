package org.akkirrai.hibiki.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AnimeCatalogRepositoryTest {
    @Test
    fun searchMatchesTitlesSubtitlesAndGenres() = runTest {
        val repository = PrototypeAnimeCatalogRepository

        assertEquals(6, repository.initialItems.size)
        assertEquals("The Apothecary Diaries", repository.search("apothecary").single().title)
        assertEquals("Frieren: Beyond Journey's End", repository.search("Fantasy").first().title)
    }
}
