package org.akkirrai.hibiki.app.navigation

import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.player.model.PlaybackSelection
import org.akkirrai.hibiki.player.model.WatchSource

internal fun WatchStateRepository.savePlaybackSourceSelection(
    titleId: String,
    source: WatchSource,
) {
    saveSelectedSource(
        titleId = titleId,
        sourceId = source.sourceId,
        sourceTitle = source.title,
        quality = source.qualityLabel,
        playerName = null,
        autoSelect = false,
    )
}

internal fun WatchStateRepository.savePlaybackSelection(selection: PlaybackSelection) {
    saveSelectedSource(
        titleId = selection.titleId,
        sourceId = selection.sourceId,
        sourceTitle = selection.sourceTitle,
        quality = selection.quality,
        playerName = selection.playerName,
        autoSelect = false,
    )
}

internal fun WatchStateRepository.loadPlaybackSelectionOrNull(titleId: String): PlaybackSelection? {
    val selection = getSelectedSource(titleId)
    val sourceId = selection.sourceId ?: return null
    return PlaybackSelection(
        titleId = titleId,
        sourceId = sourceId,
        sourceTitle = selection.sourceTitle.orEmpty(),
        quality = selection.quality,
        playerName = selection.playerName,
    )
}
