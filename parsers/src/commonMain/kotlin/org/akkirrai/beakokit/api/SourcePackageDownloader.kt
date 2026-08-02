package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable

data class SourcePackageDownloadLimits(
    val maxArtifactSizeBytes: Long = SourcePackageValidator.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
) {
    init {
        require(maxArtifactSizeBytes > 0) { "Maximum artifact size must be positive" }
        require(maxArtifactSizeBytes < Int.MAX_VALUE) {
            "Maximum artifact size must fit in a platform byte array"
        }
    }
}

data class DownloadedSourcePackage(
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

/** Host-owned binary transport used before package checksum and archive validation. */
fun interface SourcePackageTransport {
    suspend fun download(
        url: String,
        limits: SourcePackageDownloadLimits,
    ): DownloadedSourcePackage
}

class KtorSourcePackageTransport(
    private val client: HttpClient,
) : SourcePackageTransport {
    override suspend fun download(
        url: String,
        limits: SourcePackageDownloadLimits,
    ): DownloadedSourcePackage {
        if (!url.startsWith("https://")) {
            throw SourcePackageDownloadException(
                message = "Package URL must use HTTPS",
                kind = SourceErrorKind.PARSE,
                code = SourceErrorCode.INVALID_REQUEST,
            )
        }
        val response = client.get(url)
        if (response.status.value !in 200..299) {
            throw SourcePackageDownloadException(
                message = "Package request returned HTTP ${response.status.value}",
                statusCode = response.status.value,
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }

        val maxBytes = limits.maxArtifactSizeBytes.toInt()
        val bytes = ByteArray(maxBytes)
        var size = 0
        val channel = response.bodyAsChannel()
        while (size < bytes.size) {
            val read = channel.readAvailable(bytes, size, bytes.size - size)
            if (read == -1) break
            size += read
        }
        if (size == bytes.size && channel.readAvailable(ByteArray(1), 0, 1) > 0) {
            throw SourcePackageDownloadException(
                message = "Downloaded package exceeds ${limits.maxArtifactSizeBytes} bytes",
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }
        return DownloadedSourcePackage(bytes.copyOf(size))
    }
}

class SourcePackageDownloadException(
    message: String,
    statusCode: Int? = null,
    cause: Throwable? = null,
    kind: SourceErrorKind,
    code: SourceErrorCode = kind.defaultCode,
) : SourceException(message, statusCode, cause, kind, code)
