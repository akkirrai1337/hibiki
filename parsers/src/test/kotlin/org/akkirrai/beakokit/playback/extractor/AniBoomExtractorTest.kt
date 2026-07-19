package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import kotlin.test.Test
import kotlin.test.assertEquals

class AniBoomExtractorTest {
    @Test
    fun `extracts escaped hls url and quality from embed`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    <div data-config="{&quot;hls&quot;:&quot;{\&quot;src\&quot;:
                    \&quot;https:\\\/\\\/video.example\\\/anime\\\/master.m3u8\&quot;}&quot;,
                    &quot;qualityVideo&quot;:1080}"></div>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val client = HttpClient(engine)

        val stream = AniBoomExtractor(client).extract(
            PlayerLink(
                url = "https://aniboom.one/embed/example",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals("https://video.example/anime/master.m3u8", stream.url)
        assertEquals("1080p", stream.quality)
        client.close()
    }
}
