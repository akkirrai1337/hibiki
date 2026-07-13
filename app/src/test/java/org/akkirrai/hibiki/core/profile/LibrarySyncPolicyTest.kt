package org.akkirrai.hibiki.core.profile

import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncPolicyTest {
    @Test
    fun `remote-only title is imported with status and favorite`() {
        val actions = planLibraryMerge(
            localItems = emptyList(),
            remoteItems = listOf(remote(list = YummyUserList.Watching, favorite = true)),
        )

        val import = actions.single() as LibraryMergeAction.ImportRemote
        assertEquals(setOf(LibraryCategory.Watching, LibraryCategory.Favorite), import.categories)
    }

    @Test
    fun `matching statuses clear an obsolete conflict`() {
        val actions = planLibraryMerge(
            localItems = listOf(local(setOf(LibraryCategory.Completed))),
            remoteItems = listOf(remote(list = YummyUserList.Completed)),
        )

        assertEquals(listOf(LibraryMergeAction.ClearConflict("42")), actions)
    }

    @Test
    fun `different primary statuses create one conflict`() {
        val actions = planLibraryMerge(
            localItems = listOf(local(setOf(LibraryCategory.Watching))),
            remoteItems = listOf(remote(list = YummyUserList.Dropped)),
        )

        val conflict = actions.single() as LibraryMergeAction.RecordConflict
        assertEquals(LibraryCategory.Watching, conflict.localCategory)
        assertEquals(LibraryCategory.Dropped, conflict.remoteCategory)
    }

    @Test
    fun `remote favorite is merged without replacing matching primary status`() {
        val actions = planLibraryMerge(
            localItems = listOf(local(setOf(LibraryCategory.Planned))),
            remoteItems = listOf(remote(list = YummyUserList.Planned, favorite = true)),
        )

        assertTrue(actions.contains(LibraryMergeAction.ClearConflict("42")))
        assertTrue(actions.contains(LibraryMergeAction.AddRemoteFavorite("42")))
    }

    @Test
    fun `remote primary status is added to a legacy favorite-only entry`() {
        val actions = planLibraryMerge(
            localItems = listOf(local(setOf(LibraryCategory.Favorite))),
            remoteItems = listOf(remote(list = YummyUserList.Watching, favorite = true)),
        )

        assertTrue(actions.contains(LibraryMergeAction.AddRemotePrimary("42", LibraryCategory.Watching)))
        assertTrue(actions.contains(LibraryMergeAction.ClearConflict("42")))
    }

    @Test
    fun `remote favorite-only title is imported without inventing a primary status`() {
        val actions = planLibraryMerge(
            localItems = emptyList(),
            remoteItems = listOf(remote(list = null, favorite = true)),
        )

        val import = actions.single() as LibraryMergeAction.ImportRemote
        assertEquals(setOf(LibraryCategory.Favorite), import.categories)
    }

    private fun local(categories: Set<LibraryCategory>) = LocalLibraryItem(
        id = "42",
        anime = Anime(id = "42", title = "Title", subtitle = "", episodesLabel = "", status = ""),
        categories = categories,
        addedAt = null,
    )

    private fun remote(
        list: YummyUserList?,
        favorite: Boolean = false,
    ) = YummyUserAnimeListItem(
        animeId = 42L,
        title = "Title",
        posterUrl = null,
        yummyRating = null,
        list = list,
        isFavorite = favorite,
        addedAt = null,
    )
}
