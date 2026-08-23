package org.akkirrai.hibiki.profile

import org.akkirrai.hibiki.library.LibraryEntry
import org.akkirrai.hibiki.player.model.EpisodeWatchProgress
import org.akkirrai.hibiki.player.model.PlaybackContext
import org.akkirrai.hibiki.player.model.PlaybackStream

/** Platform-neutral read boundary for the local profile feature. */
interface LocalProfileDataRepository {
    /**
     * [libraryEntries], when supplied, is reused instead of re-querying library storage —
     * callers that already fetched the library for another screen should pass it through so
     * the same data isn't read twice per refresh.
     */
    suspend fun load(libraryEntries: List<LibraryEntry>? = null): LocalProfileData

    fun updateProfileName(name: String): String? = null

    fun updateProfileAvatar(uri: String) = Unit
}

/** Read boundary for locally persisted playback state used by Profile. */
interface LocalWatchStateRepository {
    fun getAllEpisodeProgress(): List<EpisodeWatchProgress>

    fun getDailyWatchActivity(): List<DailyWatchActivity>
}

/** Platform storage boundary for playback progress written by shared hosts. */
interface PlaybackProgressRepository {
    fun getAllPlaybackProgress(): List<EpisodeWatchProgress>

    fun getPlaybackProgress(titleId: String, episodeId: String): EpisodeWatchProgress?

    fun saveEpisodeProgress(
        context: PlaybackContext,
        playback: PlaybackStream,
        positionMs: Long,
        durationMs: Long,
    )
}
