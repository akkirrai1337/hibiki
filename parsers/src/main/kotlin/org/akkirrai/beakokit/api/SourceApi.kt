package org.akkirrai.beakokit.api

/** Public contract version used by source packages and their host runtime. */
object SourceApi {
    /** Current source contract version. Changes when the external ABI is incompatible. */
    const val VERSION: Int = 1

    /** Oldest source contract version supported by this client. */
    const val MIN_SUPPORTED_VERSION: Int = VERSION

    fun supports(version: Int): Boolean = version in MIN_SUPPORTED_VERSION..VERSION
}
