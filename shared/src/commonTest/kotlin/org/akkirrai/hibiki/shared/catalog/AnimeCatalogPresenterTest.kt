package org.akkirrai.hibiki.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.akkirrai.hibiki.shared.model.Anime

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeCatalogPresenterTest {
    @Test
    fun presenterLoadsPagesAndMergesDistinctItems() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
                val all = listOf(
                    Anime("1", "One", "", "1 episode", "Finished"),
                    Anime("2", "Two", "", "1 episode", "Finished"),
                    Anime("3", "Three", "", "1 episode", "Finished"),
                )
                val page = all.drop(query.offset).take(query.pageSize)
                return AnimeCatalogPage(page, query.page, query.offset + page.size < all.size)
            }
        }
        val presenter = AnimeCatalogPresenter(repository, this, pageSize = 2)

        presenter.search()
        advanceUntilIdle()
        assertEquals(listOf("1", "2"), presenter.state.value.items.map { it.id })
        assertTrue(presenter.state.value.canLoadMore)

        presenter.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), presenter.state.value.items.map { it.id })
        assertFalse(presenter.state.value.canLoadMore)
    }

    @Test
    fun presenterRestoresASeparateSourceSession() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
                AnimeCatalogPage(emptyList(), query.page, false)
        }
        val presenter = AnimeCatalogPresenter(repository, this)
        val cached = AnimeCatalogUiState(
            query = "saved query",
            items = listOf(Anime("source:one", "Saved title", "", "", "")),
            page = 2,
            canLoadMore = true,
        )

        presenter.restore(cached)

        assertEquals("saved query", presenter.state.value.query)
        assertEquals(listOf("source:one"), presenter.state.value.items.map(Anime::id))
        assertEquals(2, presenter.state.value.page)
        assertTrue(presenter.state.value.canLoadMore)
        assertFalse(presenter.state.value.isLoading)
    }

    @Test
    fun presenterReturnsToPreviousDetailsFromRelatedTitle() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
                AnimeCatalogPage(emptyList(), query.page, false)

            override suspend fun getDetails(id: String, fallback: Anime): Anime =
                fallback.copy(description = "Loaded $id")
        }
        val presenter = AnimeCatalogPresenter(repository, this)
        val first = Anime("one", "One", "", "", "")
        val second = Anime("two", "Two", "", "", "")

        presenter.openDetails(first)
        advanceUntilIdle()
        presenter.openDetails(second)
        advanceUntilIdle()

        presenter.closeDetails()
        advanceUntilIdle()
        assertEquals(first.id, presenter.state.value.selectedAnime?.id)
        assertEquals("Loaded one", presenter.state.value.selectedAnime?.description)

        presenter.closeDetails()
        assertEquals(null, presenter.state.value.selectedAnime)
    }
}
