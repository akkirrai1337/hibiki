package org.akkirrai.beakokit.api

/** Metadata calculated from the downloaded package before unpacking it. */
data class SourcePackageArtifact(
    val sizeBytes: Long,
    val sha256: String,
)

/** Performs the one-time package checks required before installation. */
class SourcePackageValidator(
    private val clientVersion: Int,
    private val supportedApiVersion: Int = SourceApi.VERSION,
    private val supportedHostApiVersion: Int = SourceHostApi.VERSION,
    private val runtimeSupportPolicy: SourceRuntimeSupportPolicy = SourceRuntimeSupportPolicy.WASMTIME_WASI,
    private val maxArtifactSizeBytes: Long = DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
    private val trustPolicy: SourcePackageTrustPolicy = SourcePackageTrustPolicy.TRUSTED_CATALOG,
) {
    init {
        require(clientVersion >= 0) { "Client version must not be negative" }
        require(maxArtifactSizeBytes > 0) { "Maximum artifact size must be positive" }
    }

    fun violations(
        manifest: SourceManifest,
        artifact: SourcePackageArtifact,
    ): List<String> = buildList {
        addAll(manifest.violations(clientVersion, supportedApiVersion, supportedHostApiVersion))
        if (!runtimeSupportPolicy.supports(manifest.runtime)) {
            add("Unsupported source runtime: ${manifest.runtime.id}/${manifest.runtime.abi}")
        }
        if (artifact.sizeBytes <= 0) add("Downloaded artifact size must be positive")
        if (artifact.sizeBytes > maxArtifactSizeBytes) {
            add("Downloaded artifact exceeds the maximum allowed size")
        }
        if (artifact.sizeBytes != manifest.artifactSizeBytes) {
            add("Downloaded artifact size does not match the manifest")
        }
        if (artifact.sha256 != manifest.sha256) {
            add("Downloaded artifact SHA-256 does not match the manifest")
        }
        trustPolicy.violation(manifest, artifact)?.let(::add)
    }

    fun requireValid(
        manifest: SourceManifest,
        artifact: SourcePackageArtifact,
    ) {
        val violations = violations(manifest, artifact)
        if (violations.isNotEmpty()) throw SourcePackageValidationException(violations)
    }

    fun requireManifestCompatible(manifest: SourceManifest) {
        val violations = manifest.violations(clientVersion, supportedApiVersion, supportedHostApiVersion)
        if (violations.isNotEmpty()) throw SourcePackageValidationException(violations)
    }

    companion object {
        const val DEFAULT_MAX_ARTIFACT_SIZE_BYTES: Long = 64L * 1024L * 1024L
    }
}

class SourcePackageValidationException(
    val violations: List<String>,
) : IllegalArgumentException(
    violations.joinToString(prefix = "Invalid source package: ", separator = "; "),
)
