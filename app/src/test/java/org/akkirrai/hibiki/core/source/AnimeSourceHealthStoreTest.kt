package org.akkirrai.hibiki.core.source

import kotlinx.coroutines.runBlocking
import org.akkirrai.beakokit.api.SourceAvailability
import org.akkirrai.beakokit.api.SourceId
import org.akkirrai.beakokit.api.track
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeSourceHealthStoreTest {
    @Test
    fun `store exposes reporter updates to app consumers`() = runBlocking {
        val store = AnimeSourceHealthStore()
        val sourceId = SourceId("fixture")

        store.reporter.track(sourceId) { "ok" }

        assertEquals(SourceAvailability.AVAILABLE, store.health(sourceId).availability)
        assertEquals(SourceAvailability.AVAILABLE, store.states.value[sourceId]?.availability)
    }
}
