package org.akkirrai.beakokit.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.CatalogCapabilities
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine

class InstalledExternalSourcePipelineTest {
    @Test
    fun installed_package_reaches_catalog_and_playback_through_the_external_registry() = runBlocking {
        val manifest = manifest()
        val packageDirectory = Files.createTempDirectory("hibiki-external-source-")
        Files.write(
            packageDirectory.resolve("manifest.json"),
            Json.encodeToString(manifest).encodeToByteArray(),
        )
        val moduleBytes = byteArrayOf(0, 97, 115, 109)
        Files.write(packageDirectory.resolve(manifest.entrypoint), moduleBytes)

        val installed = InstalledSourcePackage(
            sourceId = manifest.sourceId,
            packageVersion = manifest.packageVersion,
            packagePath = packageDirectory.toString(),
        )
        val activePackage = ActiveExternalSourcePackage(
            manifest = JvmSourcePackageManifestReader().read(installed.packagePath),
            installed = installed,
        )
        var receivedModule: ByteArray? = null
        val runtimeFactory = NativeBridgeExternalSourceRuntimeFactory(
            bridgeFactory = ExternalSourceRuntimeNativeBridgeFactory { _, _, module, _ ->
                receivedModule = module
                ExternalSourceRuntimeNativeBridge { request, _ ->
                    val decodedRequest = ExternalSourceRuntimeProtocolCodec.decodeRequest(request)
                    val payload = when (decodedRequest.operation) {
                        ExternalSourceRuntimeOperation.SEARCH ->
                            AnimeTitleRuntimePayloadCodec.encodeSearch(listOf(title("search-result")))
                        ExternalSourceRuntimeOperation.DETAILS ->
                            AnimeTitleRuntimePayloadCodec.encodeDetails(title("details-result"))
                        ExternalSourceRuntimeOperation.PLAYBACK_GROUPS ->
                            AnimeTitleRuntimePayloadCodec.encodePlaybackGroups(listOf(playbackGroup()))
                        ExternalSourceRuntimeOperation.PLAYER_LINKS ->
                            AnimeTitleRuntimePayloadCodec.encodePlayerLinks(listOf(playerLink()))
                    }
                    ExternalSourceRuntimeProtocolCodec.encodeResponse(
                        ExternalSourceRuntimeResponse(
                            requestId = decodedRequest.requestId,
                            payload = payload,
                        ),
                    )
                }
            },
            moduleReader = JvmSourcePackageModuleReader(),
            requestIdFactory = { "installed-package-request" },
        )
        val client = HttpClient(MockEngine { error("Network is not expected in this test") })
        try {
            val source = activeExternalSourceRegistry(
                packages = listOf(activePackage),
                catalogCapabilities = { CatalogCapabilities.FULL },
                runtimeFactory = runtimeFactory,
            ).create(
                id = manifest.sourceId,
                context = DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.ENGLISH),
                ),
            )

            assertEquals("search-result", source.search(AnimeSearchRequest(query = "frieren")).single().id)
            assertEquals("details-result", source.getById("title-1").id)
            val playbackSource = source as PlaybackSource
            val group = playbackSource.getPlaybackGroups(title("details-result")).single()
            assertEquals("group-1", group.id)
            val episode = group.episodes.single()
            assertEquals("episode-1", episode.id)
            assertEquals("https://video.example/episode-1.m3u8", playbackSource
                .getPlayerLinks(title("details-result"), group, episode).single().url)
            assertContentEquals(moduleBytes, receivedModule)
        } finally {
            client.close()
            Files.deleteIfExists(packageDirectory.resolve(manifest.entrypoint))
            Files.deleteIfExists(packageDirectory.resolve("manifest.json"))
            Files.deleteIfExists(packageDirectory)
        }
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("installed-external-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "Installed external source",
            languages = setOf(SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.ENGLISH,
        ),
        apiVersion = SourceApi.VERSION,
        hostApiVersion = SourceHostApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 4,
        minClientVersion = 0,
        capabilities = setOf(SourceCapability.PLAYBACK),
    )

    private fun playbackGroup() = PlaybackGroup(
        id = "group-1",
        title = "Dub",
        episodes = listOf(Episode("episode-1", 1.0, "Episode 1")),
    )

    private fun playerLink() = PlayerLink(
        url = "https://video.example/episode-1.m3u8",
        type = PlayerType.DIRECT_HLS,
        quality = "1080p",
    )

    private fun title(id: String) = AnimeTitle(
        id = id,
        russianName = null,
        englishName = "Test title",
        originalName = "Test title",
        japaneseName = null,
        synonyms = emptyList(),
        year = null,
        type = null,
        episodeCount = null,
        posterUrl = null,
        status = null,
        description = null,
    )
}
