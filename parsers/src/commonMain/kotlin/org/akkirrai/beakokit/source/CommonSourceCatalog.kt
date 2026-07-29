package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.SourceFactory
import org.akkirrai.beakokit.source.aniliberty.AniLibertySource
import org.akkirrai.beakokit.source.yummy.YummyAnimeSource

/** Explicit registration for source implementations available from commonMain. */
internal object CommonSourceCatalog {
    val ANI_LIBERTY_ID = AniLibertySource.INFO.id
    val YUMMY_ANIME_ID = YummyAnimeSource.INFO.id

    val catalog = SourceCatalog(
        listOf(
            SourceCatalogEntry(
                info = YummyAnimeSource.INFO,
                factory = SourceFactory(::YummyAnimeSource),
            ),
            SourceCatalogEntry(
                info = AniLibertySource.INFO,
                factory = SourceFactory(::AniLibertySource),
            ),
        ),
    )
}
