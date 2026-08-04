package org.akkirrai.beakokit.api

/** A downloaded package whose bytes have been validated against its repository manifest. */
data class VerifiedSourcePackageDownload(
    val downloaded: DownloadedSourcePackage,
    val artifact: SourcePackageArtifact,
)

/** Connects package download and checksum validation as one host-owned operation. */
class SourcePackageDownloadService(
    private val transport: SourcePackageTransport,
    private val artifactVerifier: SourcePackageArtifactVerifier,
    private val limits: SourcePackageDownloadLimits = SourcePackageDownloadLimits(),
) {
    suspend fun download(manifest: SourceManifest): VerifiedSourcePackageDownload {
        artifactVerifier.requireManifestCompatible(manifest)
        val downloaded = transport.download(manifest.packageUrl, limits)
        require(downloaded.sizeBytes <= limits.maxArtifactSizeBytes) {
            "Downloaded package exceeds ${limits.maxArtifactSizeBytes} bytes"
        }
        val artifact = artifactVerifier.verify(manifest, downloaded)
        return VerifiedSourcePackageDownload(downloaded, artifact)
    }
}
