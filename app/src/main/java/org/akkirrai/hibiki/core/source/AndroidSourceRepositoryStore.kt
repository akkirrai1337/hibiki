package org.akkirrai.hibiki.core.source

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceRepositoryEndpoint
import org.akkirrai.beakokit.api.SourceRepositoryStateException
import org.akkirrai.beakokit.api.SourceRepositoryStore

/** Android repository store backed by one synchronously replaceable preference value. */
class AndroidSourceRepositoryStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
) : SourceRepositoryStore {
    override fun load(): List<SourceRepositoryEndpoint> {
        val raw = preferences.getString(REPOSITORIES_KEY, null) ?: return emptyList()
        return try {
            checked(json.decodeFromString(raw))
        } catch (error: Exception) {
            throw SourceRepositoryStateException(
                message = "Source repository list is corrupted",
                cause = error,
            )
        }
    }

    override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
        val checkedRepositories = checked(repositories)
        check(
            preferences.edit()
                .putString(REPOSITORIES_KEY, json.encodeToString(checkedRepositories))
                .commit(),
        ) { "Could not persist source repository list" }
    }

    private fun checked(repositories: List<SourceRepositoryEndpoint>): List<SourceRepositoryEndpoint> {
        val urls = mutableSetOf<String>()
        repositories.forEach { repository ->
            require(urls.add(repository.url)) {
                "Duplicate repository URL: ${repository.url}"
            }
        }
        return repositories.toList()
    }

    private companion object {
        const val PREFERENCES_NAME = "beakokit_external_sources"
        const val REPOSITORIES_KEY = "repositories"
    }
}
