package org.akkirrai.hibiki.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.player.model.WatchEpisode
import org.akkirrai.hibiki.player.EpisodeDownloadRepository
import org.akkirrai.hibiki.player.EpisodeDownloadState

@Composable
internal fun PollEpisodeDownloadStates(
    repository: EpisodeDownloadRepository?,
    sourceId: String,
    episodes: List<WatchEpisode>,
    onStatesChanged: (Map<String, EpisodeDownloadState>) -> Unit,
) {
    LaunchedEffect(repository, sourceId, episodes) {
        val repositoryForDownload = repository ?: return@LaunchedEffect
        val validSourceId = sourceId.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (episodes.isEmpty()) return@LaunchedEffect
        while (true) {
            onStatesChanged(repositoryForDownload.getEpisodeStates(validSourceId, episodes.map(WatchEpisode::id)))
            delay(700L)
        }
    }
}
