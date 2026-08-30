package org.akkirrai.beakokit.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.api.context.MapSourceConfig
import org.akkirrai.beakokit.api.context.SourceContext
import java.util.Collections

data class FixtureRoute(
    val path: String,
    val body: String,
    val method: HttpMethod = HttpMethod.Get,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val query: Map<String, String> = emptyMap(),
    val contentType: ContentType = ContentType.Application.Json,
    /** Extra response headers beyond Content-Type - e.g. Set-Cookie for a cookie-forwarding test. */
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    init {
        require(path.startsWith('/')) { "Fixture route path must start with '/': $path" }
    }

    internal fun matches(request: FixtureRequest): Boolean =
        method == request.method &&
            path == request.url.encodedPath &&
            query.all { (name, value) -> request.url.parameters[name] == value }

    companion object {
        fun fromResource(
            path: String,
            resource: String,
            method: HttpMethod = HttpMethod.Get,
            status: HttpStatusCode = HttpStatusCode.OK,
            query: Map<String, String> = emptyMap(),
            contentType: ContentType = ContentType.Application.Json,
            extraHeaders: Map<String, String> = emptyMap(),
        ): FixtureRoute = FixtureRoute(
            path = path,
            body = FixtureResources.read(resource),
            method = method,
            status = status,
            query = query,
            contentType = contentType,
            extraHeaders = extraHeaders,
        )
    }
}

@Deprecated("Use FixtureRoute", ReplaceWith("FixtureRoute"))
typealias JsonFixtureRoute = FixtureRoute

data class FixtureRequest(
    val method: HttpMethod,
    val url: Url,
    val headers: Headers,
    val body: String = "",
)

/**
 * A reusable host for source contract and fixture tests.
 *
 * It supplies a JSON-enabled Ktor client, records every request and rejects any request that has
 * no declared route. The same route may be used repeatedly.
 */
class SourceFixtureHost(
    routes: List<FixtureRoute> = emptyList(),
    preferredLanguages: List<SourceLanguage> = listOf(SourceLanguage.ENGLISH),
    values: Map<String, String> = emptyMap(),
    secrets: Map<String, String> = emptyMap(),
    challengeSessionProvider: ChallengeSessionProvider = ChallengeSessionProvider.UNSUPPORTED,
) : AutoCloseable {
    private val fixtureRoutes = routes.toList()
    private val recordedRequests = Collections.synchronizedList(mutableListOf<FixtureRequest>())

    val requests: List<FixtureRequest>
        get() = synchronized(recordedRequests) { recordedRequests.toList() }

    val httpClient = HttpClient(MockEngine { requestData ->
        val request = FixtureRequest(
            method = requestData.method,
            url = requestData.url,
            headers = requestData.headers,
            body = requestData.body.toByteArray().decodeToString(),
        )
        recordedRequests += request
        val route = fixtureRoutes.firstOrNull { it.matches(request) }
            ?: error("Unexpected fixture request: ${request.method.value} ${request.url}")
        respond(
            content = route.body,
            status = route.status,
            headers = Headers.build {
                append(HttpHeaders.ContentType, route.contentType.toString())
                route.extraHeaders.forEach { (name, value) -> append(name, value) }
            },
        )
    }) {
        install(ContentNegotiation) { json() }
    }

    val context: SourceContext = DefaultSourceContext(
        httpClient = httpClient,
        preferredLanguages = preferredLanguages,
        config = MapSourceConfig(values = values, secrets = secrets),
        challengeSessionProvider = challengeSessionProvider,
    )

    override fun close() {
        httpClient.close()
    }
}

object FixtureResources {
    fun read(path: String): String {
        val normalizedPath = path.trim().removePrefix("/")
        require(normalizedPath.isNotBlank()) { "Fixture resource path must not be blank" }
        val stream = openStream(normalizedPath)
            ?: error("Fixture resource not found: $normalizedPath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun exists(path: String): Boolean {
        val normalizedPath = path.trim().removePrefix("/")
        if (normalizedPath.isBlank()) return false
        return openStream(normalizedPath)?.use { true } ?: false
    }

    private fun openStream(normalizedPath: String) = listOfNotNull(
        Thread.currentThread().contextClassLoader,
        FixtureResources::class.java.classLoader,
    ).distinct().firstNotNullOfOrNull { it.getResourceAsStream(normalizedPath) }
}
