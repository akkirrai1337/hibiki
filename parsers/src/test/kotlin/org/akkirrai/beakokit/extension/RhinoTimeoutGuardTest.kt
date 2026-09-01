package org.akkirrai.beakokit.extension

import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies a runaway (infinite-loop) script call gets killed instead of hanging its runtime forever. */
class RhinoTimeoutGuardTest {
    private val originalInstructionThreshold = instructionThreshold
    private val originalScriptTimeoutMillis = scriptTimeoutMillis

    @BeforeTest
    fun lowerTimeoutForTest() {
        // Only these two are dialed down - real extensions always run with the production
        // defaults; this just keeps the test itself from taking 15 real seconds.
        instructionThreshold = 1_000
        scriptTimeoutMillis = 50L
    }

    @AfterTest
    fun restoreProductionTimeout() {
        instructionThreshold = originalInstructionThreshold
        scriptTimeoutMillis = originalScriptTimeoutMillis
    }

    private fun context() = DefaultSourceContext(
        httpClient = HttpClient(MockEngine { error("no network") }),
        preferredLanguages = listOf(SourceLanguage.ENGLISH),
    )

    @Test
    fun `an infinite loop is killed instead of hanging forever`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var Provider = {
                    search: function() {
                        while (true) { }
                        return "unreachable";
                    }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        val error = assertFailsWith<SourceException> { runtime.callRaw("search", arrayOf()) }
        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
    }

    @Test
    fun `a script's own try-catch cannot swallow the timeout and keep looping`() {
        val runtime = RhinoExtensionRuntime(
            extensionId = "sandbox-test",
            payload = """
                var Provider = {
                    search: function() {
                        while (true) {
                            try { } catch (e) { }
                        }
                        return "unreachable";
                    }
                };
            """.trimIndent(),
            sourceContext = context(),
        )
        assertFailsWith<SourceException> { runtime.callRaw("search", arrayOf()) }
    }
}
