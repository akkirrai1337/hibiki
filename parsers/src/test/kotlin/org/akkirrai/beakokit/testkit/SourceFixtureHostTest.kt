package org.akkirrai.beakokit.testkit

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SourceFixtureHostTest {
    @Test
    fun `loads fixture body from test resources`() {
        val body = FixtureResources.read("/beakokit/yummy/empty-search.json")

        assertContains(body, "\"response\"")
    }

    @Test
    fun `matches declared JSON route and records the request`() = runBlocking {
        SourceFixtureHost(
            routes = listOf(
                FixtureRoute(
                    path = "/catalog",
                    query = mapOf("page" to "2"),
                    body = """{"items":[]}""",
                ),
            ),
        ).use { host ->
            val response = host.httpClient.get("https://source.test/catalog?page=2") {
                header("X-Fixture", "recorded")
            }

            assertEquals("""{"items":[]}""", response.bodyAsText())
            assertEquals("/catalog", host.requests.single().url.encodedPath)
            assertEquals("recorded", host.requests.single().headers["X-Fixture"])
        }
    }
}
