package org.akkirrai.hibiki.home
import org.akkirrai.hibiki.home.data.*
import org.akkirrai.hibiki.home.model.*
import org.akkirrai.hibiki.home.presentation.*
import org.akkirrai.hibiki.home.screen.*
import org.akkirrai.hibiki.home.state.*
import org.akkirrai.hibiki.home.ui.*

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.akkirrai.hibiki.catalog.AnimeCatalogPage
import org.akkirrai.hibiki.catalog.AnimeCatalogQuery
import org.akkirrai.hibiki.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.search.model.AnimeSearchFilters
import org.akkirrai.hibiki.search.model.SearchUiState

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

    @Test
    fun failedSearchCanBeRetried() = runTest {
        val repository = RetryRepository(failSearchAttempts = 1)
        val presenter = HomeSearchPresenter(repository, backgroundScope)
        presenter.onQueryChange("nar")
        advanceSearch()

        assertIs<SearchUiState.Error>(presenter.state.value.result)
        presenter.retrySearch()
        runCurrent()

        assertIs<SearchUiState.Content>(presenter.state.value.result)
        presenter.close()
    }

    @Test
    fun failedLoadMoreCanBeRetriedAndUsesNextPage() = runTest {
        val repository = RetryRepository(failLoadMoreAttempts = 1)
        val presenter = HomeSearchPresenter(repository, backgroundScope, pageSize = 1)
        presenter.onQueryChange("nar")
        advanceSearch()
        presenter.loadMore()
        runCurrent()

        val failed = assertIs<SearchUiState.Content>(presenter.state.value.result)
        assertEquals("load more failed", failed.loadMoreError)
        presenter.loadMore()
        runCurrent()

        val loaded = assertIs<SearchUiState.Content>(presenter.state.value.result)
        assertEquals(listOf("Naruto", "Boruto"), loaded.items.map { it.title })
        assertEquals(listOf(1, 2, 2), repository.requestedPages)
        presenter.close()
    }

    @Test
    fun sourceResetReloadsFilterCatalogAndPreservesQuery() = runTest {
        val repository = RetryRepository()
        val presenter = HomeSearchPresenter(repository, backgroundScope)
        presenter.onQueryChange("nar")
        advanceSearch()
        presenter.applyFilters(AnimeSearchFilters(sortAlias = "popular"))
        runCurrent()

        presenter.resetForSource()
        runCurrent()

        assertEquals("nar", presenter.state.value.query)
        assertEquals(AnimeSearchFilters(), presenter.state.value.filters)
        assertEquals(SearchUiState.Idle, presenter.state.value.result)
        assertEquals("source", presenter.state.value.filterCatalog?.sortOptions?.single()?.id)
        assertEquals(1, repository.filterCatalogCalls)
        presenter.close()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.advanceSearch() {
        testScheduler.advanceTimeBy(450)
        testScheduler.runCurrent()
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

    private class RetryRepository(
        private var failSearchAttempts: Int = 0,
        private var failLoadMoreAttempts: Int = 0,
    ) : AnimeCatalogRepository {
        private val items = listOf(
            Anime("1", "Naruto", "", "", ""),
            Anime("2", "Boruto", "", "", ""),
        )
        val requestedPages = mutableListOf<Int>()
        var filterCatalogCalls = 0
        override val initialItems: List<Anime> = emptyList()

        override suspend fun filterCatalog(): AnimeCatalogFilterCatalog {
            filterCatalogCalls++
            return AnimeCatalogFilterCatalog(
                sortOptions = listOf(AnimeCatalogFilterOption("source", "Source")),
            )
        }

        override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
            requestedPages += query.page
            if (query.page == 1 && failSearchAttempts > 0) {
                failSearchAttempts--
                error("search failed")
            }
            if (query.page == 2 && failLoadMoreAttempts > 0) {
                failLoadMoreAttempts--
                error("load more failed")
            }
            return AnimeCatalogPage(
                items = listOf(items[query.page - 1]),
                page = query.page,
                canLoadMore = query.page == 1,
            )
        }
    }
}
