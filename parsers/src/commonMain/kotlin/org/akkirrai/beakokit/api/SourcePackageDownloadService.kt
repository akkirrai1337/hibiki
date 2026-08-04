package org.akkirrai.beakokit.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
        val downloaded = try {
            withTimeout(limits.timeoutMillis) {
                transport.download(manifest.packageUrl, limits)
            }
        } catch (error: TimeoutCancellationException) {
            throw SourcePackageDownloadException(
                message = "Package download timed out after ${limits.timeoutMillis} ms",
                cause = error,
                kind = SourceErrorKind.UNAVAILABLE,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourcePackageDownloadException) {
            throw error
        } catch (error: Exception) {
            throw SourcePackageDownloadException(
                message = "Package download failed",
                cause = error,
                kind = SourceErrorKind.NETWORK,
            )
        }
        if (downloaded.sizeBytes > limits.maxArtifactSizeBytes) {
            throw SourcePackageDownloadException(
                message = "Downloaded package exceeds ${limits.maxArtifactSizeBytes} bytes",
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }
        val artifact = artifactVerifier.verify(manifest, downloaded)
        return VerifiedSourcePackageDownload(downloaded, artifact)
    }
}
