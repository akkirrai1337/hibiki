package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog

actual object BuiltInSources {
    actual val YUMMY_ANIME_ID = CommonSourceCatalog.YUMMY_ANIME_ID
    actual val ANI_LIBERTY_ID = CommonSourceCatalog.ANI_LIBERTY_ID
    actual val catalog: SourceCatalog = CommonSourceCatalog.catalog
}
