package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.generated.GeneratedSourceCatalog

/** Stable facade over the KSP-generated built-in source catalog. */
object BuiltInSources {
    val YUMMY_ANIME_ID = GeneratedSourceCatalog.YUMMY_ANIME_ID
    val ANI_LIBERTY_ID = GeneratedSourceCatalog.ANI_LIBERTY_ID
    val ANIMEGO_ID = GeneratedSourceCatalog.ANIMEGO_ID

    val catalog: SourceCatalog = GeneratedSourceCatalog.catalog
}
