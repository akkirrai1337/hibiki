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
}
