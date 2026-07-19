package org.akkirrai.beakokit.api

import org.akkirrai.animeresolver.model.MetadataSourceFeature

class SourceContractException(
    val violations: List<String>,
) : IllegalStateException(violations.joinToString(prefix = "Invalid source contract: ", separator = "; "))

/** Runtime-safe validation shared by Hibiki and the BeakoKit TestKit. */
object SourceContractValidator {
    fun violations(source: AnimeSource): List<String> = buildList {
        if (source.name != source.info.name) {
            add("source.name must match SourceInfo.name")
        }

        val declaresPlayback = SourceCapability.PLAYBACK in source.info.capabilities
        if (declaresPlayback != (source is PlaybackSource)) {
            add("PLAYBACK must match implementation of PlaybackSource")
        }

        val declaresLatest = SourceCapability.LATEST_RELEASES in source.info.capabilities
        if (declaresLatest != (source is LatestSource)) {
            add("LATEST_RELEASES must match implementation of LatestSource")
        }

        val metadataDeclaresLatest = MetadataSourceFeature.LATEST_RELEASES in source.capabilities.features
        if (declaresLatest != metadataDeclaresLatest) {
            add("LATEST_RELEASES must match metadata capabilities during legacy migration")
        }
    }

    fun requireValid(source: AnimeSource) {
        val violations = violations(source)
        if (violations.isNotEmpty()) throw SourceContractException(violations)
    }
}
