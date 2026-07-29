package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.generated.GeneratedSourceCatalog

/** Stable facade over the KSP-generated built-in source catalog. */
actual object BuiltInSources {
    actual val YUMMY_ANIME_ID = CommonSourceCatalog.YUMMY_ANIME_ID
    actual val ANI_LIBERTY_ID = CommonSourceCatalog.ANI_LIBERTY_ID
    val ANIMEGO_ID = GeneratedSourceCatalog.ANIMEGO_ID
    val ANIMEPAHE_ID = GeneratedSourceCatalog.ANIMEPAHE_ID
    val ANIMEVOST_ID = GeneratedSourceCatalog.ANIMEVOST_ID
    val GOGOANIME_ID = GeneratedSourceCatalog.GOGOANIME_ID

    actual val catalog: SourceCatalog = CommonSourceCatalog.catalog.mergedWith(GeneratedSourceCatalog.catalog)
}
