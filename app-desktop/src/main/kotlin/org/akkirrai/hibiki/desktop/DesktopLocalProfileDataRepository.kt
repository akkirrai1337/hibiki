package org.akkirrai.hibiki.desktop

import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.library.LibraryRepository

internal class DesktopLocalProfileDataRepository(
    private val progressRepository: DesktopPlaybackProgressRepository,
    private val libraryRepository: LibraryRepository,
) : LocalProfileDataRepository {
    override suspend fun load(): LocalProfileData {
        val entries = libraryRepository.getEntries()
        val library = entries.groupBy { it.anime.id }.map { (id, groupedEntries) ->
            org.akkirrai.hibiki.shared.profile.LocalLibraryItem(
                id = id,
                anime = groupedEntries.first().anime,
                categories = groupedEntries.mapTo(linkedSetOf()) { it.category },
                addedAt = groupedEntries.mapNotNull { it.addedAt }.minOrNull(),
            )
        }
        return LocalProfileData(
            profileName = "hibiki",
            episodeProgress = progressRepository.getAllPlaybackProgress(),
            activity = progressRepository.getDailyWatchActivity(),
            library = library,
        )
    }
}
