package org.akkirrai.beakokit.http

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import org.akkirrai.beakokit.api.ChallengeSession
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.ChallengeSessionRequest

/** Executes a request again with a host-provided browser session when a site returns a challenge. */
class ChallengeRequestExecutor(
    private val client: HttpClient,
    private val sessionProvider: ChallengeSessionProvider,
) {
    suspend fun execute(
        url: String,
        requiredCookieNames: Set<String>,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val request = ChallengeSessionRequest(
            url = url,
            requiredCookieNames = requiredCookieNames,
        )
        val firstResponse = send(url, session = null, configure)
        if (!firstResponse.isBrowserChallenge()) return firstResponse
        firstResponse.bodyAsChannel().cancel(null)

        val cachedOrNewSession = sessionProvider.acquire(request)
        val sessionResponse = send(url, cachedOrNewSession, configure)
        if (!sessionResponse.isBrowserChallenge()) return sessionResponse
        sessionResponse.bodyAsChannel().cancel(null)

        val refreshedSession = sessionProvider.acquire(request.copy(forceRefresh = true))
        return send(url, refreshedSession, configure)
    }

    private suspend fun send(
        url: String,
        session: ChallengeSession?,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse = client.request(url) {
        configure()
        session?.headers()?.forEach { (name, value) ->
            headers.remove(name)
            headers.append(name, value)
        }
    }
}

private fun HttpResponse.isBrowserChallenge(): Boolean =
    status == HttpStatusCode.Forbidden ||
        headers["cf-mitigated"].equals("challenge", ignoreCase = true)
