package org.akkirrai.beakokit.extension

import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

/** Verifies a scripted extension cannot reach the JVM/Android runtime beyond the curated globals. */
class RhinoExtensionRuntimeSandboxTest {
    private fun context() = DefaultSourceContext(
        httpClient = HttpClient(MockEngine { error("This test performs no network I/O") }),
        preferredLanguages = listOf(SourceLanguage.ENGLISH),
    )

    @Test
    fun `Packages global is unreachable`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var Provider = {
                    search: function() { return Packages.java.lang.Runtime.getRuntime().exec("echo pwned"); }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertFailsWith<Exception> { runtime.callRaw("search", arrayOf()) }
    }

    @Test
    fun `java global is unreachable`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var Provider = {
                    search: function() { return new java.io.File("/").listFiles(); }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertFailsWith<Exception> { runtime.callRaw("search", arrayOf()) }
    }

    @Test
    fun `curated Jsoup binding still works`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var Provider = {
                    search: function() {
                        var doc = Jsoup.parse("<p class='x'>hello</p>");
                        return doc.selectFirst(".x").text();
                    }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertEquals("\"hello\"", runtime.callRaw("search", arrayOf()))
    }

    @Test
    fun `collectPaginated stops on a short page and dedupes by id`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var pages = {
                    1: [{ id: "a" }, { id: "b" }],
                    2: [{ id: "b" }, { id: "c" }],
                };
                function fetchPage(page) { return pages[page] || []; }
                var Provider = {
                    search: function() { return collectPaginated(fetchPage, 10, 2); }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertEquals("""[{"id":"a"},{"id":"b"},{"id":"c"}]""", runtime.callRaw("search", arrayOf()))
    }

    @Test
    fun `collectPaginated stops when a page throws`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                function fetchPage(page) {
                    if (page === 1) return [{ id: "a" }, { id: "b" }];
                    throw new Error("HTTP 404");
                }
                var Provider = {
                    search: function() { return collectPaginated(fetchPage, 10, 2); }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertEquals("""[{"id":"a"},{"id":"b"}]""", runtime.callRaw("search", arrayOf()))
    }

    @Test
    fun `collectPaginated stops on an empty page and respects the wanted cap`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                function fetchPage(page) { return [{ id: "item" + page }, { id: "item" + page + "b" }]; }
                var Provider = {
                    search: function() { return collectPaginated(fetchPage, 3, 2); },
                    latest: function() { return collectPaginated(function () { return []; }, 10, 2); },
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertEquals("""[{"id":"item1"},{"id":"item1b"},{"id":"item2"},{"id":"item2b"}]""", runtime.callRaw("search", arrayOf()))
        assertEquals("[]", runtime.callRaw("latest", arrayOf()))
    }
}
