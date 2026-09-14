package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.beakokit.api.BrowserRelayProvider
import org.akkirrai.beakokit.api.BrowserRelayRequest
import org.akkirrai.beakokit.api.BrowserRelayResult
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the `browserRelay` Rhino global forwards to [org.akkirrai.beakokit.api.BrowserRelayProvider]
 * and shapes its result the way a resolver script expects, mirroring how `browserFetch` is already
 * exercised end-to-end through [ScriptedMiruroSourceTest]. */
class RhinoExtensionRuntimeBrowserRelayTest {
    @Test
    fun `browserRelay forwards to the provider and returns proxied urls`() {
        var received: BrowserRelayRequest? = null
        val provider = BrowserRelayProvider { request ->
            received = request
            BrowserRelayResult(urls = request.urls.associateWith { "http://127.0.0.1:1/relay?u=$it" })
        }
        val context = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("no HTTP expected") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            browserRelayProvider = provider,
        )
        val runtime = RhinoExtensionRuntime(
            "test",
            """
                var Provider = {
                    resolve: function () {
                        var result = browserRelay(
                            "https://example.test/embed",
                            ["https://cdn.example/master.m3u8", "https://cdn.example/sub.vtt"],
                            { headers: { "Referer": "https://example.test/" } }
                        );
                        return result.urls;
                    }
                };
            """.trimIndent(),
            context,
        )

        val result = runtime.call<Map<String, String>>("resolve")

        assertEquals(
            "http://127.0.0.1:1/relay?u=https://cdn.example/master.m3u8",
            result["https://cdn.example/master.m3u8"],
        )
        assertEquals(
            "http://127.0.0.1:1/relay?u=https://cdn.example/sub.vtt",
            result["https://cdn.example/sub.vtt"],
        )
        assertEquals("https://example.test/embed", received?.pageUrl)
        assertEquals(listOf("https://cdn.example/master.m3u8", "https://cdn.example/sub.vtt"), received?.urls)
        assertEquals("https://example.test/", received?.headers?.get("Referer"))
    }
}
