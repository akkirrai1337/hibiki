package org.akkirrai.beakokit.api

/** Platform implementation of SHA-256 for downloaded package bytes. */
fun interface SourcePackageSha256 {
    fun digest(bytes: ByteArray): String
}

/** Calculates artifact metadata and applies the existing manifest/trust validation. */
class SourcePackageArtifactVerifier(
    private val validator: SourcePackageValidator,
    private val sha256: SourcePackageSha256,
) {
    fun requireManifestCompatible(manifest: SourceManifest) =
        validator.requireManifestCompatible(manifest)

    fun verify(
        manifest: SourceManifest,
        downloaded: DownloadedSourcePackage,
    ): SourcePackageArtifact {
        val artifact = SourcePackageArtifact(
            sizeBytes = downloaded.sizeBytes,
            sha256 = sha256.digest(downloaded.bytes),
        )
        validator.requireValid(manifest, artifact)
        return artifact
    }
}
