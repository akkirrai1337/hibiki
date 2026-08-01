package org.akkirrai.hibiki.shared.home

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.SearchUiState

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeSearchPresenterTest {
    @Test
    fun sourceResetPreservesQueryButClearsResultAndFilters() = runTest {
        val presenter = HomeSearchPresenter(repository(), backgroundScope)
        presenter.onQueryChange("naruto")
        testScheduler.advanceTimeBy(450)
        testScheduler.runCurrent()
        presenter.resetForSource()

        assertEquals("naruto", presenter.state.value.query)
        assertEquals(SearchUiState.Idle, presenter.state.value.result)
        presenter.close()
    }

    @Test
    fun searchUsesHomeOwnedState() = runTest {
        val presenter = HomeSearchPresenter(repository(), backgroundScope)
        presenter.onQueryChange("nar")
        testScheduler.advanceTimeBy(450)
        testScheduler.runCurrent()

        val result = assertIs<SearchUiState.Content>(presenter.state.value.result)
        assertEquals("Naruto", result.items.single().title)
        presenter.close()
    }

    private fun repository() = object : AnimeCatalogRepository {
        private val item = Anime(
            id = "1",
            title = "Naruto",
            subtitle = "",
            episodesLabel = "",
            status = "",
        )
        override val initialItems: List<Anime> = emptyList()
        override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
            AnimeCatalogPage(
                items = if (query.text == "nar") listOf(item) else emptyList(),
                page = 1,
                canLoadMore = false,
            )
    }
}
