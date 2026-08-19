package org.akkirrai.hibiki.player

import org.akkirrai.hibiki.player.model.WatchSource

/** Android reference shows three source rows before expanding the list. */
const val INITIAL_VISIBLE_SOURCE_COUNT = 3

fun visibleWatchSources(allItems: List<WatchSource>, showAllItems: Boolean): List<WatchSource> =
    if (showAllItems) allItems else allItems.take(INITIAL_VISIBLE_SOURCE_COUNT)

fun hasMoreWatchSources(
    allItems: List<WatchSource>,
    visibleItems: List<WatchSource>,
    showAllItems: Boolean,
): Boolean = !showAllItems && allItems.size > visibleItems.size

fun mergeWatchSources(primary: List<WatchSource>, secondary: List<WatchSource>): List<WatchSource> =
    (primary + secondary).associateBy(WatchSource::sourceId).values.toList()

fun WatchSourcesScreenState.withSources(
    sources: List<WatchSource>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    showAllItems: Boolean,
): WatchSourcesScreenState {
    val visibleItems = visibleWatchSources(sources, showAllItems)
    return copy(
        allItems = sources,
        items = visibleItems,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMoreItems = hasMoreWatchSources(sources, visibleItems, showAllItems),
        showAllItems = showAllItems,
        errorMessage = null,
    )
}

fun initialWatchSourcesState(
    cachedSources: List<WatchSource>?,
    offlineSources: List<WatchSource>,
    forceRefresh: Boolean,
): WatchSourcesScreenState {
    if (forceRefresh) {
        return WatchSourcesScreenState(isLoading = true)
    }

    val mergedSources = mergeWatchSources(cachedSources.orEmpty(), offlineSources)
    val visibleItems = visibleWatchSources(mergedSources, showAllItems = false)
    return WatchSourcesScreenState(
        allItems = mergedSources,
        items = visibleItems,
        isLoading = cachedSources == null,
        hasMoreItems = hasMoreWatchSources(mergedSources, visibleItems, showAllItems = false),
    )
}

fun WatchSourcesScreenState.withLoadedSources(
    sources: List<WatchSource>,
    offlineSources: List<WatchSource>,
    isLoading: Boolean,
): WatchSourcesScreenState = withSources(
    sources = mergeWatchSources(sources, offlineSources),
    isLoading = isLoading,
    isLoadingMore = false,
    showAllItems = false,
)

fun WatchSourcesScreenState.withWatchSourcesError(errorMessage: String): WatchSourcesScreenState = copy(
    isLoading = false,
    isLoadingMore = false,
    errorMessage = errorMessage.takeIf { items.isEmpty() },
)

fun WatchSourcesScreenState.showAllWatchSources(): WatchSourcesScreenState = copy(
    showAllItems = true,
    items = allItems,
    hasMoreItems = false,
    isLoadingMore = false,
    errorMessage = null,
)
