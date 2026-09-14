package org.akkirrai.beakokit.metadata

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Which lane of a provider's [MetadataRequestQueue] a lookup waits in, carried in the coroutine
 * context so it reaches the queue without threading a parameter through every client call.
 *
 * Absent means foreground. A background request - a list card, a related-titles strip - is only let
 * through while no foreground one is waiting, so opening a title never queues behind the page of
 * cards it was opened from.
 */
class MetadataPriority private constructor(val background: Boolean) : AbstractCoroutineContextElement(MetadataPriority) {
    companion object Key : CoroutineContext.Key<MetadataPriority> {
        val Foreground = MetadataPriority(background = false)
        val Background = MetadataPriority(background = true)
    }
}
