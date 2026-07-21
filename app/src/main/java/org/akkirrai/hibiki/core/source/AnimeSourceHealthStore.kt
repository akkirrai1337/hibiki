package org.akkirrai.hibiki.core.source

import kotlinx.coroutines.flow.StateFlow
import org.akkirrai.beakokit.api.InMemorySourceHealthReporter
import org.akkirrai.beakokit.api.SourceHealth
import org.akkirrai.beakokit.api.SourceHealthReporter
import org.akkirrai.beakokit.api.SourceId

/** Application-owned, shared state for health reported by every built-in source runtime. */
class AnimeSourceHealthStore(
    private val delegate: InMemorySourceHealthReporter = InMemorySourceHealthReporter(),
) {
    val states: StateFlow<Map<SourceId, SourceHealth>>
        get() = delegate.states

    val reporter: SourceHealthReporter
        get() = delegate

    fun health(sourceId: SourceId): SourceHealth = delegate.health(sourceId)
}

/** One store is shared across repositories that create independent source runtime managers. */
object HibikiSourceHealth {
    val store = AnimeSourceHealthStore()
}
