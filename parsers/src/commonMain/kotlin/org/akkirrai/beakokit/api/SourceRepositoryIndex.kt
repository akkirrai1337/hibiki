package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/**
 * Repository-owned index of the source packages that can be offered to the user.
 *
 * The index is metadata only: package downloads, checksum verification, and activation remain
 * separate operations owned by the host.
 */
@Serializable
data class SourceRepositoryIndex(
    val apiVersion: Int,
    val sources: List<SourceManifest>,
) {
    fun violations(
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): List<String> = buildList {
        if (apiVersion != CURRENT_API_VERSION) {
            add("Unsupported source repository API version: $apiVersion")
        }
        if (sources.isEmpty()) add("Source repository must contain at least one source")

        val sourceIds = mutableSetOf<SourceId>()
        sources.forEach { manifest ->
            if (!sourceIds.add(manifest.sourceId)) {
                add("Duplicate source ID in repository: ${manifest.sourceId}")
            }
            if (manifest.sourceInfo == null) {
                add("Source metadata is required for repository source: ${manifest.sourceId}")
            } else {
                runCatching { manifest.requireSourceInfo() }
                    .onFailure { error ->
                        add(
                            "${manifest.sourceId}: Invalid source metadata: " +
                                (error.message ?: "unknown metadata error"),
                        )
                    }
            }
            manifest.violations(
                clientVersion = clientVersion,
                supportedApiVersion = supportedSourceApiVersion,
                supportedHostApiVersion = supportedHostApiVersion,
            ).forEach { violation ->
                add("${manifest.sourceId}: $violation")
            }
        }
    }

    fun requireValid(
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ) {
        val violations = violations(clientVersion, supportedSourceApiVersion, supportedHostApiVersion)
        if (violations.isNotEmpty()) throw SourceRepositoryIndexException(violations)
    }

    fun find(sourceId: SourceId): SourceManifest? = sources.singleOrNull { it.sourceId == sourceId }

    companion object {
        const val CURRENT_API_VERSION: Int = 1
    }
}

class SourceRepositoryIndexException(
    val violations: List<String>,
) : IllegalArgumentException(
    violations.joinToString(prefix = "Invalid source repository index: ", separator = "; "),
)
