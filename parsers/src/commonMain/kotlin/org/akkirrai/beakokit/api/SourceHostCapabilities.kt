package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable
import io.ktor.http.Url

/** Host services that an external source may request from the runtime. */
@Serializable
enum class SourceHostCapability {
    NETWORK,
    COOKIES,
    STORAGE,
    CHALLENGE,
    LOGGING,
}

/** Immutable permission declaration carried by a source package manifest. */
data class SourceHostRequirements(
    val capabilities: Set<SourceHostCapability> = emptySet(),
    val networkPolicy: SourceHostNetworkPolicy = SourceHostNetworkPolicy.EMPTY,
) {
    fun requires(capability: SourceHostCapability): Boolean = capability in capabilities
}

/** Converts an installed package manifest into the host permissions enforced by its runtime. */
fun SourceManifest.hostRequirements(): SourceHostRequirements = SourceHostRequirements(
    capabilities = hostCapabilities.toSet(),
    networkPolicy = hostNetworkPolicy.copy(allowedHosts = hostNetworkPolicy.allowedHosts.toSet()),
)

/** Manifest-declared HTTPS origins to which a source may send host HTTP requests. */
@Serializable
data class SourceHostNetworkPolicy(
    val allowedHosts: Set<String>,
) {
    init {
        require(allowedHosts.all { HOST_PATTERN.matches(it) }) {
            "Source network hosts must be lowercase host names"
        }
    }

    fun allows(url: String): Boolean = runCatching {
        val parsed = Url(url)
        parsed.protocol.name == "https" && parsed.host in allowedHosts
    }.getOrDefault(false)

    fun requireAllowed(url: String) {
        require(allows(url)) { "Source network URL is not allowed: $url" }
    }

    companion object {
        val EMPTY = SourceHostNetworkPolicy(emptySet())
        private val HOST_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
    }
}

/**
 * The runtime-facing part of the host boundary.
 *
 * Implementations must be scoped to one source and must not expose capabilities that were not
 * declared in that source's [SourceHostRequirements].
 */
interface SourceHostAccess {
    val requirements: SourceHostRequirements

    fun require(capability: SourceHostCapability) {
        if (!requirements.requires(capability)) {
            throw SourceHostCapabilityException(capability)
        }
    }
}

class SourceHostCapabilityException(
    val capability: SourceHostCapability,
) : IllegalStateException("Source host capability is not declared: ${capability.name}")
