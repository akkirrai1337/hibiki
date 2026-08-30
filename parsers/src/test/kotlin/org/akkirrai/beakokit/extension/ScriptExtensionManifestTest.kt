package org.akkirrai.beakokit.extension

import org.akkirrai.beakokit.api.SourceCapability
import kotlin.test.Test
import kotlin.test.assertTrue

class ScriptExtensionManifestTest {
    private val valid = ScriptExtensionManifest(
        id = "test-source",
        name = "Test Source",
        version = "1.0.0",
        lang = "en",
        payload = "var Provider = {};",
        capabilities = setOf(SourceCapability.PLAYBACK, SourceCapability.LATEST_RELEASES),
    )

    @Test
    fun `a well-formed manifest has no violations`() {
        assertTrue(valid.violations().isEmpty())
    }

    @Test
    fun `rejects an invalid id`() {
        assertTrue(valid.copy(id = "Not A Slug!").violations().isNotEmpty())
    }

    @Test
    fun `rejects a blank name`() {
        assertTrue(valid.copy(name = "   ").violations().isNotEmpty())
    }

    @Test
    fun `rejects a non-semver version`() {
        assertTrue(valid.copy(version = "latest").violations().isNotEmpty())
    }

    @Test
    fun `rejects an invalid language tag`() {
        assertTrue(valid.copy(lang = "???").violations().isNotEmpty())
    }

    @Test
    fun `rejects a blank payload`() {
        assertTrue(valid.copy(payload = "").violations().isNotEmpty())
    }

    @Test
    fun `rejects a manifest missing the currently-required capabilities`() {
        assertTrue(valid.copy(capabilities = setOf(SourceCapability.PLAYBACK)).violations().isNotEmpty())
        assertTrue(valid.copy(capabilities = emptySet()).violations().isNotEmpty())
    }
}
