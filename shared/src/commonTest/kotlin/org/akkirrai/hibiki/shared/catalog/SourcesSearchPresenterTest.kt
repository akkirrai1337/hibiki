package org.akkirrai.hibiki.shared.catalog

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.source.AppSourceDescriptor

class SourcesSearchPresenterTest {
    @Test
    fun trimsLeadingWhitespaceAndRestrictsCyrillicSearchToRussianSources() = runTest {
        val repository = FakeRepository()
        val presenter = SourcesSearchPresenter(repository, sources(), backgroundScope)

        presenter.onQueryChange("  аниме")
        testScheduler.advanceTimeBy(400)
        runCurrent()

        assertEquals("аниме", presenter.state.value.query)
        assertEquals(listOf("ru"), repository.searchCalls)
        presenter.close()
    }

    @Test
    fun retryOnlyRepeatsTheFailedSource() = runTest {
        val repository = FakeRepository(failingSources = setOf("en"))
        val presenter = SourcesSearchPresenter(repository, sources(), backgroundScope)

        presenter.onQueryChange("anime")
        testScheduler.advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("ru", "en"), repository.searchCalls)
        assertTrue(presenter.state.value.sections.single { it.sourceId == "en" }.hasError)

        repository.failingSources = emptySet()
        presenter.retry("en")
        runCurrent()

        assertEquals(listOf("ru", "en", "en"), repository.searchCalls)
        assertFalse(presenter.state.value.sections.single { it.sourceId == "en" }.hasError)
        presenter.close()
    }

    private fun sources() = listOf(
        AppSourceDescriptor("ru", "Russian", "russian", supportsSearch = true),
        AppSourceDescriptor("en", "English", "english", supportsSearch = true),
    )

    private class FakeRepository(
        var failingSources: Set<String> = emptySet(),
    ) : MultiSourceAnimeCatalogRepository {
        private val item = Anime("1", "Result", "", "", "")
        val searchCalls = mutableListOf<String>()
        override val initialItems: List<Anime> = emptyList()

        override suspend fun search(query: AnimeCatalogQuery): AnimeCatalogPage =
            AnimeCatalogPage(listOf(item), 1, false)

        override suspend fun searchSource(
            sourceId: String,
            query: AnimeCatalogQuery,
        ): AnimeCatalogPage {
            searchCalls += sourceId
            if (sourceId in failingSources) error("source failed")
            return AnimeCatalogPage(listOf(item), 1, false)
        }
    }
}
