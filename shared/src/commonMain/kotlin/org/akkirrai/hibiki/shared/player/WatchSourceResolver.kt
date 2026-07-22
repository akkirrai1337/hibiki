package org.akkirrai.hibiki.shared.player

import org.akkirrai.hibiki.shared.model.WatchSource
import org.akkirrai.hibiki.shared.model.WatchSourceSelection

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
