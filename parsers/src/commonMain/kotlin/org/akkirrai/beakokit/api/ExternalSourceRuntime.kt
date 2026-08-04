package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.AnimeSearchRequest
import org.akkirrai.beakokit.model.AnimeTitle
import org.akkirrai.beakokit.model.Episode
import org.akkirrai.beakokit.model.PlayerLink

/** Minimal runtime surface for the first external-source milestone. */
interface ExternalSourceRuntime {
    suspend fun search(request: AnimeSearchRequest): List<AnimeTitle>

    suspend fun details(id: String): AnimeTitle
}

/** Optional runtime contract for sources that expose their latest updated titles. */
interface ExternalSourceLatestRuntime : ExternalSourceRuntime {
    suspend fun latest(limit: Int): List<AnimeTitle>
}

/** Optional runtime contract for sources that expose episodes and playable links. */
interface ExternalSourcePlaybackRuntime : ExternalSourceRuntime {
    suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup>

    suspend fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink>
}

/** Combined optional runtime contract for latest titles and playback. */
interface ExternalSourceLatestPlaybackRuntime : ExternalSourcePlaybackRuntime, ExternalSourceLatestRuntime

/** Platform adapter that creates a runtime for one already active source package. */
fun interface ExternalSourceRuntimeFactory {
    fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntime
}

/** Adapts a runtime-backed external source to the regular BeakoKit source contract. */
open class RuntimeBackedAnimeSource(
    override val info: SourceInfo,
    override val catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    protected val runtime: ExternalSourceRuntime,
) : ConfigurableSource {
    override val configSchema: SourceConfigSchema
        get() = info.configSchema

    override suspend fun search(query: String): List<AnimeTitle> = search(
        AnimeSearchRequest(query = query),
    )

    override suspend fun search(request: AnimeSearchRequest): List<AnimeTitle> {
        SourceOperationGate.requireSupported(this, SourceOperation.SEARCH)
        return runtime.search(request)
    }

    override suspend fun getById(id: String): AnimeTitle {
        SourceOperationGate.requireSupported(this, SourceOperation.DETAILS)
        return runtime.details(id)
    }
}

/** Adapts the optional latest operation from an external runtime. */
class RuntimeBackedLatestAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourceLatestRuntime,
) : RuntimeBackedAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), LatestSource {
    private val latestRuntime = runtime

    override suspend fun latest(limit: Int): List<AnimeTitle> {
        require(limit > 0) { "Latest source limit must be positive" }
        SourceOperationGate.requireSupported(this, SourceOperation.LATEST)
        return latestRuntime.latest(limit)
    }
}

/** BeakoKit adapter for an external runtime with the optional playback contract. */
open class RuntimeBackedPlaybackAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourcePlaybackRuntime,
) : RuntimeBackedAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), PlaybackSource {
    private val playbackRuntime = runtime

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> {
        SourceOperationGate.requireSupported(this, SourceOperation.PLAYBACK_GROUPS)
        return playbackRuntime.playbackGroups(title)
    }

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> {
        SourceOperationGate.requireSupported(this, SourceOperation.PLAYER_LINKS)
        return playbackRuntime.playerLinks(title, group, episode)
    }
}

/** Adapts an external runtime that exposes both latest titles and playback. */
class RuntimeBackedLatestPlaybackAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourceLatestPlaybackRuntime,
) : RuntimeBackedPlaybackAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), LatestSource {
    private val latestRuntime = runtime

    override suspend fun latest(limit: Int): List<AnimeTitle> {
        require(limit > 0) { "Latest source limit must be positive" }
        SourceOperationGate.requireSupported(this, SourceOperation.LATEST)
        return latestRuntime.latest(limit)
    }
}
