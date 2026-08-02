package org.akkirrai.beakokit.source.animepahe.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.http.ChallengeRequestExecutor

internal class AnimePaheHttpClient(
    client: HttpClient,
    sessionProvider: ChallengeSessionProvider,
) {
    private val requests = ChallengeRequestExecutor(client, sessionProvider)

    suspend fun get(
        url: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val response = requests.execute(
            url = url,
            requiredCookieNames = setOf(CLOUDFLARE_COOKIE),
        ) {
            header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            configure()
        }
        if (!response.status.isSuccess()) {
            throw SourceException(
                message = "AnimePahe returned HTTP ${response.status.value} for $url",
                statusCode = response.status.value,
                kind = when (response.status.value) {
                    401, 403 -> SourceErrorKind.AUTH
                    404 -> SourceErrorKind.NOT_FOUND
                    429 -> SourceErrorKind.RATE_LIMITED
                    in 500..599 -> SourceErrorKind.NETWORK
                    else -> SourceErrorKind.UNKNOWN
                },
            )
        }
        return response.bodyAsText()
    }

    private companion object {
        const val CLOUDFLARE_COOKIE = "cf_clearance"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
