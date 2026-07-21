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

    @Test
    fun `schema validates comma separated HTTPS URL lists`() {
        val schema = SourceConfigSchema(
            listOf(SourceConfigField("mirrors", SourceConfigValueKind.HTTPS_URL_LIST)),
        )

        assertEquals(
            emptyList(),
            schema.violations(MapSourceConfig(values = mapOf("mirrors" to "https://one.example, https://two.example"))),
        )
        assertEquals(
            listOf("Config mirrors must be a comma-separated list of HTTPS URLs"),
            schema.violations(MapSourceConfig(values = mapOf("mirrors" to "https://one.example,,http://two.example"))),
        )
    }

    @Test
    fun `schema validates closed value sets without applying them to secrets`() {
        val schema = SourceConfigSchema(
            listOf(
                SourceConfigField(
                    key = "api_mode",
                    kind = SourceConfigValueKind.TEXT,
                    allowedValues = setOf("stable", "preview"),
                ),
            ),
        )

        assertEquals(
            listOf("Config api_mode must be one of: preview, stable"),
            schema.violations(MapSourceConfig(values = mapOf("api_mode" to "legacy"))),
        )
        assertFailsWith<IllegalArgumentException> {
            SourceConfigField(
                key = "token",
                kind = SourceConfigValueKind.SECRET,
                allowedValues = setOf("not-secret"),
            )
        }
    }
}
