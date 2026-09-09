package org.akkirrai.beakokit.extension

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.api.context.DefaultSourceContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/** Standing up a runtime is expensive, so the pool must only do it when a call actually needs one. */
class RhinoRuntimePoolTest {
    private fun runtime(): RhinoExtensionRuntime = RhinoExtensionRuntime(
        extensionId = "pool-test",
        payload = "var Provider = { ping: function () { return JSON.stringify(\"pong\"); } };",
        sourceContext = DefaultSourceContext(
            httpClient = HttpClient(MockEngine { error("no network") }),
            preferredLanguages = listOf(SourceLanguage.ENGLISH),
        ),
    )

    @Test
    fun `sequential calls reuse one runtime`() = runBlocking {
        val built = AtomicInteger(0)
        val pool = RhinoRuntimePool(3) { built.incrementAndGet(); runtime() }

        repeat(5) { pool.use { it.extensionId } }

        assertEquals(1, built.get())
    }

    @Test
    fun `a second concurrent call gets its own runtime`() = runBlocking {
        val built = AtomicInteger(0)
        val pool = RhinoRuntimePool(3) { built.incrementAndGet(); runtime() }
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        coroutineScope {
            val first = async {
                pool.use {
                    firstEntered.complete(Unit)
                    runBlocking { releaseFirst.await() }
                }
            }
            firstEntered.await()
            pool.use { it.extensionId }
            releaseFirst.complete(Unit)
            first.await()
        }

        assertEquals(2, built.get())
    }
}
