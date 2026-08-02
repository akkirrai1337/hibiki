package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryRepository
import platform.Foundation.NSUserDefaults

/**
 * Persistent iOS adapter for the local profile.
 *
 * Library data is read from the same repository instance used by the shared
 * library screen. Watch progress and activity are intentionally not guessed
 * here; those records will be connected when the iOS playback state is
 * migrated.
 */
internal class IosLocalProfileRepository(
    private val libraryRepository: LibraryRepository,
    private val watchStateRepository: LocalWatchStateRepository = IosWatchStateRepository(),
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : LocalProfileDataRepository {
    override suspend fun load(): LocalProfileData {
        val entries = libraryRepository.getEntries()
        val library = entries
            .groupBy { it.anime.id }
            .map { (id, groupedEntries) ->
                LocalLibraryItem(
                    id = id,
                    anime = groupedEntries.first().anime,
                    categories = groupedEntries.map { it.category }.toSet(),
                    addedAt = groupedEntries.mapNotNull { it.addedAt }.minOrNull(),
                )
            }

        return LocalProfileData(
            profileName = defaults.stringForKey(PROFILE_NAME_KEY).orEmpty()
                .trim()
                .ifBlank { DEFAULT_PROFILE_NAME },
            profileAvatarUri = defaults.stringForKey(PROFILE_AVATAR_URI_KEY),
            episodeProgress = watchStateRepository.getAllEpisodeProgress(),
            activity = watchStateRepository.getDailyWatchActivity(),
            library = library,
        )
    }

    override fun updateProfileName(name: String): String {
        val profileName = name.trim().ifBlank { DEFAULT_PROFILE_NAME }
        defaults.setObject(profileName, forKey = PROFILE_NAME_KEY)
        return profileName
    }

    override fun updateProfileAvatar(uri: String) {
        defaults.setObject(uri, forKey = PROFILE_AVATAR_URI_KEY)
    }

    private companion object {
        const val DEFAULT_PROFILE_NAME = "hibiki"
        const val PROFILE_NAME_KEY = "hibiki.profile.name"
        const val PROFILE_AVATAR_URI_KEY = "hibiki.profile.avatar"
    }
}
