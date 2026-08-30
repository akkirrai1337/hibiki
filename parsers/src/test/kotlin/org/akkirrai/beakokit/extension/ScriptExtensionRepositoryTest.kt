package org.akkirrai.beakokit.extension

import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceId
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptExtensionRepositoryTest {
    private fun tempDir(): File = Files.createTempDirectory("hibiki-script-ext-repo").toFile()

    private val manifest = ScriptExtensionManifest(
        id = "test-source",
        name = "Test Source",
        version = "1.0.0",
        lang = "en",
        payload = "var Provider = { search: function() { return []; } };",
        capabilities = setOf(SourceCapability.PLAYBACK, SourceCapability.LATEST_RELEASES),
    )

    @Test
    fun `install then loadAll exposes the extension`() {
        val repository = ScriptExtensionRepository(tempDir())
        repository.install(Json.encodeToString(ScriptExtensionManifest.serializer(), manifest))

        val result = repository.loadAll()
        assertEquals(listOf(SourceId("test-source")), result.entries.map { it.info.id })
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `uninstall removes the extension`() {
        val repository = ScriptExtensionRepository(tempDir())
        repository.install(Json.encodeToString(ScriptExtensionManifest.serializer(), manifest))
        repository.uninstall(manifest.id)

        assertTrue(repository.loadAll().entries.isEmpty())
    }

    @Test
    fun `install rejects an invalid manifest`() {
        val invalid = manifest.copy(version = "not-a-version")
        val repository = ScriptExtensionRepository(tempDir())

        assertTrue(runCatching {
            repository.install(Json.encodeToString(ScriptExtensionManifest.serializer(), invalid))
        }.isFailure)
    }

    @Test
    fun `a broken manifest file is reported as invalid instead of crashing loadAll`() {
        val dir = tempDir()
        File(dir, "broken.json").writeText("{ not json")
        val repository = ScriptExtensionRepository(dir)

        val result = repository.loadAll()
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.invalid.size)
        assertEquals("broken", result.invalid.single().id)
    }

    @Test
    fun `installedManifests reflects the current version after a reinstall`() {
        val repository = ScriptExtensionRepository(tempDir())
        repository.install(Json.encodeToString(ScriptExtensionManifest.serializer(), manifest))
        repository.install(Json.encodeToString(ScriptExtensionManifest.serializer(), manifest.copy(version = "1.1.0")))

        val installed = repository.installedManifests()
        assertEquals(listOf("test-source" to "1.1.0"), installed.map { it.id to it.version })
    }
}
