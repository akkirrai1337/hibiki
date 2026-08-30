package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.ChallengeSession
import org.akkirrai.beakokit.api.ChallengeSessionProvider
import org.akkirrai.beakokit.api.ChallengeSessionRequest
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Focused test of the `challenge()`/fetch-headers sandbox mechanism AnimePahe relies on, since the
 * full [ScriptedAnimePaheSourceTest] fixtures never actually trigger a challenge (same as the
 * original compiled-in AnimePaheSourceTest never did).
 */
class RhinoExtensionRuntimeChallengeTest {
    @Test
    fun `script retries through a challenge session on a cf-mitigated response`() = runBlocking {
        var callCount = 0
        val client = HttpClient(
            MockEngine { request ->
                callCount++
                val cookie = request.headers["Cookie"]
                if (cookie == "cf_clearance=granted") {
                    respond("ok")
                } else {
                    respond("blocked", status = HttpStatusCode.OK, headers = headersOf("cf-mitigated", "challenge"))
                }
            },
        )
        val acquireCount = AtomicInteger(0)
        val provider = ChallengeSessionProvider { request: ChallengeSessionRequest ->
            acquireCount.incrementAndGet()
            ChallengeSession(cookies = mapOf("cf_clearance" to "granted"), userAgent = "TestAgent")
        }
        val context = DefaultSourceContext(
            httpClient = client,
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
            challengeSessionProvider = provider,
        )
        val runtime = RhinoExtensionRuntime(
            extensionId = "challenge-test",
            payload = """
                var Provider = {
                    search: function() {
                        var response = fetch("https://example.test/protected", {});
                        if (response.status === 200 && response.headers["cf-mitigated"] === "challenge") {
                            var session = challenge("https://example.test/protected", ["cf_clearance"], false);
                            response = fetch("https://example.test/protected", { headers: { "Cookie": session.cookieHeader } });
                        }
                        return response.body;
                    }
                };
            """.trimIndent(),
            sourceContext = context,
        )

        val result = runtime.call<String>("search")

        assertEquals("ok", result)
        assertEquals(1, acquireCount.get())
        assertEquals(2, callCount)
        client.close()
    }
}
