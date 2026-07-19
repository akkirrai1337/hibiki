package org.akkirrai.beakokit.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceContext
import org.akkirrai.beakokit.api.SourceLanguage

data class JsonFixtureRoute(
    val path: String,
    val body: String,
    val method: HttpMethod = HttpMethod.Get,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val query: Map<String, String> = emptyMap(),
) {
    init {
        require(path.startsWith('/')) { "Fixture route path must start with '/': $path" }
    }

    internal fun matches(request: FixtureRequest): Boolean =
        method == request.method &&
            path == request.url.encodedPath &&
            query.all { (name, value) -> request.url.parameters[name] == value }
}

data class FixtureRequest(
    val method: HttpMethod,
    val url: Url,
    val headers: Headers,
)

/**
 * A reusable host for source contract and fixture tests.
 *
 * It supplies a JSON-enabled Ktor client, records every request and rejects any request that has
 * no declared route. The same route may be used repeatedly.
 */
class SourceFixtureHost(
    routes: List<JsonFixtureRoute> = emptyList(),
    preferredLanguages: List<SourceLanguage> = listOf(SourceLanguage.ENGLISH),
    values: Map<String, String> = emptyMap(),
    secrets: Map<String, String> = emptyMap(),
) : AutoCloseable {
    private val fixtureRoutes = routes.toList()
    private val recordedRequests = mutableListOf<FixtureRequest>()

    val requests: List<FixtureRequest>
        get() = recordedRequests.toList()

    val httpClient = HttpClient(MockEngine { requestData ->
        val request = FixtureRequest(
            method = requestData.method,
            url = requestData.url,
            headers = requestData.headers,
        )
        recordedRequests += request
        val route = fixtureRoutes.firstOrNull { it.matches(request) }
            ?: error("Unexpected fixture request: ${request.method.value} ${request.url}")
        respond(
            content = route.body,
            status = route.status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }) {
        install(ContentNegotiation) { json() }
    }

    val context: SourceContext = DefaultSourceContext(
        httpClient = httpClient,
        preferredLanguages = preferredLanguages,
        config = MapSourceConfig(values = values, secrets = secrets),
    )

    override fun close() {
        httpClient.close()
    }
}
