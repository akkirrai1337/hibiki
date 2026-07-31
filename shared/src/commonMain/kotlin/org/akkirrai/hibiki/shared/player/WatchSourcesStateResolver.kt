package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource

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

fun WatchSourcesScreenState.showAllWatchSources(): WatchSourcesScreenState = copy(
    showAllItems = true,
    items = allItems,
    hasMoreItems = false,
    isLoadingMore = false,
    errorMessage = null,
)
