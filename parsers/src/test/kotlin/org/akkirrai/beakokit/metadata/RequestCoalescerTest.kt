package org.akkirrai.beakokit.metadata

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestCoalescerTest {
    @Test
    fun `same key shares one in-flight request`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val coalescer = RequestCoalescer<String, Int>()

        val results = List(2) {
            async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.load("frieren") {
                    calls++
                    gate.await()
                    42
                }
            }
        }
        gate.complete(Unit)

        assertEquals(listOf(42, 42), results.awaitAll())
        assertEquals(1, calls)
    }
}
