package org.akkirrai.hibiki.shared.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.akkirrai.hibiki.shared.model.WatchSource

class WatchSourcesStateResolverTest {
    @Test
    fun mergesSourcesByIdWhileKeepingLatestEntry() {
        val primary = listOf(source("one", "Primary"), source("two", "Two"))
        val secondary = listOf(source("one", "Offline"), source("three", "Three"))

        assertEquals(
            listOf("one", "two", "three"),
            mergeWatchSources(primary, secondary).map(WatchSource::sourceId),
        )
        assertEquals("Offline", mergeWatchSources(primary, secondary).first().title)
    }

    @Test
    fun limitsInitialSourcesAndReportsMoreItems() {
        val sources = (1..7).map { source("source-$it", it.toString()) }
        val visible = visibleWatchSources(sources, showAllItems = false)

        assertEquals(3, visible.size)
        assertTrue(hasMoreWatchSources(sources, visible, showAllItems = false))
        assertEquals(sources, visibleWatchSources(sources, showAllItems = true))
        assertFalse(hasMoreWatchSources(sources, sources, showAllItems = true))
    }

    @Test
    fun buildsCachedAndRefreshingStatesFromTheSameCommonRules() {
        val cached = listOf(source("cached", "Cached"))
        val offline = listOf(source("offline", "Offline"))

        val cachedState = initialWatchSourcesState(cached, offline, forceRefresh = false)
        assertEquals(listOf("cached", "offline"), cachedState.items.map(WatchSource::sourceId))
        assertFalse(cachedState.isLoading)

        val refreshingState = initialWatchSourcesState(cached, offline, forceRefresh = true)
        assertTrue(refreshingState.isLoading)
        assertTrue(refreshingState.items.isEmpty())
    }

    @Test
    fun loadedAndErrorStatesPreserveTheAndroidEmptyErrorRule() {
        val state = WatchSourcesScreenState(isLoading = true)
            .withLoadedSources(
                sources = listOf(source("one", "One")),
                offlineSources = emptyList(),
                isLoading = false,
            )
        assertEquals(listOf("one"), state.items.map(WatchSource::sourceId))
        assertEquals(
            "failure",
            WatchSourcesScreenState(isLoading = true).withWatchSourcesError("failure").errorMessage,
        )
        assertEquals(
            null,
            state.withWatchSourcesError("ignored").errorMessage,
        )
    }

    private fun source(id: String, title: String) = WatchSource(
        sourceId = id,
        title = title,
        episodeCount = null,
    )
}
