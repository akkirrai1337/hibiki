package org.akkirrai.hibiki.catalog
import org.akkirrai.hibiki.catalog.presentation.*
import org.akkirrai.hibiki.catalog.sort.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeCatalogPresenterTest {
    @Test
    fun presenterLoadsPagesAndMergesDistinctItems() = runTest {
        var requestedFilters: AnimeSearchFilters? = null
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
                requestedFilters = query.filters
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
        assertEquals("popular", requestedFilters?.sortAlias)
        assertEquals(listOf("1", "2"), presenter.state.value.items.map { it.id })
        assertTrue(presenter.state.value.canLoadMore)

        presenter.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), presenter.state.value.items.map { it.id })
        assertFalse(presenter.state.value.canLoadMore)
    }

    @Test
    fun onQueryChangeSkipsSearchBelowMinLengthAndDebouncesLongerQueries() = runTest {
        val searchCalls = mutableListOf<String>()
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage {
                searchCalls += query.text
                return AnimeCatalogPage(emptyList(), query.page, false)
            }
        }
        val presenter = AnimeCatalogPresenter(repository, this)

        // A single short keystroke must not fire a request -- some sources 400 on a 1-2 char
        // query, and it's about to be invalidated by the next keystroke anyway.
        presenter.onQueryChange("r")
        advanceUntilIdle()
        assertEquals(emptyList(), searchCalls)

        // Typing the rest of a real query quickly should collapse into a single request once
        // typing settles, not one request per keystroke.
        presenter.onQueryChange("re")
        testScheduler.advanceTimeBy(100)
        presenter.onQueryChange("rel")
        testScheduler.advanceTimeBy(100)
        presenter.onQueryChange("release")
        advanceUntilIdle()
        assertEquals(listOf("release"), searchCalls)

        // Clearing the box back to empty means "go back to browsing" and should search
        // immediately rather than waiting out the debounce.
        presenter.onQueryChange("")
        runCurrent()
        assertEquals(listOf("release", ""), searchCalls)
    }

    @Test
    fun presenterContinuesExternalPaginationAfterAShortPage() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override fun canContinuePaginationAfterShortPage() = true

            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
                if (query.page == 1) {
                    AnimeCatalogPage(
                        items = listOf(Anime("1", "One", "", "", "")),
                        page = 1,
                        canLoadMore = false,
                    )
                } else if (query.page == 2) {
                    AnimeCatalogPage(
                        items = listOf(Anime("2", "Two", "", "", "")),
                        page = query.page,
                        canLoadMore = false,
                    )
                } else {
                    AnimeCatalogPage(emptyList(), query.page, canLoadMore = false)
                }
        }
        val presenter = AnimeCatalogPresenter(repository, this, pageSize = 24)

        presenter.search()
        advanceUntilIdle()
        assertTrue(presenter.state.value.canLoadMore)

        presenter.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), presenter.state.value.items.map(Anime::id))
        assertTrue(presenter.state.value.canLoadMore)

        presenter.loadMore()
        advanceUntilIdle()

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

    @Test
    fun clearDetailsDropsAllRelatedDetailsAtOnce() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
                AnimeCatalogPage(emptyList(), query.page, false)

            override suspend fun getDetails(id: String, fallback: Anime): Anime = fallback
        }
        val presenter = AnimeCatalogPresenter(repository, this)
        presenter.openDetails(Anime("one", "One", "", "", ""))
        advanceUntilIdle()
        presenter.openDetails(Anime("two", "Two", "", "", ""))
        advanceUntilIdle()

        presenter.clearDetails()

        assertEquals(null, presenter.state.value.selectedAnime)
    }

    @Test
    fun catalogFiltersSurviveDetailsRoundTrip() = runTest {
        val repository = object : AnimeCatalogRepository {
            override val initialItems: List<Anime> = emptyList()
            override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
                AnimeCatalogPage(emptyList(), query.page, false)
        }
        val presenter = AnimeCatalogPresenter(repository, this)
        val filters = AnimeSearchFilters(typeAlias = "tv", yearFrom = 2020, yearTo = 2024)

        presenter.setFilters(filters)
        presenter.openDetails(Anime("one", "One", "", "", ""))
        advanceUntilIdle()
        presenter.closeDetails()

        assertEquals(filters, presenter.state.value.filters)
        assertEquals(null, presenter.state.value.selectedAnime)
    }
}
