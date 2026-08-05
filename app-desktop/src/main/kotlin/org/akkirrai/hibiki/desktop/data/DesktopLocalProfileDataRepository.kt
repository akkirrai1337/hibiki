package org.akkirrai.hibiki.desktop.data

import java.util.prefs.Preferences
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.library.LibraryRepository

internal class DesktopLocalProfileDataRepository(
    private val progressRepository: DesktopPlaybackProgressRepository,
    private val libraryRepository: LibraryRepository,
    private val preferences: Preferences = Preferences.userNodeForPackage(DesktopLocalProfileDataRepository::class.java),
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
            profileName = preferences.get(PROFILE_NAME_KEY, DEFAULT_PROFILE_NAME).trim().ifBlank { DEFAULT_PROFILE_NAME },
            profileAvatarUri = preferences.get(PROFILE_AVATAR_URI_KEY, null),
            episodeProgress = progressRepository.getAllPlaybackProgress(),
            activity = progressRepository.getDailyWatchActivity(),
            library = library,
        )
    }

    override fun updateProfileName(name: String): String {
        val profileName = name.trim().ifBlank { DEFAULT_PROFILE_NAME }
        preferences.put(PROFILE_NAME_KEY, profileName)
        preferences.flush()
        return profileName
    }

    override fun updateProfileAvatar(uri: String) {
        preferences.put(PROFILE_AVATAR_URI_KEY, uri)
        preferences.flush()
    }

    private companion object {
        const val DEFAULT_PROFILE_NAME = "hibiki"
        const val PROFILE_NAME_KEY = "profile_name"
        const val PROFILE_AVATAR_URI_KEY = "profile_avatar_uri"
    }
}
