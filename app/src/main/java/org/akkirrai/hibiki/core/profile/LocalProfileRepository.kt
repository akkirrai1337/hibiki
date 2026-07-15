package org.akkirrai.hibiki.core.profile

import android.content.Context
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.WatchStateRepository.DailyWatchActivity
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository

/** Source-independent data owned by Hibiki and used by the local profile. */
class LocalProfileRepository(
    context: Context,
    private val watchStateRepository: WatchStateRepository = WatchStateRepository(context.applicationContext),
    private val libraryRepository: LibraryRepository = LibraryRepository(context.applicationContext),
) {
    fun getData(): LocalProfileData {
        val entries = libraryRepository.getLibraryEntries()
        val library = entries
            .groupBy { it.anime.id }
            .map { (id, sameTitleEntries) ->
                LocalLibraryItem(
                    id = id,
                    anime = sameTitleEntries.first().anime,
                    categories = sameTitleEntries.mapTo(linkedSetOf()) { it.category },
                    addedAt = sameTitleEntries.mapNotNull { it.addedAt }.minOrNull(),
                )
            }

        return LocalProfileData(
            episodeProgress = watchStateRepository.getAllEpisodeProgress(),
            activity = watchStateRepository.getDailyWatchActivity(),
            library = library,
        )
    }
}

data class LocalProfileData(
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
