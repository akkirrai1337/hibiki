package org.akkirrai.beakokit.api

/**
 * Convention a standalone source-extension APK must follow so the host app can discover and
 * load it via `PackageManager`, without the host downloading or unpacking anything itself.
 *
 * An extension declares one `<activity>` (never launched, existing only as a discovery anchor)
 * in its manifest with an intent-filter matching [ACTION], plus a `<meta-data>` entry under
 * [META_SOURCE_CLASS] naming the class that implements [AnimeSource]. That class must expose a
 * public constructor taking a single [SourceContext] parameter.
 */
object SourceExtensionContract {
    /** Intent action every source-extension APK advertises via an intent-filter. */
    const val ACTION = "org.akkirrai.hibiki.action.SOURCE"

    /** Manifest meta-data key naming the fully-qualified `AnimeSource` implementation class. */
    const val META_SOURCE_CLASS = "hibiki.source.class"

    /** Minimum extension manifest contract version the host currently understands. */
    const val CONTRACT_VERSION = 1

    /** Manifest meta-data key declaring the extension's contract version as an integer. */
    const val META_CONTRACT_VERSION = "hibiki.source.contractVersion"
}
