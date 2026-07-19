package org.akkirrai.beakokit.http

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MirrorRequestExecutorTest {
    @Test
    fun `uses mirrors in order until one succeeds`() = runBlocking {
        val attempted = mutableListOf<String>()
        val executor = MirrorRequestExecutor(
            sourceName = "TestSource",
            baseUrls = listOf("https://first.test/", "https://second.test"),
        )

        val result = executor.execute { baseUrl ->
            attempted += baseUrl
            if (baseUrl.contains("first")) error("Primary is unavailable")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf("https://first.test", "https://second.test"), attempted)
    }

    @Test
    fun `does not retry coroutine cancellation`() = runBlocking {
        var attempts = 0
        val executor = MirrorRequestExecutor(
            sourceName = "TestSource",
            baseUrls = listOf("https://first.test", "https://second.test"),
        )

        assertFailsWith<CancellationException> {
            executor.execute<Unit> {
                attempts += 1
                throw CancellationException("Cancelled by caller")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `reports typed unavailable error after every mirror fails`() = runBlocking {
        val executor = MirrorRequestExecutor(
            sourceName = "TestSource",
            baseUrls = listOf("https://first.test", "https://second.test"),
        )

        val error = assertFailsWith<SourceUnavailableException> {
            executor.execute<Unit> { baseUrl -> error("Failed: $baseUrl") }
        }

        assertEquals(SourceErrorKind.UNAVAILABLE, error.kind)
        assertEquals(1, error.suppressed.size)
    }
}
