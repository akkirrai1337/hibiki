package org.akkirrai.hibiki.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.library.LibraryCategory

class LibraryCategoryResolverTest {
    @Test
    fun prefersOrderedNonSavedCategory() {
        assertEquals(LibraryCategory.Watching, setOf(LibraryCategory.Saved, LibraryCategory.Watching).primaryLibraryCategory())
        assertEquals(LibraryCategory.Saved, emptySet<LibraryCategory>().primaryLibraryCategory())
    }
}
