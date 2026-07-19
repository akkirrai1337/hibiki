package org.akkirrai.beakokit.playback.extractor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals

class SibnetExtractorTest {
    @Test
    fun `extracts direct mp4 source from sibnet embed`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <link rel="canonical" href="https://video.sibnet.ru/video5560903-_Kondrateandr_RU_SUB__Noch_mechtyi/"/>
                    </head>
                    <body>
                    <script type="text/javascript">
                    player.src([
                        {src: "/v/50adb8b798cbf55d7c999be4e4326c9b/5560903.mp4", type: "video/mp4"},
                    ]);
                    </script>
                    </body>
                    </html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=windows-1251"),
            )
        }
        val client = HttpClient(engine)

        val stream = SibnetExtractor(client).extract(
            PlayerLink(
                url = "https://video.sibnet.ru/shell.php?videoid=5560903",
                type = PlayerType.EMBED,
                quality = null,
            ),
        )

        assertEquals("https://video.sibnet.ru/v/50adb8b798cbf55d7c999be4e4326c9b/5560903.mp4", stream.url)
        assertEquals(StreamType.MP4, stream.type)
        assertEquals("https://video.sibnet.ru/video5560903-_Kondrateandr_RU_SUB__Noch_mechtyi/", stream.headers[HttpHeaders.Referrer])
        assertEquals("https://video.sibnet.ru", stream.headers[HttpHeaders.Origin])
        assertEquals(true, stream.headers[HttpHeaders.UserAgent]?.contains("Mozilla/5.0") == true)
        client.close()
    }
}
