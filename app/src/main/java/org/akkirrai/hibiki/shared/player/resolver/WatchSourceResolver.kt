package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.WatchSourceSelection

fun resolveWatchSource(
    sources: List<WatchSource>,
    selection: WatchSourceSelection,
): WatchSource? {
    if (sources.isEmpty()) return null
    return if (selection.autoSelect) {
        sources.first()
    } else {
        sources.firstOrNull { it.sourceId == selection.sourceId } ?: sources.first()
    }
}

fun hasWatchSource(selectedSource: WatchSource?, selection: WatchSourceSelection): Boolean =
    selectedSource != null || !selection.sourceTitle.isNullOrBlank()
