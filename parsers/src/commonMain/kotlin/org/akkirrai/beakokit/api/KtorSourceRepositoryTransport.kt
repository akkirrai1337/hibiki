package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
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
        val response = client.get(url)
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
        val body = rawBody.decodeToString()
        return SourceRepositoryResponse(
            statusCode = response.status.value,
            body = body,
        )
    }
}
