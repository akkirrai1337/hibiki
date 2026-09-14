package org.akkirrai.beakokit.metadata

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestBatcherTest {
    @Test
    fun `lookups that pile up while a request waits for its slot share one request`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val sent = Collections.synchronizedList(mutableListOf<List<Int>>())
        val batcher = RequestBatcher<Int, Int>(maxBatch = 10, MetadataPriority.Foreground) { takeKeys ->
            gate.await()
            takeKeys().also(sent::add).associateWith { it * 10 }
        }

        val results = (1..5).map { key -> async(start = CoroutineStart.UNDISPATCHED) { batcher.load(key) } }
        gate.complete(Unit)

        assertEquals((1..5).map { BatchOutcome.Done(it * 10) }, results.awaitAll())
        assertEquals(listOf((1..5).toList()), sent.toList())
    }

    @Test
    fun `a batch never exceeds its maximum size`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val sent = Collections.synchronizedList(mutableListOf<Int>())
        val batcher = RequestBatcher<Int, Int>(maxBatch = 2, MetadataPriority.Foreground) { takeKeys ->
            gate.await()
            takeKeys().also { sent += it.size }.associateWith { it }
        }

        val results = (1..5).map { key -> async(start = CoroutineStart.UNDISPATCHED) { batcher.load(key) } }
        gate.complete(Unit)
        results.awaitAll()

        assertEquals(listOf(2, 2, 1), sent.toList())
    }

    @Test
    fun `a request that is never sent fails every waiting key`() = runBlocking {
        val batcher = RequestBatcher<Int, Int>(maxBatch = 10, MetadataPriority.Foreground) { null }

        assertEquals(BatchOutcome.Failed, batcher.load(1))
    }

    @Test
    fun `a key the answer lacks is done with nothing, not failed`() = runBlocking {
        val batcher = RequestBatcher<Int, Int>(maxBatch = 10, MetadataPriority.Foreground) { takeKeys ->
            takeKeys()
            emptyMap()
        }

        assertEquals(BatchOutcome.Done(null), batcher.load(1))
    }
}
