package org.akkirrai.beakokit.extension

import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.model.AnimeSearchFilter
import org.akkirrai.beakokit.model.AnimeSearchSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A manifest is published by the source repository on its own schedule, so it can name values a
 * given build has never heard of. That must cost the value, not the whole extension.
 */
class ScriptExtensionManifestLenienceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun manifest(capabilities: String, sorts: String, filters: String, fallback: String) = """
        {
          "id": "yummy-anime",
          "name": "YummyAnime",
          "version": "1.3.1",
          "lang": "ru",
          "payload": "var Provider = {};",
          "capabilities": $capabilities,
          "supportedSorts": $sorts,
          "supportedFilters": $filters,
          "fallbackSort": "$fallback"
        }
    """.trimIndent()

    @Test
    fun `a capability this build does not know is skipped, not fatal`() {
        val decoded = json.decodeFromString(
            ScriptExtensionManifest.serializer(),
            manifest(
                capabilities = """["LATEST_RELEASES", "PLAYBACK", "TELEPATHY"]""",
                sorts = """["RELEVANCE", "ALPHABETIC_BY_MOOD"]""",
                filters = """["TYPE", "COLOUR"]""",
                fallback = "RELEVANCE",
            ),
        )

        assertEquals(setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK), decoded.capabilities)
        assertEquals(setOf(AnimeSearchSort.RELEVANCE), decoded.supportedSorts)
        assertEquals(setOf(AnimeSearchFilter.TYPE), decoded.supportedFilters)
        assertTrue(decoded.violations().isEmpty())
    }

    @Test
    fun `an unknown fallback sort becomes the one every source supports`() {
        val decoded = json.decodeFromString(
            ScriptExtensionManifest.serializer(),
            manifest(
                capabilities = """["LATEST_RELEASES", "PLAYBACK"]""",
                sorts = """["RELEVANCE"]""",
                filters = """[]""",
                fallback = "ALPHABETIC_BY_MOOD",
            ),
        )

        assertEquals(AnimeSearchSort.RELEVANCE, decoded.fallbackSort)
    }

    @Test
    fun `activity sync is a capability this build knows`() {
        val decoded = json.decodeFromString(
            ScriptExtensionManifest.serializer(),
            manifest(
                capabilities = """["LATEST_RELEASES", "PLAYBACK", "ACTIVITY_SYNC"]""",
                sorts = """["RELEVANCE"]""",
                filters = """[]""",
                fallback = "RELEVANCE",
            ),
        )

        assertTrue(SourceCapability.ACTIVITY_SYNC in decoded.capabilities)
    }
}
