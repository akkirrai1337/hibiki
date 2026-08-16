package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream

/** A source-owned voiceover or release group available for watching a title. */
data class PlaybackGroup(
    val id: String,
    val title: String,
    val episodes: List<Episode>,
    val qualityLabel: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Playback group id must not be blank" }
        require(title.isNotBlank()) { "Playback group title must not be blank" }
        require(episodes.isNotEmpty()) { "Playback group must contain at least one episode" }
    }
}

/** Optional capability implemented only by sources that can provide playable media. */
interface PlaybackSource {
    suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup>

    suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink>
}

/** Converts a source-owned player link into one or more directly playable streams. */
interface StreamExtractor {
    fun supports(link: PlayerLink): Boolean

    suspend fun extract(link: PlayerLink): VideoStream

    suspend fun extractVariants(link: PlayerLink): List<VideoStream> = listOf(extract(link))
}

/** Verifies that a resolved stream can be consumed before it reaches the player. */
interface StreamValidator {
    suspend fun validate(stream: VideoStream): StreamValidationResult
}
