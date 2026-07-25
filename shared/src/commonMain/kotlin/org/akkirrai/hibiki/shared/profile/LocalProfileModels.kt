package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.EpisodeWatchProgress

data class DailyWatchActivity(
    val date: String,
    val watchedMs: Long,
    val completedEpisodes: Int,
)

data class LocalProfileData(
    val profileName: String = "",
    val profileAvatarUri: String? = null,
    val episodeProgress: List<EpisodeWatchProgress> = emptyList(),
    val activity: List<DailyWatchActivity> = emptyList(),
    val library: List<LocalLibraryItem> = emptyList(),
)

data class LocalLibraryItem(
    val id: String,
    val anime: Anime,
    val categories: Set<LibraryCategory>,
    val addedAt: Long?,
)
