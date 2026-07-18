package org.akkirrai.beakokit.source.yummy

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.MapSourceConfig
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyAnimeSourceTest {
    @Test
    fun `source owns identity and consumes host language and secret`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/anime", request.url.encodedPath)
            assertEquals("en", request.headers["Lang"])
            assertEquals("application-secret", request.headers["X-Application"])
            respond(
                content = """{"response":[]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json() }
        }

        try {
            val source = YummyAnimeSource(
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                    config = MapSourceConfig(
                        values = mapOf(YummyAnimeSource.BASE_URL_KEY to "https://yummy.test"),
                        secrets = mapOf(
                            YummyAnimeSource.APPLICATION_TOKEN_KEY to "application-secret",
                        ),
                    ),
                ),
            )

            assertEquals(SourceId("yummy-anime"), source.info.id)
            assertEquals("YummyAnime", source.name)
            assertEquals(emptyList(), source.search("frieren"))
        } finally {
            client.close()
        }
    }
}
