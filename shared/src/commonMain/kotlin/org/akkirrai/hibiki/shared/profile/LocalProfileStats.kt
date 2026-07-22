package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.EpisodeProgressStatus
import org.akkirrai.hibiki.shared.model.progressStatus

data class LocalProfileStats(
    val libraryTotal: Int,
    val favoriteTotal: Int,
    val completedEpisodes: Int,
    val watchedMs: Long,
)

fun LocalProfileData.stats(): LocalProfileStats = LocalProfileStats(
    libraryTotal = library.distinctBy(LocalLibraryItem::id).size,
    favoriteTotal = library.count { LibraryCategory.Favorite in it.categories },
    completedEpisodes = maxOf(
        activity.sumOf { it.completedEpisodes },
        episodeProgress.count { it.progressStatus() == EpisodeProgressStatus.Watched },
    ),
    watchedMs = activity.sumOf { it.watchedMs },
)
