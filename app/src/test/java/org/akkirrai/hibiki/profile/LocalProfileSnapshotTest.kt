package org.akkirrai.hibiki.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.library.LibraryCategory
import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeRating

class LocalProfileSnapshotTest {
    @Test
    fun buildsSharedProfileSectionsFromOneDataContract() {
        val watching = Anime(
            id = "watching",
            title = "Watching title",
            subtitle = "",
            episodesLabel = "12",
            status = "Ongoing",
            genres = listOf("Action", "Drama"),
            ratings = listOf(AnimeRating("source", 8.5)),
        )
        val favorite = Anime(
            id = "favorite",
            title = "Favorite title",
            subtitle = "",
            episodesLabel = "24",
            status = "Finished",
            genres = listOf("Action"),
        )
        val snapshot = buildLocalProfileSnapshot(
            data = LocalProfileData(
                library = listOf(
                    LocalLibraryItem("watching", watching, setOf(LibraryCategory.Watching), 1_767_571_200_000L),
                    LocalLibraryItem("favorite", favorite, setOf(LibraryCategory.Favorite), 1_767_484_800_000L),
                ),
                activity = listOf(
                    DailyWatchActivity("2026-01-04", watchedMs = 3_600_000L, completedEpisodes = 2),
                ),
            ),
            activityDateStrings = listOf("2026-01-04", "2026-01-05"),
            labels = LocalProfileSnapshotLabels(
                durationLabel = { "${it / 3_600_000L} h" },
                categoryLabel = { it.storageValue },
                dateLabel = { it.toString() },
                activityDateLabel = { it.substring(5) },
            ),
        )

        assertEquals("1 h", snapshot.watchTimeLabel)
        assertEquals(1, snapshot.activeDaysCount)
        assertEquals(2, snapshot.totalEpisodes)
        assertEquals(2, snapshot.libraryTotal)
        assertEquals(listOf(2, 0), snapshot.activityDays.map(ActivityDay::episodeCount))
        assertEquals(listOf("Watching title", "Favorite title"), snapshot.recentLibraryItems.map(RecentLibraryItem::title))
        assertEquals(listOf("Favorite title"), snapshot.favoriteLibraryItems.map(RecentLibraryItem::title))
        assertEquals(listOf("Action", "Drama"), snapshot.genreSegments.map(DistributionSegment::label))
        assertEquals(2, snapshot.genreTrackedTitlesCount)
        assertEquals(1, snapshot.libraryStatusSegments.first { it.label == "watching" }.count)
        assertEquals(1, snapshot.libraryStatusSegments.first { it.label == "favorite" }.count)
    }
}
