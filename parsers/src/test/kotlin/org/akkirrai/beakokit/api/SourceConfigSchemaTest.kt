package org.akkirrai.beakokit.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceConfigSchemaTest {
    private val schema = SourceConfigSchema(
        listOf(
            SourceConfigField("base_url", SourceConfigValueKind.HTTPS_URL, required = true),
            SourceConfigField("application_token", SourceConfigValueKind.SECRET, required = true),
        ),
    )

    @Test
    fun `schema reports missing required values and insecure URLs`() {
        val violations = schema.violations(
            MapSourceConfig(values = mapOf("base_url" to "http://example.com")),
        )

        assertEquals(
            listOf(
                "Config base_url must use an HTTPS URL",
                "Missing required secret config: application_token",
            ),
            violations,
        )
    }

    @Test
    fun `secret fields never read regular values`() {
        val error = assertFailsWith<SourceConfigException> {
            schema.requireValid(
                MapSourceConfig(
                    values = mapOf(
                        "base_url" to "https://example.com",
                        "application_token" to "not-a-secret",
                    ),
                ),
            )
        }

        assertEquals(listOf("Missing required secret config: application_token"), error.violations)
    }

    @Test
    fun `schema rejects duplicate field keys`() {
        assertFailsWith<IllegalArgumentException> {
            SourceConfigSchema(
                listOf(
                    SourceConfigField("base_url", SourceConfigValueKind.HTTPS_URL),
                    SourceConfigField("base_url", SourceConfigValueKind.TEXT),
                ),
            )
        }
    }
}
