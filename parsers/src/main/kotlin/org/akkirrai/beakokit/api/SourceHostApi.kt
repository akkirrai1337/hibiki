package org.akkirrai.beakokit.api

/** Version of the runtime host services exposed to external source packages. */
object SourceHostApi {
    const val VERSION: Int = 1
    const val MIN_SUPPORTED_VERSION: Int = VERSION

    fun supports(version: Int): Boolean = version in MIN_SUPPORTED_VERSION..VERSION
}

