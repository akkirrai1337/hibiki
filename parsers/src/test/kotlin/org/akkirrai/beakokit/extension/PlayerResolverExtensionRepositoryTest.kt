package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerResolverExtensionRepositoryTest {
    @Test
    fun `installed resolver resolves a matching embed outside the APK`() = runBlocking {
        val directory = Files.createTempDirectory("hibiki-resolvers").toFile()
        val repository = PlayerResolverExtensionRepository(directory)
        repository.install(
            Json.encodeToString(
                PlayerResolverExtensionManifest.serializer(),
                PlayerResolverExtensionManifest(
                    id = "example-player",
                    name = "Example player",
                    version = "1.0.0",
                    hosts = setOf("example.test"),
                    payload = """
                        var Provider = { resolve: function(linkJson) {
                            var link = JSON.parse(String(linkJson));
                            return [{ url: link.url + ".m3u8", type: "HLS", quality: "720p", headers: {} }];
                        }};
                    """.trimIndent(),
                ),
            ),
        )
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val resolver = repository.loadAll(
            DefaultSourceContext(client, listOf(SourceLanguage.ENGLISH)),
        ).single()

        val stream = resolver.extract(PlayerLink("https://embed.example.test/video", PlayerType.EMBED, null))

        assertEquals("https://embed.example.test/video.m3u8", stream.url)
        assertEquals(StreamType.HLS, stream.type)
        client.close()
    }
}
