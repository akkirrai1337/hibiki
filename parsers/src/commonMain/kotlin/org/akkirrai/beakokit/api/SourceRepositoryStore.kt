package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/** A user-configured repository endpoint. */
@Serializable
data class SourceRepositoryEndpoint(
    val url: String,
) {
    init {
        require(url.isNotBlank()) { "Repository URL must not be blank" }
        require(url.startsWith("https://")) { "Repository URL must use HTTPS" }
    }
}

/** Platform persistence boundary for the user-configured repository list. */
interface SourceRepositoryStore {
    fun load(): List<SourceRepositoryEndpoint>

    /** Implementations must replace the persisted list atomically. */
    fun persistAtomically(repositories: List<SourceRepositoryEndpoint>)
}

/** Common repository-list policy independent of platform persistence. */
class SourceRepositoryCatalog(
    private val store: SourceRepositoryStore,
) {
    fun load(): List<SourceRepositoryEndpoint> = checked(store.load())

    fun add(endpoint: SourceRepositoryEndpoint): List<SourceRepositoryEndpoint> {
        val current = load()
        if (current.any { it.url == endpoint.url }) return current
        val next = current + endpoint
        store.persistAtomically(next)
        return next
    }

    fun remove(url: String): List<SourceRepositoryEndpoint> {
        require(url.isNotBlank()) { "Repository URL must not be blank" }
        val current = load()
        val next = current.filterNot { it.url == url }
        if (next != current) store.persistAtomically(next)
        return next
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
}
