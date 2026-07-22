package org.akkirrai.hibiki.core.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.akkirrai.hibiki.core.source.LibraryRepository
import org.akkirrai.hibiki.core.source.WatchStateRepository
import org.akkirrai.hibiki.shared.profile.LocalLibraryItem
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository

/** Source-independent data owned by Hibiki and used by the local profile. */
class LocalProfileRepository(
    context: Context,
    private val watchStateRepository: WatchStateRepository = WatchStateRepository(context.applicationContext),
    private val libraryRepository: LibraryRepository = LibraryRepository(context.applicationContext),
) : LocalProfileDataRepository {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE)
    private val defaultProfileName = context.getString(org.akkirrai.hibiki.R.string.app_name)

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
            profileName = preferences.getString(PROFILE_NAME_KEY, null)?.trim().orEmpty().ifBlank { defaultProfileName },
            profileAvatarUri = preferences.getString(PROFILE_AVATAR_URI_KEY, null),
            episodeProgress = watchStateRepository.getAllEpisodeProgress(),
            activity = watchStateRepository.getDailyWatchActivity(),
            library = library,
        )
    }

    /** Shared CMP read boundary; profile mutation and Android URI permissions stay host-specific. */
    override suspend fun load(): LocalProfileData = getData()

    fun updateProfileName(name: String): String {
        val profileName = name.trim().ifBlank { defaultProfileName }
        preferences.edit().putString(PROFILE_NAME_KEY, profileName).apply()
        return profileName
    }

    fun updateProfileAvatar(uri: String) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        preferences.edit().putString(PROFILE_AVATAR_URI_KEY, uri).apply()
    }

    private companion object {
        const val PROFILE_PREFERENCES = "local_profile"
        const val PROFILE_NAME_KEY = "profile_name"
        const val PROFILE_AVATAR_URI_KEY = "profile_avatar_uri"
    }
}
