package org.akkirrai.beakokit.api

import org.akkirrai.animeresolver.model.AnimeTitle
import org.akkirrai.animeresolver.model.Episode
import org.akkirrai.animeresolver.model.PlayerLink

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
