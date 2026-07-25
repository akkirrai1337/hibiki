package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource

const val INITIAL_VISIBLE_SOURCE_COUNT = 6

fun visibleWatchSources(allItems: List<WatchSource>, showAllItems: Boolean): List<WatchSource> =
    if (showAllItems) allItems else allItems.take(INITIAL_VISIBLE_SOURCE_COUNT)

fun hasMoreWatchSources(
    allItems: List<WatchSource>,
    visibleItems: List<WatchSource>,
    showAllItems: Boolean,
): Boolean = !showAllItems && allItems.size > visibleItems.size

fun mergeWatchSources(primary: List<WatchSource>, secondary: List<WatchSource>): List<WatchSource> =
    (primary + secondary).associateBy(WatchSource::sourceId).values.toList()
