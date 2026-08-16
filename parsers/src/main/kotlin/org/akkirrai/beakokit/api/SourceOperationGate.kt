package org.akkirrai.beakokit.api

/** Checks optional source operations before a runtime dispatch. */
object SourceOperationGate {
    fun supports(source: AnimeSource, operation: SourceOperation): Boolean = when (operation) {
        SourceOperation.HEALTH_CHECK -> source is HealthCheckSource
        SourceOperation.LATEST ->
            source is LatestSource && SourceCapability.LATEST_RELEASES in source.info.capabilities
        SourceOperation.PLAYBACK_GROUPS,
        SourceOperation.PLAYER_LINKS,
        -> source is PlaybackSource && SourceCapability.PLAYBACK in source.info.capabilities
        else -> supports(source.info, operation)
    }

    fun supports(info: SourceInfo, operation: SourceOperation): Boolean = when (operation) {
        SourceOperation.SEARCH,
        SourceOperation.FILTER_CATALOG,
        SourceOperation.DETAILS,
        -> true

        SourceOperation.LATEST -> SourceCapability.LATEST_RELEASES in info.capabilities
        SourceOperation.PLAYBACK_GROUPS,
        SourceOperation.PLAYER_LINKS,
        -> SourceCapability.PLAYBACK in info.capabilities

        // These require a separate source contract and are not represented by a capability yet.
        SourceOperation.HEALTH_CHECK,
        SourceOperation.SCHEDULE,
        -> false
    }

    fun requireSupported(source: AnimeSource, operation: SourceOperation) {
        if (!supports(source, operation)) {
            throw SourceException(
                message = "Source does not support operation ${operation.name}: ${source.info.id}",
                kind = SourceErrorKind.UNKNOWN,
                code = SourceErrorCode.UNSUPPORTED_OPERATION,
            )
        }
    }
}
