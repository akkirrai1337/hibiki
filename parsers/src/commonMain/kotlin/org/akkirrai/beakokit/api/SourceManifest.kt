package org.akkirrai.beakokit.api

import kotlinx.serialization.Serializable

/** Execution format and ABI declared by an installable source package. */
@Serializable
data class SourceRuntime(
    val id: String,
    val abi: String,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Invalid source runtime id: $id" }
        require(abi.isNotBlank()) { "Source runtime ABI must not be blank" }
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z][a-z0-9-]*")
    }
}

/** User-visible source metadata carried by an external package manifest. */
@Serializable
data class SourceManifestInfo(
    val displayName: String,
    val languages: Set<SourceLanguage>,
    val primaryLanguage: SourceLanguage,
    val website: String? = null,
    val iconUrl: String? = null,
)

/** Repository metadata used to validate a package before it can be activated. */
@Serializable
data class SourceManifest(
    val manifestFormatVersion: Int,
    val sourceId: SourceId,
    val packageVersion: String,
    val sourceInfo: SourceManifestInfo? = null,
    val apiVersion: Int,
    val hostApiVersion: Int = SourceHostApi.VERSION,
    val runtime: SourceRuntime,
    val entrypoint: String,
    val packageUrl: String,
    val sha256: String,
    val artifactSizeBytes: Long,
    val minClientVersion: Int,
    val maxClientVersion: Int? = null,
    val capabilities: Set<SourceCapability> = emptySet(),
    val hostCapabilities: Set<SourceHostCapability> = emptySet(),
    val hostNetworkPolicy: SourceHostNetworkPolicy = SourceHostNetworkPolicy.EMPTY,
    val signature: String? = null,
) {
    fun violations(
        clientVersion: Int,
        supportedApiVersion: Int,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): List<String> = buildList {
        if (manifestFormatVersion != CURRENT_FORMAT_VERSION) {
            add("Unsupported manifest format version: $manifestFormatVersion")
        }
        if (packageVersion.isBlank()) add("Package version must not be blank")
        if (apiVersion != supportedApiVersion) add("Unsupported source API version: $apiVersion")
        if (hostApiVersion != supportedHostApiVersion) {
            add("Unsupported source host API version: $hostApiVersion")
        }
        if (entrypoint.isBlank()) add("Entrypoint must not be blank")
        if (!isValidHttpsUrl(packageUrl)) add("Package URL must be a valid HTTPS URL")
        if (hostNetworkPolicy.allowedHosts.isNotEmpty() && SourceHostCapability.NETWORK !in hostCapabilities) {
            add("Network policy requires the NETWORK host capability")
        }
        if (!SHA256_PATTERN.matches(sha256)) add("SHA-256 must be 64 lowercase hexadecimal characters")
        if (artifactSizeBytes <= 0) add("Artifact size must be positive")
        if (minClientVersion < 0) add("Minimum client version must not be negative")
        if (maxClientVersion != null && maxClientVersion < minClientVersion) {
            add("Maximum client version must not be lower than minimum client version")
        }
        if (clientVersion < minClientVersion || (maxClientVersion != null && clientVersion > maxClientVersion)) {
            add("Client version is outside the package compatibility range")
        }
    }

    fun requireSourceInfo(): SourceInfo = sourceInfo?.let { metadata ->
        SourceInfo(
            id = sourceId,
            name = metadata.displayName,
            languages = metadata.languages,
            primaryLanguage = metadata.primaryLanguage,
            website = metadata.website,
            iconUrl = metadata.iconUrl,
            capabilities = capabilities,
        )
    } ?: throw SourceManifestException(listOf("Source display metadata is required for registration"))

    fun requireValid(
        clientVersion: Int,
        supportedApiVersion: Int,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ) {
        val violations = violations(clientVersion, supportedApiVersion, supportedHostApiVersion)
        if (violations.isNotEmpty()) throw SourceManifestException(violations)
    }

    /** Package manifests repeat source metadata but cannot repeat the ZIP's own checksum/size. */
    fun matchesPackageManifest(packageManifest: SourceManifest): Boolean =
        copy(
            sha256 = packageManifest.sha256,
            artifactSizeBytes = packageManifest.artifactSizeBytes,
        ) == packageManifest

    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

class SourceManifestException(
    val violations: List<String>,
) : IllegalArgumentException(
    violations.joinToString(prefix = "Invalid source manifest: ", separator = "; "),
)
