package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.extension.ScriptExtensionManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AnimeSourceRegistryTest {
    private val manifest = ScriptExtensionManifest(
        id = "test-source",
        name = "Test Source",
        version = "1.0.0",
        lang = "en",
        payload = "var Provider = { search: function() { return []; }, latest: function() { return []; }, getById: function() { throw new Error('n/a'); }, getPlaybackGroups: function() { return []; }, getPlayerLinks: function() { return []; }, getSettings: function() { return {}; } };",
        capabilities = setOf(SourceCapability.PLAYBACK, SourceCapability.LATEST_RELEASES),
    )

    @Test
    fun `no sources are registered until extensions are installed`() {
        val extensionsDir = Files.createTempDirectory("hibiki-registry-empty").toFile()

        AnimeSourceRegistry.initialize(extensionsDir)

        assertTrue(AnimeSourceRegistry.sources.isEmpty())
        assertTrue(AnimeSourceRegistry.catalog.sources.isEmpty())
        assertTrue(AnimeSourceRegistry.invalidScriptExtensions().isEmpty())
    }

    @Test
    fun `installing a script extension registers it and uninstalling removes it`() {
        val extensionsDir = Files.createTempDirectory("hibiki-registry-install").toFile()
        AnimeSourceRegistry.initialize(extensionsDir)

        AnimeSourceRegistry.installScriptExtension(
            Json.encodeToString(ScriptExtensionManifest.serializer(), manifest),
            originRepositoryUrl = "",
        )

        val descriptor = AnimeSourceRegistry.descriptor(SourceId("test-source"))
        assertEquals("Test Source", descriptor.name)
        assertTrue(descriptor.supportsPlayback)
        assertEquals(
            setOf(SourceCapability.LATEST_RELEASES, SourceCapability.PLAYBACK),
            AnimeSourceRegistry.sources.single().info.capabilities,
        )
        assertTrue(AnimeSourceRegistry.catalog.sources.any { it.id == SourceId("test-source") })
        assertEquals(mapOf("test-source" to "1.0.0"), AnimeSourceRegistry.installedScriptExtensionVersions())

        AnimeSourceRegistry.uninstallScriptExtension(SourceId("test-source"))

        assertTrue(AnimeSourceRegistry.sources.isEmpty())
        assertTrue(AnimeSourceRegistry.descriptorOrNull(SourceId("test-source")) == null)
    }

    @Test
    fun `an invalid manifest file is surfaced instead of crashing`() {
        val extensionsDir = Files.createTempDirectory("hibiki-registry-invalid").toFile()
        java.io.File(extensionsDir, "broken.json").writeText("{ not json")

        AnimeSourceRegistry.initialize(extensionsDir)

        assertTrue(AnimeSourceRegistry.sources.isEmpty())
        assertEquals(1, AnimeSourceRegistry.invalidScriptExtensions().size)
    }
}
