package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

class LocalProfileStatsTest {
    @Test
    fun aggregatesLibraryAndActivity() {
        val anime = Anime(id = "1", title = "Demo", subtitle = "", episodesLabel = "", status = "")
        val data = LocalProfileData(
            library = listOf(
                LocalLibraryItem("1", anime, setOf(LibraryCategory.Favorite), null),
                LocalLibraryItem("1", anime, setOf(LibraryCategory.Watching), null),
            ),
            activity = listOf(DailyWatchActivity("2026-01-01", watchedMs = 1200L, completedEpisodes = 2)),
            episodeProgress = listOf(
                EpisodeWatchProgress(
                    titleId = "1",
                    episodeId = "ep-1",
                    episodeNumber = 1.0,
                    sourceId = "source",
                    voiceoverId = "voice",
                    sourceTitle = "Source",
                    positionMs = 900L,
                    durationMs = 1000L,
                    updatedAt = 1L,
                ),
            ),
        )

        assertEquals(LocalProfileStats(1, 1, 2, 1200L), data.stats())
    }
}
