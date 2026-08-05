package org.akkirrai.hibiki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.ActiveExternalSourcePackage
import org.akkirrai.beakokit.api.DefaultSourceContext
import org.akkirrai.beakokit.api.ExternalSourcePlaybackRuntime
import org.akkirrai.beakokit.api.InstalledSourcePackage
import org.akkirrai.beakokit.api.JvmSourcePackageManifestReader
import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceApi
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.SourceManifest
import org.akkirrai.beakokit.api.SourceManifestInfo
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.SourceRuntime
import org.akkirrai.beakokit.api.SourceHostCapability
import org.akkirrai.beakokit.api.SourceHostNetworkPolicy
import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.hibiki.shared.source.createAndroidExternalSourceRuntimeFactory
import org.akkirrai.hibiki.shared.source.validateAndroidExternalSourceRuntime
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.io.path.writeBytes
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ExternalSourcePackageValidationInstrumentedTest {
    @Test
    fun installedWasmPackageExecutesDetailsThroughAndroidRuntime() = runBlocking {
        val packageDirectory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .toPath()
            .resolve("external-source-details-${System.nanoTime()}")
        val module = packageDirectory.resolve("source.wasm")
        packageDirectory.toFile().mkdirs()
        val client = HttpClient(MockEngine {
            respond(
                content = "{\"data\":${releaseJson()}}",
                status = HttpStatusCode.OK,
            )
        })
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.assets.open("aniliberty-source.wasm").use { input ->
                module.writeBytes(input.readBytes())
            }
            val manifest = manifest()
            packageDirectory.resolve("manifest.json").writeText(Json.encodeToString(manifest))
            val installedManifest = JvmSourcePackageManifestReader().read(packageDirectory.toString())
            val runtime = createAndroidExternalSourceRuntimeFactory(context).create(
                sourcePackage = ActiveExternalSourcePackage(
                    manifest = installedManifest,
                    installed = InstalledSourcePackage(
                        sourceId = installedManifest.sourceId,
                        packageVersion = installedManifest.packageVersion,
                        packagePath = packageDirectory.toString(),
                    ),
                ),
                context = DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                ),
            )

            val search = runtime.search(AnimeSearchRequest(query = "naruto", limit = 20))
            assertTrue(search.any { it.id == "413" })

            val title = runtime.details("413")

            assertTrue(title.id == "413")
            assertTrue(title.originalName == "Naruto")

            val playbackRuntime = runtime as ExternalSourcePlaybackRuntime
            val group = playbackRuntime.playbackGroups(title).single()
            assertTrue(group.episodes.any { it.id == "episode-1" })
            val links = playbackRuntime.playerLinks(title, group, group.episodes.single())
            assertTrue(links.any { it.url.endsWith("720.m3u8") })
        } finally {
            client.close()
            module.deleteIfExists()
            packageDirectory.resolve("manifest.json").deleteIfExists()
            packageDirectory.toFile().delete()
        }
    }

    @Test
    fun installedWasmPackageCreatesAndroidExternalRuntime() = runBlocking {
        val packageDirectory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .toPath()
            .resolve("external-source-runtime-${System.nanoTime()}")
        val module = packageDirectory.resolve("source.wasm")
        packageDirectory.toFile().mkdirs()
        val client = HttpClient(MockEngine { error("HTTP must not be called while creating runtime") })
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.assets.open("aniliberty-source.wasm").use { input ->
                module.writeBytes(input.readBytes())
            }
            val manifest = manifest()
            packageDirectory.resolve("manifest.json").writeText(Json.encodeToString(manifest))
            val installedManifest = JvmSourcePackageManifestReader().read(packageDirectory.toString())
            createAndroidExternalSourceRuntimeFactory(context).create(
                sourcePackage = ActiveExternalSourcePackage(
                    manifest = installedManifest,
                    installed = InstalledSourcePackage(
                        sourceId = installedManifest.sourceId,
                        packageVersion = installedManifest.packageVersion,
                        packagePath = packageDirectory.toString(),
                    ),
                ),
                context = DefaultSourceContext(
                    httpClient = client,
                    preferredLanguages = listOf(SourceLanguage.RUSSIAN),
                ),
            )
            Unit

        } finally {
            client.close()
            module.deleteIfExists()
            packageDirectory.resolve("manifest.json").deleteIfExists()
            packageDirectory.toFile().delete()
        }
    }

    @Test
    fun installedWasmPackagePassesAndroidRuntimeValidation() = runBlocking {
        val packageDirectory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .toPath()
            .resolve("external-source-validation-${System.nanoTime()}")
        val module = packageDirectory.resolve("source.wasm")
        packageDirectory.toFile().mkdirs()
        try {
            module.writeBytes(moduleBytes())
            val manifest = manifest()
            packageDirectory.resolve("manifest.json").writeText(Json.encodeToString(manifest))
            val installedManifest = JvmSourcePackageManifestReader().read(packageDirectory.toString())
            val installed = InstalledSourcePackage(
                sourceId = installedManifest.sourceId,
                packageVersion = installedManifest.packageVersion,
                packagePath = packageDirectory.toString(),
            )

            validateAndroidExternalSourceRuntime(
                ActiveExternalSourcePackage(manifest = installedManifest, installed = installed),
            )
            assertTrue(module.toFile().exists())
        } finally {
            module.deleteIfExists()
            packageDirectory.resolve("manifest.json").deleteIfExists()
            packageDirectory.toFile().delete()
        }
    }

    private fun manifest() = SourceManifest(
        manifestFormatVersion = SourceManifest.CURRENT_FORMAT_VERSION,
        sourceId = SourceId("instrumented-source"),
        packageVersion = "1.0.0",
        sourceInfo = SourceManifestInfo(
            displayName = "Instrumented source",
            languages = setOf(SourceLanguage.RUSSIAN, SourceLanguage.ENGLISH),
            primaryLanguage = SourceLanguage.RUSSIAN,
        ),
        apiVersion = SourceApi.VERSION,
        runtime = SourceRuntime("wasm", "wasm32-wasi-preview1"),
        entrypoint = "source.wasm",
        packageUrl = "https://example.com/source.zip",
        sha256 = "a".repeat(64),
        artifactSizeBytes = 1,
        minClientVersion = 0,
        hostCapabilities = setOf(SourceHostCapability.NETWORK),
        capabilities = setOf(SourceCapability.PLAYBACK),
        hostNetworkPolicy = SourceHostNetworkPolicy(
            allowedHosts = setOf("anilibria.top", "api.anilibria.app"),
        ),
    )

    private fun releaseJson(): String = """
        {
          "id":413,
          "name":{"main":"Naruto","english":"Naruto","alternative":null},
          "year":2007,
          "type":{"value":"TV"},
          "episodes_total":1,
          "is_ongoing":false,
          "description":"Fixture release",
          "poster":{"src":"/storage/poster.jpg"},
          "genres":[{"name":"Action"}],
          "episodes":[{
            "id":"episode-1",
            "ordinal":1,
            "name":"Episode 1",
            "hls_720":"https://cache.libria.fun/videos/episode-1/720.m3u8",
            "duration":1400,
            "opening":{"start":1,"stop":100},
            "ending":{"start":null,"stop":null}
          }]
        }
    """.trimIndent()

    private fun moduleBytes(): ByteArray = """
        (module
          (memory (export "memory") 1)
          (func (export "beakokit_reset"))
          (func (export "beakokit_alloc") (param i32) (result i32)
            i32.const 4096)
          (func (export "beakokit_call") (param i32 i32) (result i64)
            i64.const 0))
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
}
