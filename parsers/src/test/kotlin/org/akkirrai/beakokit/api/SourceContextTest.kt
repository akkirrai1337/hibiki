package org.akkirrai.beakokit.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun `config keeps regular values and secrets in separate channels`() {
        val config = MapSourceConfig(
            values = mapOf("base_url" to "https://source.test"),
            secrets = mapOf("token" to "secret-value"),
        )

        assertEquals("https://source.test", config.value("base_url"))
        assertEquals("secret-value", config.secret("token"))
        assertNull(config.value("token"))
        assertNull(config.secret("base_url"))
    }
}
