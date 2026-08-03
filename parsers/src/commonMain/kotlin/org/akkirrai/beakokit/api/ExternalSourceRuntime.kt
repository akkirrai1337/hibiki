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

/** Optional runtime contract for sources that expose episodes and playable links. */
interface ExternalSourcePlaybackRuntime : ExternalSourceRuntime {
    suspend fun playbackGroups(title: AnimeTitle): List<PlaybackGroup>

    suspend fun playerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink>
}

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
) : AnimeSource {
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

/** BeakoKit adapter for an external runtime with the optional playback contract. */
class RuntimeBackedPlaybackAnimeSource(
    info: SourceInfo,
    catalogCapabilities: org.akkirrai.beakokit.model.CatalogCapabilities,
    runtime: ExternalSourcePlaybackRuntime,
) : RuntimeBackedAnimeSource(
    info = info,
    catalogCapabilities = catalogCapabilities,
    runtime = runtime,
), PlaybackSource {
    private val playbackRuntime = runtime

    override suspend fun getPlaybackGroups(title: AnimeTitle): List<PlaybackGroup> =
        playbackRuntime.playbackGroups(title)

    override suspend fun getPlayerLinks(
        title: AnimeTitle,
        group: PlaybackGroup,
        episode: Episode,
    ): List<PlayerLink> = playbackRuntime.playerLinks(title, group, episode)
}
