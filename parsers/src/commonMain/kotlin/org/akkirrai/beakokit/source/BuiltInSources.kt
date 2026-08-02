package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceId

/** Platform catalog facade; JVM may add sources unavailable to common targets. */
expect object BuiltInSources {
    val YUMMY_ANIME_ID: SourceId
    val ANI_LIBERTY_ID: SourceId
    val catalog: SourceCatalog
}
