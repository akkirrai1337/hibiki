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
}
