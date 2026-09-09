package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `fetchAll` is the way out of scripts being forced to make independent requests one at a time (see
 * [RhinoExtensionRuntime]'s FetchAllFunction). These check the two things a script depends on: that
 * the requests really do overlap, and that one failure doesn't discard the answers that arrived.
 */
class RhinoExtensionRuntimeFetchAllTest {
    private fun runtimeWith(engine: MockEngine, payload: String) = RhinoExtensionRuntime(
        extensionId = "fetch-all-test",
        payload = payload,
        sourceContext = DefaultSourceContext(
            httpClient = HttpClient(engine),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        ),
    )

    @Test
    fun `answers come back in the order they were asked for`() {
        // Deliberately answered slowest-first: if the results were ordered by completion rather
        // than by request, this is what would catch it.
        val engine = MockEngine { request ->
            val id = request.url.parameters["id"].orEmpty()
            if (id == "1") delay(200)
            respond(content = "body-$id", status = HttpStatusCode.OK, headers = headersOf("X-Id", id))
        }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                var rs = fetchAll(["https://x.test/?id=1", "https://x.test/?id=2", "https://x.test/?id=3"]);
                return rs[0].body + "," + rs[1].body + "," + rs[2].body;
            } };
            """.trimIndent(),
        )
        assertEquals("\"body-1,body-2,body-3\"", runtime.callRaw("search", arrayOf()))
    }

    @Test
    fun `requests overlap instead of running one after another`() {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val engine = MockEngine {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, now) }
            delay(50)
            inFlight.decrementAndGet()
            respond(content = "ok", status = HttpStatusCode.OK)
        }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                var urls = [];
                for (var i = 0; i < 4; i++) urls.push("https://x.test/?id=" + i);
                return String(fetchAll(urls).length);
            } };
            """.trimIndent(),
        )
        assertEquals("\"4\"", runtime.callRaw("search", arrayOf()))
        // The whole point of the function. One at a time would leave this at 1.
        assertTrue(peak.get() > 1, "expected overlapping requests, peak concurrency was ${peak.get()}")
    }

    @Test
    fun `a failed request occupies its slot instead of sinking the batch`() {
        val engine = MockEngine { request ->
            if (request.url.parameters["id"] == "2") throw java.io.IOException("connection reset")
            respond(content = "fine", status = HttpStatusCode.OK)
        }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                var rs = fetchAll(["https://x.test/?id=1", "https://x.test/?id=2", "https://x.test/?id=3"]);
                return rs[0].ok + "|" + rs[1].ok + "|" + rs[2].ok
                    + "|" + String(rs[1].status) + ":" + String(rs[1].error)
                    + "|" + rs[0].body + rs[2].body;
            } };
            """.trimIndent(),
        )
        val raw = runtime.callRaw("search", arrayOf())
        val parts = raw.trim('"').split("|")
        assertEquals("true", parts[0]); assertEquals("false", parts[1]); assertEquals("true", parts[2])
        // status 0 is "never got an answer", the same thing XHR reports below HTTP.
        assertTrue(parts[3].startsWith("0:"), "expected status 0 for a transport failure, got ${parts[3]}")
        assertContains(parts[3], "reset")
        // ...and the two that did answer are still usable.
        assertEquals("finefine", parts[4])
    }

    @Test
    fun `an HTTP error is a response, not a failure`() {
        val engine = MockEngine { respond(content = "nope", status = HttpStatusCode.NotFound) }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                var r = fetchAll(["https://x.test/"])[0];
                return String(r.status) + "|" + r.ok + "|" + ("error" in r);
            } };
            """.trimIndent(),
        )
        // 404 is the server's answer, so no `error` key - that one means the request never landed.
        assertEquals("\"404|false|false\"", runtime.callRaw("search", arrayOf()))
    }

    @Test
    fun `entries may carry their own method and headers`() {
        var seenMethod: String? = null
        var seenHeader: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method.value
            seenHeader = request.headers["X-Probe"]
            respond(content = "ok", status = HttpStatusCode.OK)
        }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                return String(fetchAll([{ url: "https://x.test/", method: "POST", headers: { "X-Probe": "yes" }, body: "hi" }])[0].ok);
            } };
            """.trimIndent(),
        )
        assertEquals("\"true\"", runtime.callRaw("search", arrayOf()))
        assertEquals("POST", seenMethod)
        assertEquals("yes", seenHeader)
    }

    @Test
    fun `a transport failure still escapes a plain fetch entirely`() {
        // Unchanged, and worth pinning: a network failure from fetch() does not merely throw into
        // the script - it escapes the script's own try/catch and ends the whole call (the runtime
        // converts it at the callRaw boundary, see the comment there about Method.invoke losing the
        // original exception). So a script cannot make one optional request best-effort with a
        // try/catch, however it is written.
        //
        // That is precisely the difference fetchAll buys: the same failure inside a batch is a
        // value in one slot, and the requests that succeeded are still returned.
        val engine = MockEngine { throw java.io.IOException("connection reset") }
        val runtime = runtimeWith(
            engine,
            """
            var Provider = { search: function () {
                try { fetch("https://x.test/"); return "no-throw"; }
                catch (e) { return "caught"; }
            } };
            """.trimIndent(),
        )
        assertFailsWith<SourceException> { runtime.callRaw("search", arrayOf()) }
    }
}
