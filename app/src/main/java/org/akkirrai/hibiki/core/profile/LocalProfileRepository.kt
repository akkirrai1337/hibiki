package org.akkirrai.hibiki.core.profile

import android.content.Context
import org.akkirrai.animeresolver.metadata.YummyUserAnimeListItem
import org.akkirrai.animeresolver.metadata.YummyUserList
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.EpisodeWatchProgress
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.core.source.YummyIdMigration

/** Local, source-agnostic data used by the profile analytics. */
class LocalProfileRepository(
    context: Context,
    private val watchStateRepository: WatchStateRepository = WatchStateRepository(context.applicationContext),
    private val libraryRepository: LibraryRepository = LibraryRepository(context.applicationContext),
    private val conflictRepository: ProfileDataConflictRepository = ProfileDataConflictRepository(context.applicationContext),
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
            library = library,
        )
    }

    /**
     * Imports remote-only titles locally and records only true primary-status conflicts.
     * Local-only titles are retained; they are not silently pushed to the website.
     */
    fun mergeRemoteLibrary(
        remoteItems: List<YummyUserAnimeListItem>,
        remoteMetadata: List<Anime>,
    ): List<LibraryStatusConflict> {
        conflictRepository.retainLibraryStatusConflicts(
            remoteItems.mapTo(mutableSetOf()) {
                YummyIdMigration.normalizeTitleId(it.animeId.toString())
            },
        )
        val metadataById = remoteMetadata.associateBy { YummyIdMigration.normalizeTitleId(it.id) }
        val localItems = getData().library

        planLibraryMerge(localItems, remoteItems).forEach { action ->
            when (action) {
                is LibraryMergeAction.ImportRemote -> {
                    val remote = action.remote
                    val anime = metadataById[action.id] ?: Anime(
                        id = action.id,
                        title = remote.title,
                        subtitle = "",
                        episodesLabel = "",
                        status = "",
                        posterUrl = remote.posterUrl,
                    )
                    libraryRepository.importLibraryEntry(
                        anime = anime,
                        categories = action.categories,
                        addedAt = remote.addedAt?.toEpochMillis(),
                    )
                }
                is LibraryMergeAction.RecordConflict -> {
                    val localTitle = localItems.firstOrNull { it.id == action.id }?.anime?.title.orEmpty()
                    conflictRepository.recordLibraryStatusConflict(
                        animeId = action.id,
                        title = action.remote.title.ifBlank { localTitle },
                        localCategory = action.localCategory,
                        remoteCategory = action.remoteCategory,
                    )
                }
                is LibraryMergeAction.ClearConflict ->
                    conflictRepository.clearLibraryStatusConflictForAnime(action.id)
                is LibraryMergeAction.AddRemotePrimary ->
                    libraryRepository.replacePrimaryCategory(action.id, action.category)
                is LibraryMergeAction.AddRemoteFavorite ->
                    libraryRepository.addSupplementalCategory(action.id, LibraryCategory.Favorite)
            }
        }

        return conflictRepository.pendingLibraryStatusConflicts()
    }

    fun pendingLibraryStatusConflicts(): List<LibraryStatusConflict> =
        conflictRepository.pendingLibraryStatusConflicts()

    fun applyRemoteLibraryStatus(conflict: LibraryStatusConflict) {
        libraryRepository.replacePrimaryCategory(conflict.animeId, conflict.remoteCategory)
        conflictRepository.clearLibraryStatusConflict(conflict.key)
    }

    fun completeLocalLibraryStatusResolution(conflict: LibraryStatusConflict) {
        conflictRepository.clearLibraryStatusConflict(conflict.key)
    }

}

internal fun YummyUserList.toLibraryCategory(): LibraryCategory = when (this) {
    YummyUserList.Watching -> LibraryCategory.Watching
    YummyUserList.Planned -> LibraryCategory.Planned
    YummyUserList.Completed -> LibraryCategory.Completed
    YummyUserList.Dropped -> LibraryCategory.Dropped
    YummyUserList.OnHold -> LibraryCategory.OnHold
}

internal fun LibraryCategory.toYummyUserList(): YummyUserList? = when (this) {
    LibraryCategory.Watching -> YummyUserList.Watching
    LibraryCategory.Planned -> YummyUserList.Planned
    LibraryCategory.Completed -> YummyUserList.Completed
    LibraryCategory.Dropped -> YummyUserList.Dropped
    LibraryCategory.OnHold -> YummyUserList.OnHold
    LibraryCategory.Favorite, LibraryCategory.Saved -> null
}

private fun Long.toEpochMillis(): Long = if (this in 1 until 1_000_000_000_000L) this * 1_000L else this

data class LocalProfileData(
    val episodeProgress: List<EpisodeWatchProgress> = emptyList(),
    val library: List<LocalLibraryItem> = emptyList(),
)

data class LocalLibraryItem(
    val id: String,
    val anime: Anime,
    val categories: Set<LibraryCategory>,
    val addedAt: Long?,
)
