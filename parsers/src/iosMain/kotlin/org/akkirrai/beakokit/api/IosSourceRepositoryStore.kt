package org.akkirrai.beakokit.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** iOS repository store backed by one replaceable JSON value. */
class IosSourceRepositoryStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val maxRepositoryBytes: Long = DEFAULT_MAX_REPOSITORY_BYTES,
) : SourceRepositoryStore {
    init {
        require(maxRepositoryBytes > 0) { "Maximum repository state size must be positive" }
    }

    override fun load(): List<SourceRepositoryEndpoint> {
        val stored = defaults.objectForKey(KEY) ?: return emptyList()
        val raw = stored as? String ?: throw SourceRepositoryStateException(
            "Source repository list is corrupted: expected a string value",
        )
        return try {
            if (raw.encodeToByteArray().size.toLong() > maxRepositoryBytes) {
                throw SourceRepositoryStateException(
                    "Source repository state exceeds $maxRepositoryBytes bytes",
                )
            }
            checked(json.decodeFromString(raw))
        } catch (error: SourceRepositoryStateException) {
            throw error
        } catch (error: Exception) {
            throw SourceRepositoryStateException(
                message = "Source repository list is corrupted",
                cause = error,
            )
        }
    }

    override fun persistAtomically(repositories: List<SourceRepositoryEndpoint>) {
        val bytes = json.encodeToString(checked(repositories)).encodeToByteArray()
        require(bytes.size.toLong() <= maxRepositoryBytes) {
            "Source repository state exceeds $maxRepositoryBytes bytes"
        }
        defaults.setObject(bytes.decodeToString(), forKey = KEY)
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
        const val DEFAULT_MAX_REPOSITORY_BYTES: Long = 2L * 1024L * 1024L
    }
}
