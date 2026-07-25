package org.akkirrai.hibiki.shared.library

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.model.Anime

class LibraryPresenterTest {
    @Test
    fun keepsCurrentCategoryWhenEntriesChange() {
        val presenter = LibraryPresenter()
        val entry = LibraryEntry(
            anime = Anime(id = "1", title = "Demo", subtitle = "", episodesLabel = "", status = ""),
            category = LibraryCategory.Watching,
        )

        presenter.updateEntries(listOf(entry))

        assertEquals(LibraryCategory.Watching, presenter.state.value.selectedCategory)
        assertEquals(listOf(entry), presenter.state.value.visibleEntries)
    }

    @Test
    fun fallsBackToFirstAvailableCategory() {
        val presenter = LibraryPresenter(LibraryUiState(selectedCategory = LibraryCategory.Watching))
        val entry = LibraryEntry(
            anime = Anime(id = "1", title = "Saved title", subtitle = "", episodesLabel = "", status = ""),
            category = LibraryCategory.Saved,
        )

        presenter.updateEntries(listOf(entry))

        assertEquals(LibraryCategory.Saved, presenter.state.value.selectedCategory)
    }
}
