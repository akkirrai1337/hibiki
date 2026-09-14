package org.akkirrai.beakokit.metadata

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Which lane of a provider's [MetadataRequestQueue] a lookup waits in, carried in the coroutine
 * context so it reaches the queue without threading a parameter through every client call.
 *
 * Absent means foreground. Visible cards come next, and speculative related-title work is last, so
 * prefetch never keeps a just-visible card from getting its provider slot.
 */
enum class MetadataWorkClass {
    FOREGROUND,
    VISIBLE,
    PREFETCH,
}

class MetadataPriority private constructor(
    val workClass: MetadataWorkClass,
) : AbstractCoroutineContextElement(MetadataPriority) {
    /** Search-order balancing still needs to distinguish user-blocking from background work. */
    val background: Boolean = workClass != MetadataWorkClass.FOREGROUND

    companion object Key : CoroutineContext.Key<MetadataPriority> {
        val Foreground = MetadataPriority(MetadataWorkClass.FOREGROUND)
        val Visible = MetadataPriority(MetadataWorkClass.VISIBLE)
        val Prefetch = MetadataPriority(MetadataWorkClass.PREFETCH)

        /** Existing callers that have no visibility signal are conservative prefetch work. */
        val Background = Prefetch
    }
}
