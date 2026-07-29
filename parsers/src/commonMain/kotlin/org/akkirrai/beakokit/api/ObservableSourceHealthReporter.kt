package org.akkirrai.beakokit.api

import kotlinx.coroutines.flow.StateFlow

/** Optional observable view for hosts that render source state reactively. */
interface ObservableSourceHealthReporter : SourceHealthReporter {
    val states: StateFlow<Map<SourceId, SourceHealth>>
}
