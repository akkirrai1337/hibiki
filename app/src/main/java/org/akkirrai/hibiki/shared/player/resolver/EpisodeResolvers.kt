package org.akkirrai.hibiki.shared.player

import androidx.compose.runtime.Composable
import org.akkirrai.hibiki.shared.player.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.shared.player.model.WatchEpisode
import org.akkirrai.hibiki.shared.player.model.progressStatus

fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

private val episodeTitlePattern = Regex("""^Episode\s+(.+)$""", RegexOption.IGNORE_CASE)

fun fallbackEpisodeNumberFromTitle(title: String): String? = episodeTitlePattern
    .matchEntire(title.trim())
    ?.groupValues
    ?.getOrNull(1)
    ?.takeIf(String::isNotBlank)

@Composable
fun resolveLocalizedEpisodeTitle(
    title: String,
    episodeLabel: @Composable (number: String) -> String,
): String {
    val fallbackEpisodeNumber = fallbackEpisodeNumberFromTitle(title)
    return if (fallbackEpisodeNumber != null) episodeLabel(fallbackEpisodeNumber) else title
}

@Composable
fun resolveEpisodeNumberTitle(
    episodeNumber: Double,
    episodeLabel: @Composable (number: String) -> String,
): String = episodeLabel(formatEpisodeNumber(episodeNumber))

fun resolveCurrentEpisode(
    requestedEpisodeId: String,
    requestedEpisodeNumber: Double?,
    episodes: List<WatchEpisode>,
    currentEpisodes: List<WatchEpisode>,
    savedEpisodeNumber: Double? = null,
): WatchEpisode? {
    episodes.firstOrNull { it.id == requestedEpisodeId }?.let { return it }
    val knownNumber = requestedEpisodeNumber
        ?: currentEpisodes.firstOrNull { it.id == requestedEpisodeId }?.number
        ?: savedEpisodeNumber
    return knownNumber?.let { number -> episodes.firstOrNull { it.number == number } }
}

fun resolveCurrentEpisodeTitle(
    playbackTitle: String?,
    currentEpisodeId: String?,
    episodes: List<WatchEpisode>,
): String {
    val title = playbackTitle.orEmpty().trim()
    if (title.isNotBlank()) return title

    return resolveCurrentEpisodeNumber(currentEpisodeId, episodes)
        ?.let { "Episode $it" }
        .orEmpty()
}

fun resolveCurrentEpisodeNumber(
    currentEpisodeId: String?,
    episodes: List<WatchEpisode>,
): String? = episodes
    .firstOrNull { it.id == currentEpisodeId }
    ?.let { formatEpisodeNumber(it.number) }

fun resolveNextEpisodeNumber(progressItems: List<EpisodeWatchProgress>, episodeCount: Int?): Double? {
    val lastWatched = progressItems.filter(EpisodeWatchProgress::isWatchedToEnd).map(EpisodeWatchProgress::episodeNumber).maxOrNull() ?: return 1.0
    val nextSaved = progressItems.map(EpisodeWatchProgress::episodeNumber).filter { it > lastWatched }.minOrNull()
    if (nextSaved != null) return nextSaved
    val inferred = lastWatched + 1.0
    return if (episodeCount == null || inferred <= episodeCount.toDouble()) inferred else null
}

fun resolveAdjacentEpisode(
    episodes: List<WatchEpisode>,
    currentEpisodeId: String,
    currentEpisodeNumber: Double?,
    offset: Int,
): WatchEpisode? {
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    if (currentIndex != -1) return episodes.getOrNull(currentIndex + offset)

    val number = currentEpisodeNumber ?: return null
    return if (offset < 0) {
        episodes.filter { it.number < number }.maxByOrNull(WatchEpisode::number)
    } else {
        episodes.filter { it.number > number }.minByOrNull(WatchEpisode::number)
    }
}

@Composable
fun resolvePlayerEpisodeSubtitle(
    state: PlayerUiState,
    episodeLabel: @Composable (String) -> String,
): String = resolveLocalizedEpisodeTitle(
    title = resolveCurrentEpisodeTitle(
        playbackTitle = state.playback?.episodeTitle,
        currentEpisodeId = state.currentEpisodeId,
        episodes = state.episodes,
    ).let { fallbackTitle ->
        if (state.playback?.episodeTitle.isNullOrBlank()) {
            resolveCurrentEpisodeNumber(state.currentEpisodeId, state.episodes)
                ?.let { number -> episodeLabel(number) }
                ?: fallbackTitle
        } else {
            fallbackTitle
        }
    },
    episodeLabel = episodeLabel,
)

fun resolveEpisodeProgressStatus(progress: EpisodeWatchProgress?): EpisodeProgressStatus =
    progress?.progressStatus() ?: EpisodeProgressStatus.NotStarted
