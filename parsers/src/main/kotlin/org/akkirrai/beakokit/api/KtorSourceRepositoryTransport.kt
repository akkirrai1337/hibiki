package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining

/** Ktor adapter for hosts that already own and configure an HttpClient instance. */
class KtorSourceRepositoryTransport(
    private val client: HttpClient,
) : SourceRepositoryTransport {
    override suspend fun get(
        url: String,
        limits: SourceRepositoryLoadLimits,
    ): SourceRepositoryResponse {
        // Repository indexes are mutable metadata. A manual refresh must not
        // be satisfied from an intermediate HTTP/CDN cache after a publisher
        // has released a new package version.
        val response = client.get(url) {
            header(HttpHeaders.CacheControl, "no-cache, no-store, max-age=0")
            header(HttpHeaders.Pragma, "no-cache")
        }
        if (response.status.value !in 200..299) {
            response.bodyAsChannel().cancel(null)
            return SourceRepositoryResponse(
                statusCode = response.status.value,
                body = "",
            )
        }
        val bytes = response.bodyAsChannel()
            .readRemaining(limits.maxResponseBytes + 1)
        val rawBody = bytes.readBytes()
        if (rawBody.size.toLong() > limits.maxResponseBytes) {
            throw SourceRepositoryLoadException(
                message = "Repository response exceeds ${limits.maxResponseBytes} bytes",
                kind = SourceErrorKind.UNAVAILABLE,
            )
        }
        val body = try {
            rawBody.decodeToString(throwOnInvalidSequence = true)
        } catch (error: Exception) {
            throw SourceRepositoryLoadException(
                message = "Repository response is not valid UTF-8",
                cause = error,
                kind = SourceErrorKind.PARSE,
                code = SourceErrorCode.INVALID_RESPONSE,
            )
        }
        return SourceRepositoryResponse(
            statusCode = response.status.value,
            body = body,
        )
    }
}
