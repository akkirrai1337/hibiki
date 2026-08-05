package org.akkirrai.hibiki.shared.app.destination.context

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.shared.player.OfflineWatchDataRepository

internal data class AppDestinationPlaybackContext(
    val episodeDownloadRepository: EpisodeDownloadRepository?,
    val offlineWatchDataRepository: OfflineWatchDataRepository?,
    val offlineTitleMetadataRepository: OfflineTitleMetadataRepository?,
    val resumeFrameContent: (@Composable (String, Modifier) -> Unit)?,
    val downloadMode: Boolean,
)
