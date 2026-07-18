package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SourceContextTest {
    @Test
    fun `default context requires a preferred language`() {
        val client = HttpClient(MockEngine { error("Network must not be called") })

        try {
            assertFailsWith<IllegalArgumentException> {
                DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = emptyList(),
                )
            }
        } finally {
            client.close()
        }
    }
}
