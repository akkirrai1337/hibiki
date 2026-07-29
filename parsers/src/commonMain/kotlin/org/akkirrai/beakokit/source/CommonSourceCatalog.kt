package org.akkirrai.beakokit.source

import org.akkirrai.beakokit.api.SourceCatalog
import org.akkirrai.beakokit.api.SourceCatalogEntry
import org.akkirrai.beakokit.api.SourceFactory
import org.akkirrai.beakokit.source.aniliberty.AniLibertySource

/** Explicit registration for source implementations available from commonMain. */
internal object CommonSourceCatalog {
    val ANI_LIBERTY_ID = AniLibertySource.INFO.id

    val catalog = SourceCatalog(
        listOf(
            SourceCatalogEntry(
                info = AniLibertySource.INFO,
                factory = SourceFactory(::AniLibertySource),
            ),
        ),
    )
}
