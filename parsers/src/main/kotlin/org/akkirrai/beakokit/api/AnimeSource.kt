package org.akkirrai.beakokit.api

import org.akkirrai.animeresolver.core.MetadataSource

/** A discoverable source whose identity and behavior travel together. */
interface AnimeSource : MetadataSource {
    val info: SourceInfo

    override val name: String
        get() = info.name
}
