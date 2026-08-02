package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** iOS repository store backed by one replaceable JSON value. */
class IosSourceRepositoryStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val json: Json = Json { ignoreUnknownKeys = false },
) : SourceRepositoryStore {
    override fun load(): List<SourceRepositoryEndpoint> {
        val raw = defaults.stringForKey(KEY) ?: return emptyList()
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
        defaults.setObject(json.encodeToString(checked(repositories)), forKey = KEY)
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
        const val KEY = "beakokit.source_repositories"
    }
}
